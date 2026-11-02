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

1. Open a notebook and tap ▶ on a code cell (or choose **Connect to Termux .venv…** from the menu).
2. Enter the `.venv` path, e.g. `~/myproject/.venv`. The working directory defaults to the folder that contains the `.venv`.
3. Allow the **Run commands in Termux environment** permission when Android asks.

pynb starts `<venv>/bin/python` in Termux's background and talks to it over `127.0.0.1` with a random per-session token. The toolbar shows the Python version and whether the kernel is idle or busy.

### Running and saving

- **▶ / ■** on a cell runs it or stops it. **Run All** runs every code cell and stops at the first error.
- **Interrupt**, **Restart** (fresh variables) and **Disconnect** are in the menu.
- **Save** writes your edits and outputs into the `.ipynb` file. Only changed cells are rewritten, and the file keeps Jupyter's own formatting. Notebooks that cannot be overwritten (samples, read-only files) are saved with **Save As…**.

Supported: printed output, the value of the last line, `display()`, rich `_repr_*_` objects, matplotlib figures, tracebacks, `!shell commands`, `%pip`, `%cd`, `%pwd`, `%time`, `%%bash` and `%%writefile`. Outputs appear when a cell finishes (they are not streamed live), and `input()` is not supported.
