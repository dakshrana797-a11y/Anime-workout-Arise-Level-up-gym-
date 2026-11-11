ARISE: HUNTER SYSTEM — ANDROID STUDIO PROJECT

This is a native Android app project (Java + Android WebView) that opens the Hunter System inside its own Android app window. It does not open Chrome.

IMPORTANT: This ZIP is Android source code, not an already-compiled APK. This environment does not have the Android SDK/Gradle build toolchain needed to compile and sign an APK.

BUILD:
1. Open this folder in Android Studio on a computer.
2. Let Android Studio sync/download the Android Gradle plugin and SDK components.
3. Choose Build > Build Bundle(s) / APK(s) > Build APK(s).
4. Android Studio will generate an installable debug APK under app/build/outputs/apk/debug/.
5. Transfer that APK to the Android phone and install it. Only install APKs you built or trust.

The app bundles index.html locally, so the Hunter System does not need Chrome or an internet connection once installed.
No dangerous permissions are requested.
