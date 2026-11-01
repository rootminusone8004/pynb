# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
