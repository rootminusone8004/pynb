# Changelog

All notable changes to the **pynb** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.2] - 2026-11-05
### Fixed
- **Reproducible builds**: Release APKs are now built with JDK 21, the same as F-Droid's build server, so F-Droid's build is byte-for-byte identical and it can ship the developer-signed APK.

## [1.4.1] - 2026-11-05
### Changed
- **App name**: It got a new app string!
- **F-droid**: Made it compatible for F-droid integration

## [1.4.0] - 2026-11-04
### Added
- **Code suggestions**: While editing a code cell, a suggestion strip above the key bar offers completions from the connected kernel (Termux or SSH server): your variables and their attributes, functions, keywords, builtins, and importable modules for `import` / `from … import`. They come from the live session, so they know what earlier cells defined, and they work while a cell is running. Nothing is added to the app; without a connected kernel no suggestions are shown.
- **Parameter hints**: Typing `(` after a function (e.g. `range(`) shows its parameters above the suggestions, with the one you are typing highlighted. The hint follows `,` and `name=` arguments, picks the matching form for functions with several (like `range(stop)` / `range(start, stop[, step])`), shows the first line of the docstring, and disappears when `)` closes the call. It also comes from the connected kernel.
- **Run cells on an SSH server**: Connect to a `.venv` on AWS EC2, a VPS or any Linux/macOS server with a private key (`.pem`, ed25519, RSA, ECDSA; passphrases supported) or a password. The kernel listens only on the server's localhost and is reached through an SSH tunnel, keeps running across connection drops, and pynb reconnects to it automatically. Servers are saved, credentials are encrypted with the Android Keystore, and host keys are verified (trust on first use, with a warning if a key changes).
- **Mermaid diagrams**: ```` ```mermaid ```` blocks in markdown cells now render as diagrams (flowcharts, sequence, class, state, Gantt, pie, ...). Rendering is fully offline, follows the light/dark theme, shows parse errors inline, and tapping a diagram opens it in the zoomable image viewer.

## [1.3.0] - 2026-11-03
### Added
- **Run cells with Termux**: Connect to a Python `.venv` in Termux and run code cells (▶ per cell, Run All, Interrupt, Restart). Supports printed output, results, `display()`, matplotlib figures, errors, `!shell`, `%pip` and `%cd`.
- **Edit cells**: ✏️ on markdown and code cells opens a full-screen editor. Code gets Python highlighting, auto-indent and a key bar (Tab, unindent, brackets, quotes); markdown gets a markdown key bar. "Apply and run" runs the edited code cell.
- **Save / Save As**: Save edits and executed outputs back into the `.ipynb` file. Only the cells you changed are rewritten, and the file keeps Jupyter's formatting.

### Changed
- **Release automation**: The version bump and Fastlane changelog are now made by a git pre-commit hook in your own commit; CI only checks them and no longer commits.

## [1.2.0] - 2026-11-02
### Changed
- **App color**: Switched to Budjet's deep orange tone (`#E44919`).
- **Release automation**: GitHub Actions now sets `versionCode`/`versionName` from CHANGELOG.md and writes the Fastlane changelog (`changelogs/<versionCode>.txt`) for each release.

### Fixed
- **Math rendering**: Text between formulas was sometimes dropped, and prices like `$5` could be mistaken for math.
- **Display math**: `$$...$$`, `\[...\]` and `\begin{...}` now render as centered display equations. Multi-line formulas no longer break on blank lines or on lines that look like Markdown.
- **Inline math** now sits on the text baseline, and wide equations shrink to fit the screen instead of being clipped.
- **MathJax compatibility**: `equation`, starred environments (`align*`...), `\label`, `\tag`, `\lvert`/`\rvert`, `\bm`, `\coloneqq` and `\operatorname*` are now handled.

## [1.0.1] - 2026-10-16
### Changed
- **Deep Orange tone**: The orange color has been deepened.

## [1.0.0] - 2026-09-18

### Added
- **Native RecyclerView Engine**: Smooth 60/120 FPS rendering of Jupyter Notebooks without laggy nested WebViews.
- **Markwon Markdown Integration**: Full Markdown support including headings (H1-H6), lists, tables, blockquotes, and code blocks.
- **Python Syntax Highlighter**: Regex token syntax highlighting with monospace font, line numbers toggle, and one-tap code copying.
- **Output Types Support**: Native display for stdout/stderr streams, Matplotlib/Seaborn plot figures, and ANSI-colored error tracebacks.
- **Fullscreen Image Inspection**: `ZoomableImageView` dialog supporting pinch-to-zoom, pan, double-tap zoom, and image sharing.
- **Folder Workspace Explorer**: Traverse directory trees using Android's Storage Access Framework with breadcrumb jumping and live search.
- **Table of Contents (Outline)**: Bottom-sheet outline extracted from Markdown headers for instant navigation across large notebooks.
- **Embedded Sample Notebooks**: Data Science, Machine Learning, and Python Fundamentals sample notebooks included.
- **R8 Code & Resource Shrinking**: Release APK optimized down to ~2.1 MB.
- **Fastlane & GitHub Actions**: Full automation for testing, release notes generation, and automated GitHub Releases.
