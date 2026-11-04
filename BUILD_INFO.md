# 🧰 Build Information

## Project Information
- **Project Name:** pynb
- **Package Name:** `com.rootminusone8004.pynb`
- **Primary Language:** Java 17 (app), Python 3 standard library (kernel bridge)
- **Target Platform:** Android 7.0+ (API 24 to 34)

## Build Environment
- **Java Development Kit (JDK):** OpenJDK 17
- **Gradle Version:** 8.12
- **Android Gradle Plugin (AGP):** 8.7.3
- **Android Studio / IntelliJ IDEA:** 2024.1+

## SDK and Tools
- **compileSdkVersion:** 34
- **buildToolsVersion:** 34.0.0
- **minSdkVersion:** 24 (Android 7.0 Nougat)
- **targetSdkVersion:** 34 (Android 14)

## Core Dependencies
All dependencies come from Maven Central or Google's Maven repository.

- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.activity:activity:1.9.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.cardview:cardview:1.0.0`
- `androidx.documentfile:documentfile:1.0.1`
- `androidx.lifecycle:lifecycle-viewmodel:2.8.3` / `lifecycle-livedata:2.8.3`
- `com.google.code.gson:gson:2.10.1`
- `io.noties.markwon:core|ext-tables|html|image|ext-latex|inline-parser:4.6.2` (Markdown & LaTeX)
- `com.github.mwiede:jsch:2.28.8` (SSH kernels)
- `org.bouncycastle:bcprov-jdk18on:1.83` (ed25519 / curve25519 for SSH)
- `junit:junit:4.13.2`, `org.json:json:20240303` (Unit tests)

## Bundled Assets
- `app/src/main/assets/kernel/pynb_kernel.py`: the Python kernel bridge (our own source, standard library only).
- `app/src/main/assets/mermaid/mermaid.min.js`: [Mermaid](https://github.com/mermaid-js/mermaid) **11.4.1** (MIT), the unmodified `dist/mermaid.min.js` from the npm package, used offline to draw diagrams.
  - Source: `https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js`
  - SHA-256: `a43bc1afd446f9c4cc66ac5dd45d02e8d65e26fc5344ec0ef787f88d6ddb6f9e`
- `app/src/main/assets/samples/*.ipynb`: sample notebooks.

## Build Status
- **Success:** Yes
- **Code Minification & Shrinking:** R8 enabled (`minifyEnabled true`, `shrinkResources true`)
- **Release APK Size:** ~5 MB
- **CI / CD Pipeline:** GitHub Actions Automated Release (`.github/workflows/release.yml`)

---

## 🛠️ Build Instructions

### Common Prerequisites
1. **Git**: Ensure [Git](https://git-scm.com/) is installed.
   - *Arch Linux*: `sudo pacman -S git`
   - *Ubuntu / Debian*: `sudo apt install git`
   - *macOS*: `brew install git`

2. **Java 17 (JDK)**: Ensure OpenJDK 17 is installed and set as `JAVA_HOME`.
   - *Arch Linux*: `sudo pacman -S jdk17-openjdk`
   - *Ubuntu / Debian*: `sudo apt install openjdk-17-jdk`
   - *macOS*: `brew install openjdk@17`

3. **Android SDK**: Platform API 34 and Build-Tools 34.0.0 (installed by Android Studio, or with `sdkmanager "platforms;android-34" "build-tools;34.0.0"`). Point to it with `ANDROID_HOME` or a `local.properties` file (`sdk.dir=/path/to/sdk`).

4. **Clone the Repository**:
   ```bash
   git clone "https://github.com/rootminusone8004/pynb"
   cd pynb
   ```

---

### Method 1: Android Studio / IntelliJ IDEA

1. **Launch Android Studio**.
2. Select **Open** and select the cloned `pynb` repository directory.
3. Wait for Gradle sync to complete and download required dependencies.
4. **Run Unit Tests**: Right-click on `app/src/test` and select **Run 'Tests in 'test''**.
5. **Build APK**:
   - In the top menu, go to **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
   - The compiled debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
6. **Run on Device or Emulator**: Click the green **Run (Play)** button in the toolbar.

---

### Method 2: Command Line (Terminal)

1. **Grant execute permission to Gradle Wrapper**:
   ```bash
   chmod +x gradlew
   ```

2. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

3. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

4. **Build Minified Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```
   - With a keystore at `app/release.jks` (passwords from the `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` environment variables): `app/build/outputs/apk/release/app-release.apk`, signed.
   - Without a keystore: `app/build/outputs/apk/release/app-release-unsigned.apk`. This is the build F-Droid uses; it signs the APK itself.

5. **Install on Connected Device via ADB**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🤖 F-Droid

pynb is built to meet F-Droid's inclusion policy:

- **100% free software**: the app is MIT licensed, and every dependency is open source (see the license table in [README.md](README.md)). No Google Play Services, Firebase, analytics or ads.
- **Builds from source** with plain `./gradlew assembleRelease` (Gradle subdir `app`), from Maven Central and Google's Maven repository only.
- **Unsigned release when no keystore is present**, so F-Droid can sign it (release signing is only applied if `app/release.jks` exists).
- **No dependency metadata blob**: `dependenciesInfo { includeInApk = false; includeInBundle = false }`.
- **Store listing** lives in `fastlane/metadata/android/en-US/` (title, short and full description, icon, feature graphic, screenshots, and per-release changelogs named `<versionCode>.txt`).
- **Versioning**: `versionCode = major * 10000 + minor * 100 + patch` (e.g. `1.3.0` → `10300`). Releases are tagged `vX.Y.Z`.
- **Network use**: the `INTERNET` permission is used only for SSH connections to servers the user adds; there are no other network requests, so no anti-features apply.

---

## 🏷️ Versioning & Releases

The version comes from `CHANGELOG.md`. A git pre-commit hook (`.githooks/pre-commit`, enable it once with `git config core.hooksPath .githooks`) runs `.github/scripts/update_version.py`, which:

1. Takes the newest `## [X.Y.Z]` heading of `CHANGELOG.md` (an `## [Unreleased]` section is ignored).
2. Writes `versionCode` / `versionName` into `app/build.gradle`.
3. Writes that release's notes to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

CI only checks that these files are up to date (`update_version.py --check`), runs the unit tests, builds the signed release APK and publishes the GitHub release.

---

## 📥 Releases & Download

- **GitHub Releases**: Download pre-built APKs from the [Releases](https://github.com/rootminusone8004/pynb/releases) page.
