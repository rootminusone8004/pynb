"""
pynb kernel bridge - runs inside Termux with the interpreter of the chosen .venv.

Started by the pynb app through Termux's RUN_COMMAND intent:
    <venv>/bin/python -u -c "<this file>" --port <port> --token <token>

Standard library only. Listens on 127.0.0.1 and requires the token on every request.
Executes cells in one persistent namespace (like a Jupyter kernel) and answers with
nbformat v4 output dicts, so the app can store them in the .ipynb file unchanged.

Endpoints (JSON):
    GET  /info        interpreter details
    POST /execute     {"code": "..."} -> {"execution_count": n, "status": "ok"|"error", "outputs": [...]}
    POST /interrupt   raise KeyboardInterrupt in the running cell
    POST /restart     fresh namespace and execution counter
    POST /shutdown    stop the kernel process
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

KERNEL_VERSION = 1
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
            if self.path == "/execute":
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
    port, token = None, None
    args = list(argv)
    while args:
        arg = args.pop(0)
        if arg == "--port":
            port = int(args.pop(0))
        elif arg == "--token":
            token = args.pop(0)
    if not port or not token:
        sys.__stderr__.write("usage: pynb_kernel --port PORT --token TOKEN\n")
        return 2

    # Background processes start with SIGINT ignored; restore Python's handler so
    # /interrupt can raise KeyboardInterrupt in the running cell.
    signal.signal(signal.SIGINT, signal.default_int_handler)

    kernel = Kernel()
    server = ThreadingHTTPServer(("127.0.0.1", port), make_handler(kernel, token))
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    sys.__stdout__.write("pynb kernel listening on 127.0.0.1:%d (python %s)\n"
                         % (port, sys.version.split()[0]))
    sys.__stdout__.flush()
    try:
        kernel.serve_forever()
    finally:
        server.shutdown()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
