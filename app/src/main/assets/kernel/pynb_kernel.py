"""
pynb kernel bridge - runs inside Termux with the interpreter of the chosen .venv.

Started by the pynb app through Termux's RUN_COMMAND intent:
    <venv>/bin/python -u -c "<this file>" --port <port> --token <token>
or on a remote server over SSH (reached through an SSH port forward):
    <venv>/bin/python -u ~/.pynb/pynb_kernel.py --port 0 --token-file F --ready-file R
--token-file reads the token from a file and deletes it (keeps it out of `ps`);
--port 0 picks a free port, which is written to --ready-file once the server listens.

Standard library only. Listens on 127.0.0.1 and requires the token on every request.
Executes cells in one persistent namespace (like a Jupyter kernel) and answers with
nbformat v4 output dicts, so the app can store them in the .ipynb file unchanged.

Endpoints (JSON):
    GET  /info        interpreter details
    POST /execute     {"code": "..."} -> {"execution_count": n, "status": "ok"|"error", "outputs": [...]}
    POST /interrupt   raise KeyboardInterrupt in the running cell
    POST /restart     fresh namespace and execution counter
    POST /shutdown    stop the kernel process
    POST /complete    {"code": "...", "cursor": n} -> {"matches": [{"text", "type"}], "prefix_length": n,
                      "signature": null | {"label", "params": [[start, end], ...], "active": i, "doc"}}
                      (standard library only: live variables, attributes, keywords, builtins,
                      importable modules; answers while a cell is running)
"""
import ast
import base64
import builtins
import io
import json
import linecache
import os
import queue
import re
import shlex
import signal
import subprocess
import sys
import threading
import time
import traceback
import warnings
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

KERNEL_VERSION = 2
IDLE_TIMEOUT_SECONDS = 4 * 60 * 60

os.environ.setdefault("MPLBACKEND", "Agg")
warnings.filterwarnings("ignore", message=".*non-interactive.*")


# --------------------------------------------------------------------------- output capture

class Outputs:
    """Collects nbformat outputs for one cell, in the order they were produced."""

    def __init__(self):
        self.items = []
        self.lock = threading.Lock()

    def stream(self, name, text):
        if not text:
            return
        with self.lock:
            last = self.items[-1] if self.items else None
            if last and last["output_type"] == "stream" and last["name"] == name:
                last["text"] += text
            else:
                self.items.append({"output_type": "stream", "name": name, "text": text})

    def display(self, data, metadata=None, execution_count=None):
        out = {"data": data, "metadata": metadata or {}}
        if execution_count is None:
            out["output_type"] = "display_data"
        else:
            out["output_type"] = "execute_result"
            out["execution_count"] = execution_count
        with self.lock:
            self.items.append(out)

    def error(self, ename, evalue, tb_lines):
        with self.lock:
            self.items.append({"output_type": "error", "ename": ename,
                               "evalue": evalue, "traceback": tb_lines})


class StreamWriter(io.TextIOBase):
    def __init__(self, kernel, name):
        self.kernel = kernel
        self.name = name

    def writable(self):
        return True

    def write(self, text):
        if not isinstance(text, str):
            text = str(text)
        outputs = self.kernel.current
        if outputs is not None:
            outputs.stream(self.name, text)
        else:
            (sys.__stdout__ if self.name == "stdout" else sys.__stderr__).write(text)
        return len(text)

    def flush(self):
        pass

    def isatty(self):
        return False


# --------------------------------------------------------------------------- rich display

def _b64(value):
    if isinstance(value, bytes):
        return base64.b64encode(value).decode("ascii")
    return value


def mime_bundle(obj):
    """Best-effort equivalent of IPython's display formatter."""
    data, metadata = {}, {}
    method = getattr(obj, "_repr_mimebundle_", None)
    if callable(method):
        try:
            result = method()
            if isinstance(result, tuple):
                result, metadata = result[0] or {}, result[1] or {}
            data.update(result or {})
        except Exception:
            pass
    for mime, attr in (("text/html", "_repr_html_"), ("text/markdown", "_repr_markdown_"),
                       ("text/latex", "_repr_latex_"), ("image/svg+xml", "_repr_svg_"),
                       ("image/png", "_repr_png_"), ("image/jpeg", "_repr_jpeg_"),
                       ("application/json", "_repr_json_")):
        if mime in data:
            continue
        method = getattr(obj, attr, None)
        if not callable(method):
            continue
        try:
            value = method()
        except Exception:
            continue
        if isinstance(value, tuple):
            value = value[0]
        if value is not None:
            data[mime] = _b64(value)
    if "text/plain" not in data:
        try:
            data["text/plain"] = repr(obj)
        except Exception as e:  # pragma: no cover - broken __repr__
            data["text/plain"] = "<unrepresentable object: %s>" % e
    for mime in ("image/png", "image/jpeg"):
        if mime in data:
            data[mime] = _b64(data[mime])
    return data, metadata


# --------------------------------------------------------------------------- magics

_IGNORED_MAGICS = {"matplotlib", "config", "load_ext", "reload_ext", "autoreload",
                   "aimport", "precision", "xmode", "env"}


def transform_source(code):
    """Turns IPython-style syntax (!cmd, %magic, %%cell magic) into plain Python."""
    stripped = code.lstrip()
    if stripped.startswith("%%"):
        first, _, body = stripped.partition("\n")
        parts = first[2:].split(None, 1)
        name = parts[0] if parts else ""
        arg = parts[1] if len(parts) > 1 else ""
        if name in ("bash", "sh", "script"):
            return "__pynb__.shell(%r)\n" % body
        if name == "time":
            return "__pynb__.timed(%r)\n" % body
        if name in ("capture", "writefile"):
            if name == "writefile":
                return "__pynb__.writefile(%r, %r)\n" % (arg, body)
            return body
        raise SyntaxError("Cell magic %%%%%s is not supported by the pynb kernel" % name)

    out = []
    for line in code.split("\n"):
        m = re.match(r"^(\s*)(!|%)(.*)$", line)
        if not m:
            out.append(line)
            continue
        indent, kind, rest = m.groups()
        if kind == "!":
            out.append("%s__pynb__.shell(%r)" % (indent, rest))
            continue
        parts = rest.split(None, 1)
        name = parts[0] if parts else ""
        arg = parts[1] if len(parts) > 1 else ""
        if name in ("pip", "conda"):
            cmd = "%s -m pip %s" % (shlex.quote(sys.executable), arg)
            out.append("%s__pynb__.shell(%r)" % (indent, cmd))
        elif name == "cd":
            out.append("%s__pynb__.cd(%r)" % (indent, arg))
        elif name == "pwd":
            out.append("%sprint(__import__('os').getcwd())" % indent)
        elif name == "time":
            out.append("%s__pynb__.timed(%r)" % (indent, arg))
        elif name in _IGNORED_MAGICS:
            out.append("%spass" % indent)
        else:
            raise SyntaxError("Line magic %%%s is not supported by the pynb kernel" % name)
    return "\n".join(out)


# --------------------------------------------------------------------------- kernel

class Kernel:
    def __init__(self):
        self.current = None
        self.execution_count = 0
        self.executing = False
        self.tasks = queue.Queue()
        self.last_activity = time.time()
        self.namespace = None
        self.reset()
        sys.stdout = StreamWriter(self, "stdout")
        sys.stderr = StreamWriter(self, "stderr")

    # helpers exposed to user code as __pynb__
    def reset(self):
        self.namespace = {"__name__": "__main__", "__builtins__": builtins,
                          "__pynb__": self, "display": self.display_objects}
        self.execution_count = 0

    def display_objects(self, *objs, **kwargs):
        if self.current is None:
            return
        for obj in objs:
            data, metadata = mime_bundle(obj)
            self.current.display(data, metadata)

    def shell(self, command):
        proc = subprocess.run(command, shell=True, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        print(proc.stdout.decode("utf-8", "replace"), end="")
        return None

    def cd(self, path):
        os.chdir(os.path.expanduser(path.strip().strip("'\"") or "~"))
        print(os.getcwd())

    def timed(self, code):
        start = time.time()
        exec(compile(code, "<timed>", "exec"), self.namespace)
        print("Wall time: %.3f s" % (time.time() - start))

    def writefile(self, path, body):
        with open(os.path.expanduser(path.strip()), "w", encoding="utf-8") as f:
            f.write(body)
        print("Writing %s" % path.strip())

    def flush_figures(self):
        plt = sys.modules.get("matplotlib.pyplot")
        if plt is None:
            return
        try:
            for num in plt.get_fignums():
                fig = plt.figure(num)
                buf = io.BytesIO()
                fig.savefig(buf, format="png", bbox_inches="tight")
                self.current.display({"image/png": base64.b64encode(buf.getvalue()).decode("ascii"),
                                      "text/plain": repr(fig)})
            plt.close("all")
        except Exception as e:
            self.current.stream("stderr", "pynb: could not render figure: %s\n" % e)

    def _patch_pyplot(self):
        plt = sys.modules.get("matplotlib.pyplot")
        if plt is not None and getattr(plt.show, "__name__", "") != "_pynb_show":
            def _pynb_show(*args, **kwargs):
                self.flush_figures()
            plt.show = _pynb_show

    # execution -------------------------------------------------------------
    def run_cell(self, code):
        self.execution_count += 1
        count = self.execution_count
        outputs = Outputs()
        self.current = outputs
        filename = "<cell %d>" % count
        status = "ok"
        try:
            self._patch_pyplot()
            source = transform_source(code)
            linecache.cache[filename] = (len(source), None, source.splitlines(True), filename)
            tree = ast.parse(source, filename, "exec")
            last_expr = None
            if tree.body and isinstance(tree.body[-1], ast.Expr) and not code.rstrip().endswith(";"):
                last_expr = ast.Expression(tree.body.pop().value)
            exec(compile(tree, filename, "exec"), self.namespace)
            self._patch_pyplot()
            if last_expr is not None:
                value = eval(compile(last_expr, filename, "eval"), self.namespace)
                if value is not None:
                    self.namespace["_"] = value
                    data, metadata = mime_bundle(value)
                    outputs.display(data, metadata, execution_count=count)
            self.flush_figures()
        except BaseException as e:  # includes KeyboardInterrupt / SystemExit from user code
            status = "error"
            self.flush_figures()
            self._report(outputs, e)
        finally:
            sys.stdout.flush()
            self.current = None
        return {"execution_count": count, "status": status, "outputs": outputs.items}

    def _report(self, outputs, exc):
        frames = traceback.extract_tb(exc.__traceback__)
        # keep only user frames: drop this script ("<string>") and, for syntax errors,
        # the compiler internals
        frames = [f for f in frames if f.filename != "<string>"]
        if isinstance(exc, SyntaxError):
            frames = []
        lines = []
        if frames:
            lines.append("Traceback (most recent call last):\n")
            lines.extend(traceback.format_list(frames))
        lines.extend(traceback.format_exception_only(type(exc), exc))
        outputs.error(type(exc).__name__, str(exc), [l.rstrip("\n") for l in lines])

    def serve_forever(self):
        """Runs cells on the main thread so SIGINT can interrupt blocking calls."""
        while True:
            try:
                task = self.tasks.get(timeout=60)
            except queue.Empty:
                if time.time() - self.last_activity > IDLE_TIMEOUT_SECONDS:
                    return
                continue
            except KeyboardInterrupt:
                continue
            if task is None:
                return
            code, reply = task
            self.executing = True
            try:
                result = self.run_cell(code)
            except KeyboardInterrupt:
                result = {"execution_count": self.execution_count, "status": "error",
                          "outputs": [{"output_type": "error", "ename": "KeyboardInterrupt",
                                       "evalue": "", "traceback": ["KeyboardInterrupt"]}]}
            finally:
                self.executing = False
            reply.put(result)

    def interrupt(self):
        if self.executing:
            os.kill(os.getpid(), signal.SIGINT)


# --------------------------------------------------------------------------- completion

import keyword
import pkgutil

MAX_COMPLETIONS = 60
_IDENT_TAIL = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)*\s*\.\s*[A-Za-z0-9_]*$"
                         r"|[A-Za-z_][A-Za-z0-9_]*$")
_FROM_IMPORT = re.compile(r"^\s*from\s+([\w.]+)\s+import\s+(?:.*,\s*)?\(?\s*(\w*)$")
_IMPORT = re.compile(r"^\s*(?:import\s+(?:[\w.]+\s*,\s*)*|from\s+)([\w.]*)$")
_module_names = None


def _kind(obj):
    import inspect
    if inspect.ismodule(obj):
        return "module"
    if inspect.isclass(obj):
        return "class"
    if callable(obj):
        return "function"
    return "instance"


def _top_level_modules():
    global _module_names
    if _module_names is None:
        names = set(sys.builtin_module_names)
        try:
            for info in pkgutil.iter_modules():
                names.add(info.name)
        except Exception:
            pass
        _module_names = sorted(n for n in names if n.isidentifier())
    return _module_names


def _rank(items, prefix):
    """Public names first (unless the user typed '_'), then alphabetical; deduplicated."""
    seen, out = set(), []
    for name, kind in items:
        if name in seen or not name.startswith(prefix):
            continue
        if name.startswith("_") and not prefix.startswith("_"):
            continue
        seen.add(name)
        out.append({"text": name, "type": kind})
    out.sort(key=lambda m: (m["text"].startswith("__"), m["text"].lower()))
    return out[:MAX_COMPLETIONS]


def _complete(code, cursor, namespace):
    before = code[:cursor]
    line = before[before.rfind("\n") + 1:]

    m = _FROM_IMPORT.match(line)
    if m:  # from pkg import <prefix>
        module_name, prefix = m.group(1), m.group(2)
        items = []
        try:
            import importlib
            module = sys.modules.get(module_name) or importlib.import_module(module_name)
            items = [(n, _kind(getattr(module, n, None))) for n in dir(module)]
            path = getattr(module, "__path__", None)
            if path:
                items += [(i.name, "module") for i in pkgutil.iter_modules(path)]
        except Exception:
            pass
        return _rank(items, prefix), len(prefix)

    m = _IMPORT.match(line)
    if m:  # import <pkg.sub> / from <pkg.sub>
        dotted = m.group(1)
        parent, _, prefix = dotted.rpartition(".")
        if not parent:
            return _rank([(n, "module") for n in _top_level_modules()], prefix), len(prefix)
        try:
            import importlib
            module = sys.modules.get(parent) or importlib.import_module(parent)
            items = [(i.name, "module") for i in pkgutil.iter_modules(getattr(module, "__path__", []) or [])]
        except Exception:
            items = []
        return _rank(items, prefix), len(prefix)

    # Inside a comment: nothing to suggest.
    if "#" in line and not ("'" in line or '"' in line):
        return [], 0

    m = _IDENT_TAIL.search(line)
    if not m:
        return [], 0
    expr = re.sub(r"\s+", "", m.group(0))
    if "." in expr:  # attribute access: walk the chain with getattr only, never call anything
        *chain, prefix = expr.split(".")
        if chain[0] in namespace:
            obj = namespace[chain[0]]
        elif hasattr(builtins, chain[0]):
            obj = getattr(builtins, chain[0])
        else:
            return [], len(prefix)
        try:
            for part in chain[1:]:
                obj = getattr(obj, part)
            names = dir(obj)
        except Exception:
            return [], len(prefix)
        items = []
        for n in names:
            try:
                items.append((n, _kind(getattr(obj, n))))
            except Exception:
                items.append((n, "instance"))
        return _rank(items, prefix), len(prefix)

    prefix = expr
    items = [(k, "keyword") for k in keyword.kwlist]
    items += [(n, _kind(v)) for n, v in list(namespace.items())]
    items += [(n, _kind(getattr(builtins, n))) for n in dir(builtins)]
    return _rank(items, prefix), len(prefix)


# --------------------------------------------------------------------------- signature help

_CALLEE = re.compile(r"(?<![\w.])([A-Za-z_]\w*(?:\s*\.\s*[A-Za-z_]\w*)*)\s*$")
_KWARG = re.compile(r"^\s*([A-Za-z_]\w*)\s*=(?!=)")
_DOC_SIGNATURE_LINE = re.compile(r"^[\w.]+\(.*\)")
_NOT_CALLS = set(keyword.kwlist)


def _open_call(code, cursor):
    """Innermost unclosed '(' before the cursor, skipping strings and comments.

    Returns (paren_index, argument_index, current_argument_text), or None when the cursor is
    not inside a call (or is inside a string or comment)."""
    stack = []  # [opening char, index, top-level commas, index after the last comma]
    i, n = 0, cursor
    while i < n:
        c = code[i]
        if c == "#":
            nl = code.find("\n", i, n)
            if nl < 0:
                return None
            i = nl
        elif c in "'\"":
            quote = code[i:i + 3] if code[i:i + 3] in ("'''", '"""') else c
            end = i + len(quote)
            while end < n:
                if code[end] == "\\":
                    end += 2
                    continue
                if code.startswith(quote, end) or (len(quote) == 1 and code[end] == "\n"):
                    break
                end += 1
            if end >= n:
                return None
            i = end + len(quote) - 1
        elif c in "([{":
            stack.append([c, i, 0, i + 1])
        elif c in ")]}":
            if stack:
                stack.pop()
        elif c == "," and stack:
            stack[-1][2] += 1
            stack[-1][3] = i + 1
        i += 1
    if not stack or stack[-1][0] != "(":
        return None
    _, at, commas, arg_start = stack[-1]
    return at, commas, code[arg_start:cursor]


def _resolve(expr, namespace):
    parts = expr.split(".")
    if parts[0] in namespace:
        obj = namespace[parts[0]]
    elif hasattr(builtins, parts[0]):
        obj = getattr(builtins, parts[0])
    else:
        raise LookupError(parts[0])
    for part in parts[1:]:
        obj = getattr(obj, part)  # attribute lookups only; nothing is called
    return obj


def _split_top_level(text):
    parts, depth, cur = [], 0, ""
    for ch in text:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def _first_doc_line(obj):
    """First line of the first docstring paragraph that isn't a list of signatures."""
    import inspect
    for paragraph in (inspect.getdoc(obj) or "").split("\n\n"):
        lines = [l.strip() for l in paragraph.strip().splitlines()]
        if lines and lines[0] and not _DOC_SIGNATURE_LINE.match(lines[0]):
            return lines[0][:200]
    return ""


def _signature_overloads(obj, name):
    """[(label, [(start, end, param_name, inspect kind)])] from inspect, else from the docstring."""
    import inspect
    try:
        sig = inspect.signature(obj)
    except (TypeError, ValueError):
        sig = None
    if sig is not None:
        label, spans = name + "(", []
        params = list(sig.parameters.values())
        for i, p in enumerate(params):
            if i:
                label += ", "
            if p.kind == p.KEYWORD_ONLY and (i == 0 or params[i - 1].kind not in (p.KEYWORD_ONLY, p.VAR_POSITIONAL)):
                label += "*, "
            text = str(p)
            spans.append((len(label), len(label) + len(text), p.name, p.kind))
            label += text
            if p.kind == p.POSITIONAL_ONLY and (i + 1 == len(params) or params[i + 1].kind != p.POSITIONAL_ONLY):
                label += ", /"
        return [(label + ")", spans)]

    # Builtins without a __text_signature__ (e.g. range) list their signatures at the top of
    # the docstring: "range(stop) -> range object" / "range(start, stop[, step]) -> ...".
    overloads = []
    short = name.split(".")[-1]
    for line in (getattr(obj, "__doc__", None) or "").splitlines()[:12]:
        m = re.match(r"^\s*(?:[\w.]+\.)?" + re.escape(short) + r"\((.*)\)", line)
        if not m:
            continue
        inner = m.group(1)
        label = name + "(" + inner + ")"
        spans, pos, keyword_only = [], len(name) + 1, False
        for part in _split_top_level(inner.replace("[", "").replace("]", "")):
            pname = part.strip().split("=")[0].strip()
            if pname in ("*", "/") or not pname:
                keyword_only = keyword_only or pname == "*"  # markers, not parameters
                continue
            at = label.find(pname, pos)
            if at < 0:
                continue
            if pname.startswith("**"):
                kind = inspect.Parameter.VAR_KEYWORD
            elif pname.startswith("*"):
                kind, keyword_only = inspect.Parameter.VAR_POSITIONAL, True
            elif keyword_only:
                kind = inspect.Parameter.KEYWORD_ONLY
            else:
                kind = inspect.Parameter.POSITIONAL_OR_KEYWORD
            spans.append((at, at + len(pname), pname.lstrip("*"), kind))
            pos = at + len(pname)
        overloads.append((label, spans))
    return overloads


def signature_help(code, cursor, namespace):
    import inspect
    call = _open_call(code, cursor)
    if not call:
        return None
    paren, arg_index, arg_text = call
    m = _CALLEE.search(code[:paren])
    if not m:
        return None
    expr = re.sub(r"\s+", "", m.group(1))
    if expr in _NOT_CALLS or code[:m.start(1)].rstrip().endswith(("def", "class")):
        return None
    try:
        obj = _resolve(expr, namespace)
    except Exception:
        return None
    if not callable(obj):
        return None
    overloads = _signature_overloads(obj, expr)
    if not overloads:
        return None

    kw = _KWARG.match(arg_text)
    keyword_name = kw.group(1) if kw else None
    P = inspect.Parameter

    def positional(spans):
        fixed = [i for i, sp in enumerate(spans) if sp[3] in (P.POSITIONAL_ONLY, P.POSITIONAL_OR_KEYWORD)]
        var = next((i for i, sp in enumerate(spans) if sp[3] == P.VAR_POSITIONAL), -1)
        return fixed, var

    def fits(spans):
        if keyword_name:
            return any(sp[2] == keyword_name for sp in spans)
        fixed, var = positional(spans)
        return arg_index < len(fixed) or var >= 0

    # First overload that can take the current argument: range(stop) vs range(start, stop, step).
    label, spans = next(((l, sp) for l, sp in overloads if fits(sp)), overloads[-1])

    active = -1
    if keyword_name:
        active = next((i for i, sp in enumerate(spans) if sp[2] == keyword_name), -1)
    else:
        fixed, var = positional(spans)
        if arg_index < len(fixed):
            active = fixed[arg_index]
        elif var >= 0:
            active = var
    return {"label": label, "params": [[a, b] for a, b, _, _ in spans], "active": active,
            "doc": _first_doc_line(obj)}


def complete(kernel, code, cursor):
    cursor = max(0, min(int(cursor), len(code)))
    namespace = dict(kernel.namespace)  # snapshot: a running cell may be changing it
    matches, prefix_length = _complete(code, cursor, namespace)
    try:
        signature = signature_help(code, cursor, namespace)
    except Exception:
        signature = None
    return {"matches": matches, "prefix_length": prefix_length, "signature": signature}


# --------------------------------------------------------------------------- HTTP API

def make_handler(kernel, token):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):
            pass

        def _send(self, status, payload):
            body = json.dumps(payload).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _authorized(self):
            if self.headers.get("Authorization", "") != "Bearer " + token:
                self._send(403, {"error": "forbidden"})
                return False
            kernel.last_activity = time.time()
            return True

        def do_GET(self):
            if not self._authorized():
                return
            if self.path == "/info":
                self._send(200, {
                    "kernel_version": KERNEL_VERSION,
                    "python_version": sys.version.split()[0],
                    "implementation": sys.implementation.name,
                    "executable": sys.executable,
                    "prefix": sys.prefix,
                    "cwd": os.getcwd(),
                    "pid": os.getpid(),
                    "busy": kernel.executing,
                    "execution_count": kernel.execution_count,
                })
            else:
                self._send(404, {"error": "not found"})

        def do_POST(self):
            if not self._authorized():
                return
            length = int(self.headers.get("Content-Length") or 0)
            try:
                payload = json.loads(self.rfile.read(length) or b"{}")
            except ValueError:
                self._send(400, {"error": "invalid json"})
                return
            if self.path == "/complete":
                try:
                    self._send(200, complete(kernel, payload.get("code", ""), payload.get("cursor", 0)))
                except Exception as e:
                    self._send(200, {"matches": [], "prefix_length": 0, "error": str(e)})
            elif self.path == "/execute":
                reply = queue.Queue(maxsize=1)
                kernel.tasks.put((payload.get("code", ""), reply))
                self._send(200, reply.get())
            elif self.path == "/interrupt":
                kernel.interrupt()
                self._send(200, {"status": "ok"})
            elif self.path == "/restart":
                kernel.interrupt()
                kernel.reset()
                self._send(200, {"status": "ok"})
            elif self.path == "/shutdown":
                self._send(200, {"status": "ok"})
                kernel.interrupt()
                kernel.tasks.put(None)
            else:
                self._send(404, {"error": "not found"})

    return Handler


def main(argv):
    port, token, token_file, ready_file = None, None, None, None
    args = list(argv)
    while args:
        arg = args.pop(0)
        if arg == "--port":
            port = int(args.pop(0))
        elif arg == "--token":
            token = args.pop(0)
        elif arg == "--token-file":
            token_file = args.pop(0)
        elif arg == "--ready-file":
            ready_file = args.pop(0)
    if token_file:
        try:
            with open(token_file) as f:
                token = f.read().strip()
        finally:
            try:
                os.remove(token_file)
            except OSError:
                pass
    if port is None or port < 0 or not token:
        sys.__stderr__.write("usage: pynb_kernel --port PORT (--token TOKEN | --token-file FILE)"
                             " [--ready-file FILE]\n")
        return 2

    # Background processes start with SIGINT ignored; restore Python's handler so
    # /interrupt can raise KeyboardInterrupt in the running cell.
    signal.signal(signal.SIGINT, signal.default_int_handler)

    kernel = Kernel()
    server = ThreadingHTTPServer(("127.0.0.1", port), make_handler(kernel, token))
    server.daemon_threads = True
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()
    if ready_file:
        tmp = ready_file + ".tmp"
        with open(tmp, "w") as f:
            f.write(str(port))
        os.replace(tmp, ready_file)  # atomic: readers never see a partial port
    sys.__stdout__.write("pynb kernel listening on 127.0.0.1:%d (python %s)\n"
                         % (port, sys.version.split()[0]))
    sys.__stdout__.flush()
    try:
        kernel.serve_forever()
    finally:
        server.shutdown()
        if ready_file:
            try:
                os.remove(ready_file)
            except OSError:
                pass
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
