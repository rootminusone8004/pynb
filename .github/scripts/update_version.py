#!/usr/bin/env python3
"""
Automatic versioning, run by the git pre-commit hook (.githooks/pre-commit) so the
version bump and Fastlane changelog land in your own commit.

  python3 .github/scripts/update_version.py           # update files (pre-commit hook)
  python3 .github/scripts/update_version.py --check   # CI: fail if files are out of date

1. Resolves the release version:
   - a pushed tag ``vX.Y.Z`` wins;
   - otherwise the newest ``## [X.Y.Z]`` heading of CHANGELOG.md is used
     (an ``## [Unreleased]`` section is ignored).
2. Computes ``versionCode = major * 10000 + minor * 100 + patch``
   (1.0.1 -> 10001, 1.2.0 -> 10200, 2.0.0 -> 20000).
3. Writes ``versionCode`` / ``versionName`` into app/build.gradle.
4. Writes that version's CHANGELOG.md section, as plain text, to
   ``fastlane/metadata/android/en-US/changelogs/<versionCode>.txt``
   (the file F-Droid and Google Play read for "What's new").

In --check mode nothing is written; the script fails if a file would change.

Outputs (GITHUB_OUTPUT): version, version_code, tag_name, changed.
"""
import os
import re
import sys
from pathlib import Path

FASTLANE_CHANGELOG_DIR = Path("fastlane/metadata/android/en-US/changelogs")
BUILD_GRADLE = Path("app/build.gradle")
CHANGELOG = Path("CHANGELOG.md")
# F-Droid and Google Play both cap "What's new" text at 500 characters.
MAX_CHANGELOG_CHARS = 500

VERSION_HEADING = re.compile(r"^##\s*\[([0-9]+\.[0-9]+\.[0-9]+)\]", re.MULTILINE)


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def repo_root():
    here = Path.cwd()
    if (here / CHANGELOG).exists():
        return here
    return Path(__file__).resolve().parents[2]


def resolve_version(changelog_text):
    ref = os.environ.get("GITHUB_REF", "")
    if ref.startswith("refs/tags/v"):
        return ref[len("refs/tags/v"):]
    match = VERSION_HEADING.search(changelog_text)
    if not match:
        fail("Could not find a '## [X.Y.Z]' heading in CHANGELOG.md")
    return match.group(1)


def compute_version_code(version):
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", version)
    if not match:
        fail(f"Version '{version}' is not in X.Y.Z form")
    major, minor, patch = (int(p) for p in match.groups())
    if minor > 99 or patch > 99:
        fail(f"Version '{version}': minor and patch must be <= 99 for the versionCode scheme")
    return major * 10000 + minor * 100 + patch


def changelog_section(changelog_text, version):
    pattern = rf"^##\s*\[{re.escape(version)}\][^\n]*\n(.*?)(?=^##\s*\[|\Z)"
    match = re.search(pattern, changelog_text, re.DOTALL | re.MULTILINE)
    return match.group(1).strip() if match else ""


def to_plain_text(markdown):
    """Converts a Keep-a-Changelog section into the plain-text store format."""
    lines = []
    for raw in markdown.splitlines():
        line = raw.rstrip()
        if not line.strip():
            continue
        line = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", line)  # links -> text
        line = re.sub(r"(\*\*|__)(.+?)\1", r"\2", line)          # bold
        line = line.replace("`", "")
        heading = re.match(r"^#{1,6}\s*(.+)$", line)
        if heading:
            lines.append(heading.group(1).strip())
            continue
        bullet = re.match(r"^(\s*)[-*+]\s+(.*)$", line)
        if bullet:
            depth = len(bullet.group(1).expandtabs(4)) // 2
            lines.append("  " * (depth + 1) + "- " + bullet.group(2).strip())
            continue
        lines.append(line.strip())
    return "\n".join(lines)


def truncate(text, limit):
    if len(text) <= limit:
        return text
    kept = []
    for line in text.splitlines():
        candidate = "\n".join(kept + [line])
        if len(candidate) > limit - 4:
            break
        kept.append(line)
    print(f"::warning::Changelog is longer than {limit} characters; truncated for the store")
    return "\n".join(kept + ["..."])


CHECK_ONLY = "--check" in sys.argv[1:]
STALE = []


def write_text(path, content):
    if CHECK_ONLY:
        STALE.append(str(path))
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def update_build_gradle(path, version, version_code):
    text = path.read_text(encoding="utf-8")
    current = re.search(r"^\s*versionCode\s+(\d+)\s*$", text, re.MULTILINE)
    if not current or not re.search(r'^\s*versionName\s+"[^"]*"\s*$', text, re.MULTILINE):
        fail(f"{path} must contain literal 'versionCode <int>' and 'versionName \"<x.y.z>\"' lines")
    if int(current.group(1)) > version_code:
        fail(f"versionCode would go down ({current.group(1)} -> {version_code}); bump CHANGELOG.md")

    new = re.sub(r"^(\s*versionCode\s+)\d+\s*$", rf"\g<1>{version_code}", text, count=1, flags=re.MULTILINE)
    new = re.sub(r'^(\s*versionName\s+)"[^"]*"\s*$', rf'\g<1>"{version}"', new, count=1, flags=re.MULTILINE)
    if new != text:
        write_text(path, new)
        return True
    return False


def write_if_changed(path, content):
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return False
    write_text(path, content)
    return True


def main():
    root = repo_root()
    changelog_text = (root / CHANGELOG).read_text(encoding="utf-8")

    version = resolve_version(changelog_text)
    version_code = compute_version_code(version)

    changed = update_build_gradle(root / BUILD_GRADLE, version, version_code)

    notes = to_plain_text(changelog_section(changelog_text, version))
    if notes:
        txt = root / FASTLANE_CHANGELOG_DIR / f"{version_code}.txt"
        changed |= write_if_changed(txt, truncate(notes, MAX_CHANGELOG_CHARS) + "\n")
        print(f"Fastlane changelog: {txt.relative_to(root)}")
    else:
        print(f"::warning::No CHANGELOG.md section for {version}; fastlane changelog not written")

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as f:
            f.write(f"version={version}\n")
            f.write(f"version_code={version_code}\n")
            f.write(f"tag_name=v{version}\n")
            f.write(f"changed={'true' if changed else 'false'}\n")

    print(f"versionName: {version}")
    print(f"versionCode: {version_code}")
    if CHECK_ONLY:
        if STALE:
            fail("Version files are out of date: " + ", ".join(STALE)
                 + ". Run 'python3 .github/scripts/update_version.py' (or enable the hook with "
                 + "'git config core.hooksPath .githooks') and commit the result.")
        print("Version files are up to date")
    else:
        print(f"Files changed: {changed}")


if __name__ == "__main__":
    main()
