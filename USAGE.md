# App Usage Guide

## How to Start the App
  1. Locate the **pynb** icon on your Android home screen or app drawer.
  2. Tap the icon to launch the app.

---

## Opening Notebooks

There are three convenient ways to open and read Jupyter notebooks in **pynb**:

### 1. Folder Workspace Explorer
  - Tap **"Choose Folder Workspace"** on the home screen.
  - The Android system directory selector appears. Navigate to any folder containing your Python scripts or Jupyter notebooks (e.g. `Downloads`, `Documents/PythonProjects`, an SD card, or Google Drive folder).
  - Tap **"Use this folder"** and grant read permission.
  - You can now traverse subfolders, jump across breadcrumb ancestors, filter by `.ipynb Only`, or search files in real time.
  - Tap any `.ipynb` file to open it in the reader.

### 2. Single Notebook File Picker
  - Tap **"Open Single Notebook (.ipynb)"** on the home screen.
  - Select any `.ipynb` or `.json` file from your device storage or cloud provider.

### 3. External File Managers & Browsers
  - Whenever you tap an `.ipynb` file in WhatsApp, Telegram, Gmail, Chrome, or any Android file manager, select **pynb** from the "Open with" app chooser to inspect the notebook immediately.

### 4. Built-In Sample Notebooks
  - From the main screen, tap any of the three preloaded sample notebooks:
    - **Data Science & Matplotlib**: Pandas DataFrames, summary statistics, and convergence plots.
    - **Machine Learning & Diagnostics**: Classifier evaluation metrics and error traceback examples.
    - **Python Fundamentals & Syntax**: OOP classes, lambdas, and stdout outputs.

---

## Inside the Notebook Viewer

- **Smooth Scrolling**: Scroll naturally through hundreds of cells at 60/120 FPS.
- **Copy Code / Markdown**: Tap the copy icon on any code or markdown cell header to copy its contents to the clipboard.
- **Source / Rendered Toggle**: Tap the "Source" button on any Markdown cell to inspect the raw markdown syntax.
- **Collapse / Expand**: Tap the collapse arrow on long code blocks to fold or unfold the cell.
- **Inspect Plots**: Tap any Matplotlib or Seaborn figure to open it in fullscreen mode with pinch-to-zoom and pan.
- **Table of Contents (Outline)**: Tap the Outline icon in the top toolbar to view all Markdown headings and jump straight to any section.
- **Search in Notebook**: Tap the magnifying glass icon in the top toolbar to search across cell code and text. Use the arrows to cycle between matches.
- **Font Size Scaling**: Use `A+` or `A-` from the top menu to adjust text sizing for comfortable reading.
- **Line Numbers**: Toggle line numbers on/off from the options menu.

## Editing Cells

Tap ✏️ on a markdown or code cell to open the full-screen editor.

- **Code cells**: Python syntax highlighting, automatic indentation (a new line keeps the previous line's indentation and adds a level after a `:`), and a key bar with **⇥** (indent; indents every selected line), **⇤** (unindent) and symbols that are awkward on phone keyboards.
- **Markdown cells**: a key bar with `#`, `*`, `` ` ``, `$`, `>`, `|` and brackets.
- **✓ Done** applies the edit; **▶ Apply and run** also runs a code cell (see below). Closing with ✕ or Back asks before discarding changes.

Edited cells keep their outputs until they are run again. Use **Save** to write the changes into the `.ipynb` file.

### Code suggestions

When a kernel is connected (Termux or SSH server), typing in a code cell's editor shows suggestions in a strip above the key bar. Tap one to insert it. They come from the running Python session: variables defined by earlier cells and their attributes (`df.` → `head`, `describe`…), functions, keywords, builtins, and installed modules after `import` / `from … import`. Inside a call, a bar shows the function's parameters with the one you are typing highlighted. For example, typing `range(1, ` shows `range(start, stop[, step])` with `stop` highlighted. The bar goes away when you type `)`. Suggestions and hints only appear while connected. A kernel started by an older pynb version has no suggestions until you **Disconnect** and connect again.

## Running Cells with Termux

pynb can run code cells with the Python interpreter of a virtual environment (`.venv`) in [Termux](https://termux.dev). Variables persist between cells, like in Jupyter.

### One-time setup (in Termux)

1. Allow other apps to start commands in Termux:
   ```sh
   echo "allow-external-apps=true" >> ~/.termux/termux.properties
   termux-reload-settings
   ```
2. Create a virtual environment if you do not have one:
   ```sh
   pkg install python
   python -m venv ~/myproject/.venv
   ```
   For numpy / matplotlib, install them with `pkg install python-numpy matplotlib` and create the venv with `--system-site-packages`.

### Connecting

1. Open a notebook and tap ▶ on a code cell (or choose **Connect to Python…** from the menu), and pick **Termux**.
2. Enter the `.venv` path, e.g. `~/myproject/.venv`. The working directory defaults to the folder that contains the `.venv`.
3. Allow the **Run commands in Termux environment** permission when Android asks.

pynb starts `<venv>/bin/python` in Termux's background and talks to it over `127.0.0.1` with a random per-session token. The toolbar shows the Python version and whether the kernel is idle or busy.

## Running Cells on an SSH Server (AWS EC2, VPS…)

Cells can also run on a Linux or macOS server you can reach over SSH, with the Python of a `.venv` on that server.

### One-time setup (on the server)

```bash
python3 -m venv ~/myproject/.venv
~/myproject/.venv/bin/pip install numpy pandas matplotlib   # whatever your notebooks need
```

For AWS EC2, the security group only needs inbound **SSH (port 22)**. The kernel is never exposed to the internet.

### Connecting

1. Choose **Connect to Python…** and pick **SSH server**.
2. Enter the host (e.g. `ec2-1-2-3-4.compute.amazonaws.com`), port and user. On EC2 the user is usually `ec2-user` (Amazon Linux), `ubuntu` (Ubuntu) or `admin` (Debian).
3. Choose **Private key** and select your key file (the EC2 `.pem`, `id_ed25519`, `id_rsa`…; add its passphrase if it has one), or choose **Password**.
4. Enter the `.venv` path on the server, e.g. `~/myproject/.venv`.
5. The first time, pynb shows the server's host-key fingerprint. Compare it with the server's own (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`) and tap **Trust and connect**. If the key changes later, pynb warns you before connecting.

pynb copies its small kernel script to `~/.pynb/` on the server, starts it with `<venv>/bin/python` listening only on the server's `127.0.0.1`, and reaches it through an SSH port forward. Servers are saved for next time; keys and passwords are encrypted with the Android Keystore. **Forget this server** removes a server and its credentials.

Good to know:

- Code, files, `!shell`, `%pip` and `%cd` act on the **server**. The notebook itself is still saved on your phone.
- The server kernel keeps running if the connection drops or the app is closed (it stops by itself after 4 hours idle). pynb reconnects to it automatically, so your variables are kept. If the connection drops *while* a cell runs, that cell's output is lost.
- The server needs `sh` and must allow SSH port forwarding (`AllowTcpForwarding`, on by default). Windows servers, PuTTY `.ppk` keys (convert them with `puttygen key.ppk -O private-openssh -o key.pem`) and two-factor prompts are not supported.

### Running and saving

- **▶ / ■** on a cell runs it or stops it. **Run All** runs every code cell and stops at the first error.
- **Interrupt**, **Restart** (fresh variables) and **Disconnect** are in the menu.
- **Save** writes your edits and outputs into the `.ipynb` file. Only changed cells are rewritten, and the file keeps Jupyter's own formatting. Notebooks that cannot be overwritten (samples, read-only files) are saved with **Save As…**.

Supported: printed output, the value of the last line, `display()`, rich `_repr_*_` objects, matplotlib figures, tracebacks, `!shell commands`, `%pip`, `%cd`, `%pwd`, `%time`, `%%bash` and `%%writefile`. Outputs appear when a cell finishes (they are not streamed live), and `input()` is not supported.
