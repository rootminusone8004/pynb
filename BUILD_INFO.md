# Build Information

## Project Information
  - **Project Name:** pynb
  - **Package Name:** com.pynb.app

## Build Environment
  - **Android Studio / IntelliJ IDEA (Community Edition):** Ladybug / Iguana or later (IDEA 2024.3+)
  - **Gradle Version:** 8.12
  - **Java Version:** Java 17 (Source & Target compatibility), JDK 17 / 21 (Build)

## SDK and Tools
  - **compileSdkVersion:** 34
  - **buildToolsVersion:** 34
  - **minSdkVersion:** 24
  - **targetSdkVersion:** 34

## Gradle
  - **AGP Version:** 8.7.3

## Dependencies
  - `androidx.appcompat:appcompat:1.7.0`
  - `com.google.android.material:material:1.12.0`
  - `androidx.activity:activity:1.9.0`
  - `androidx.constraintlayout:constraintlayout:2.1.4`
  - `androidx.recyclerview:recyclerview:1.3.2`
  - `androidx.cardview:cardview:1.0.0`
  - `androidx.documentfile:documentfile:1.0.1`
  - `com.google.code.gson:gson:2.10.1`
  - `io.noties.markwon:core:4.6.2`
  - `io.noties.markwon:ext-tables:4.6.2`
  - `io.noties.markwon:html:4.6.2`

## Optimization & Shrinking
  - **R8 Code Shrinking:** Enabled (`minifyEnabled true`)
  - **Resource Shrinking:** Enabled (`shrinkResources true`)
  - **ProGuard File:** `proguard-android-optimize.txt` + `proguard-rules.pro`
  - **Release APK Size:** ~2.1 MB (reduced from > 7 MB)

## Build Status
  - **Success:** Yes
