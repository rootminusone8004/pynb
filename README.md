# pynb 📓

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="pynb Icon" width="84" />
</p>

<p align="center">
  <strong>A fast, buttery-smooth native Java Jupyter Notebook (.ipynb) reader and workspace explorer for Android.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Build_Pipeline-passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white" alt="Pipeline Status" />
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android Platform" />
  <img src="https://img.shields.io/badge/Language-Java%2017-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java Language" />
  <img src="https://img.shields.io/badge/Theme-Vibrant%20Orange-E44919?style=for-the-badge" alt="Vibrant Orange" />
  <img src="https://img.shields.io/badge/Size-%3C%202.5%20MB-blue?style=for-the-badge" alt="App Size" />
  <img src="https://img.shields.io/badge/License-MIT-orange?style=for-the-badge" alt="MIT License" />
</p>

<p align="center">
  <a href="../../actions/workflows/release.yml">
    <img src="https://github.com/rootminusone8004/pynb/actions/workflows/release.yml/badge.svg?branch=master" alt="Build &amp; Release Status" />
  </a>
</p>

---

## 📖 Overview

**pynb** is a native Java Android application built to display, inspect, and explore **Jupyter Notebook (`.ipynb`)** files with fluid 60/120 FPS performance.

Unlike traditional viewers that rely on heavy, memory-intensive nested WebViews, **pynb** uses native Android components with custom `ViewHolder` recycling. Code cells, Markdown text, terminal streams, and figures render instantly while consuming minimal RAM and battery life.

---

## 🌟 Core Features & Highlights

### ⚡ 1. Butter-Smooth Native RecyclerView Engine

- **60 / 120 FPS Scrolling**: Zero WebView stutter. Each notebook cell is recycled natively.
- **ViewHolder Specialization**: Separate optimized ViewHolders for Markdown cells, Code cells, and Raw cells.
- **Background Parsing**: Notebooks are parsed asynchronously on a dedicated thread pool so the UI thread never drops a single frame.

### 📝 2. Native Markdown Formatting via Markwon

- **Full Markdown Typography**: Powered by **Markwon**, rendering headings (H1–H6), bulleted and numbered lists, GitHub Flavored Markdown tables, blockquotes, and code blocks.
- **Source / Rendered Toggle**: Tap "Source" on any Markdown card to switch between rendered rich text and raw Markdown syntax.
- **One-Tap Copy**: Quick copy button on every Markdown header.

### 🐍 3. Python Syntax Highlighting

- **Microsecond Regex Tokenizer**: Highlights Python keywords, builtins, constants, numbers, strings, decorators, and comments directly into native `Spannable` spans.
- **Line Numbers Toggle**: Line numbers column can be shown or hidden based on reading preference.
- **Monospace Code Typography**: Clean monospace rendering with horizontal scrolling for extra-wide statements.
- **Collapse / Expand**: Fold long code blocks to focus on surrounding outputs or explanations.

### 📊 4. Full Jupyter Output Fidelity

- **Stream Outputs (`stdout` / `stderr`)**: Clean monospace containers with real-time ANSI escape code translation (`\u001b[31m`, `\u001b[32m`, etc.) into vivid Android colors.
- **Error Tracebacks**: Soft-red error diagnostics container with colorized traceback lines matching JupyterLab and VS Code styling.
- **Matplotlib & Seaborn Plots**: Decodes Base64 PNG/JPEG figures and caches bitmaps in an in-memory LRU cache to prevent re-decoding during scrolling.
- **Pinch-to-Zoom Fullscreen Plot Viewer**: Tap any figure to open an interactive viewer with pinch-to-zoom, pan, double-tap zoom, and image sharing.

### 📁 5. Folder Workspace Explorer

- **Directory Traversal**: Open any project folder using Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`).
- **Interactive Breadcrumbs**: Tap any path segment in the breadcrumb bar (`📁 Root > Project > Week1`) to jump up the directory hierarchy instantly.
- **Live Search & Filter**: Search files within the directory in real-time and toggle between `.ipynb Only` and `All Files`.
- **Recent Workspaces**: Remembers previously opened project folders so you can return in one tap.

### 📑 6. Table of Contents (Outline)

- **Automated Header Extraction**: Scans all Markdown headings (`#`, `##`, `###`) to build a clean outline.
- **Bottom-Sheet Navigation**: Open the outline bottom sheet and tap any heading to jump smoothly to that section.

### 🪶 7. Extreme APK Optimization (< 2.5 MB)

- **R8 Code Shrinking**: Dead code elimination strips unused dependencies.
- **Resource Shrinking**: Unused drawables and XML layouts are automatically stripped during release builds.
- **Tiny Footprint**: Releases compile down to **~2.1 MB** (over 70% reduction from unoptimized debug builds).

### 🔒 8. 100% Offline & Private

- **Zero Permissions**: No `android.permission.INTERNET` requested.
- **No Telemetry**: No analytics, trackers, ads, or external network requests.
- **Scoped Storage**: Reads files strictly through Android's modern Storage Access Framework without broad storage access requests.

---

## 📂 Project Structure

```text
pynb/
├── .github/
│   ├── scripts/
│   │   ├── generate_release_notes.py   # Extracts release notes from CHANGELOG.md
│   │   └── generate_summary.py         # Generates Markdown step summary with hashes
│   └── workflows/
│       └── release.yml                 # Automated release & APK publication workflow
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/samples/         # Preloaded sample notebooks (Data Science, ML, Python)
│   │   │   ├── java/com/pynb/app/
│   │   │   │   ├── adapter/            # RecyclerView adapters (Notebook, Folder, Recents)
│   │   │   │   ├── model/              # Data models (Notebook, Cell, Output, FolderItem)
│   │   │   │   ├── parser/             # Notebook JSON parser, Python highlighter, ANSI parser
│   │   │   │   ├── ui/                 # Activities & Dialogs (Main, FolderBrowser, Viewer)
│   │   │   │   ├── util/               # FileUtils, ImageCache, RecentManagers
│   │   │   │   └── view/               # Custom ZoomableImageView with touch gestures
│   │   │   └── res/                    # Colors, drawables, layouts, and adaptive mipmaps
│   │   └── test/java/com/pynb/app/     # JUnit unit tests for parser, outline, and ANSI
│   ├── build.gradle                    # App module Gradle configuration (R8 & signing)
│   └── proguard-rules.pro              # ProGuard & R8 optimization rules
├── fastlane/
│   ├── metadata/android/en-US/         # Fastlane store metadata, icons & changelogs
│   ├── Appfile                         # Fastlane app configuration
│   ├── Fastfile                        # Fastlane lanes (test, build, release, beta)
│   └── README.md                       # Fastlane usage guide
├── BUILD_INFO.md                       # Build environment & SDK specifications
├── CHANGELOG.md                        # Project change log (Keep a Changelog standard)
├── Gemfile                             # Ruby Gemfile for Fastlane
├── LICENSE                             # MIT License
├── README.md                           # Main project documentation
├── SECURITY.md                         # Security and disclosure policy
└── USAGE.md                            # Comprehensive user manual
```

---

## 🛠️ Building From Source

### Prerequisites
- **JDK 17** or later
- **Android SDK** with Platform API 34 and Build-Tools 34.0.0+

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Build Optimized Release APK
```bash
./gradlew assembleRelease
```
The optimized, shrunk release APK (~2.1 MB) will be generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 🤖 Fastlane Automation

You can run Fastlane tasks locally using Bundler:

```bash
bundle install
bundle exec fastlane android test     # Run all unit tests
bundle exec fastlane android release  # Build optimized release APK
```

---

## 📄 License

This project is open-source and licensed under the [MIT License](LICENSE).
