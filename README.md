# KrKr2

KrKr2 is an independently maintained, cross-platform player for software made
with the KiriKiri family of engines. This is the
**AdvancedAppCreator community fork**. It is not an official KiriKiri,
Kirikiri Z, or Kirikiroid2 release, and those projects do not maintain or
support this fork.

[中文](README_CN.md)

## Provenance

This repository descends from Kirikiroid2, which ports the KiriKiri/Kirikiri Z
(T Visual Presenter) runtime to cocos2d-x and Android. KiriKiri and Kirikiri Z
copyright and contributor notices are retained in [LICENSE](LICENSE).

The imported Git history begins at local commit
`3d55f5c79a85c0282b68f8a4f1354449b358b7ed` (2024-12-10). The latest upstream
revision that the retained local history identifies exactly is commit
`22bcd0c509fa370dce8bd0b4c664a4878cf5cf32`, merged as
`b5c9937b51d3f3b1034693e1a58573ac3cd72349` with the recorded source
`upstream/dev`. The former remote URL is not recorded in the commit object, so
this repository does not claim a more specific source mapping without
evidence.

This fork changes the application identity, Android launch integration,
translation/OCR features, platform/build support, plugins, and publication
configuration. Report fork issues only at
<https://github.com/AdvancedAppCreator/kirikiroid2/issues>. See [SECURITY.md](SECURITY.md)
for private vulnerability reports.

## Adult Game Manager integration

[Adult Game Manager](https://github.com/AdvancedAppCreator/adult-game-manager)
can track and launch supported KiriKiri games through this community fork while
keeping its own local library and catalog state.

- [Unified launcher setup](https://advancedappcreator.github.io/adult-game-manager-releases/launcher-setup/)
- [Latest AGM release](https://github.com/AdvancedAppCreator/adult-game-manager/releases/latest)
- [Latest Kirikiroid2 Community Fork release](https://github.com/AdvancedAppCreator/kirikiroid2/releases/latest)

AGM and this fork are separate applications with separate releases, licenses,
privacy disclosures, and support boundaries.

## Supported targets

- Android: API 29+, arm64-v8a and x86_64
- Windows: x86_64 with Visual Studio 2022
- Linux: x86_64 with GCC
- macOS: arm64 with Xcode (maintained on a best-effort basis)

## Build prerequisites

All platforms require CMake 3.31.1+, Ninja, Python 3, NASM, and a vcpkg
checkout at baseline `aa40adda5352e87655b8583cfb2451d5e9e276fd` (the baseline
in `vcpkg-configuration.json`). Set `VCPKG_ROOT` to that checkout.

### Android

Install JDK 17, Android SDK platform/build tools 34, and Android NDK
`28.0.13004108`. Set `ANDROID_SDK_ROOT` (or `ANDROID_HOME`) and either
`ANDROID_NDK` or `ANDROID_NDK_HOME`.

```bash
./platforms/android/gradlew -p ./platforms/android assembleDebug
```

The project compiles and targets SDK 34; older documentation referring to SDK
33 is obsolete. Output is under
`platforms/android/out/android/app/outputs/apk/`.

Android APK redistribution must include the generated third-party-license and
FFmpeg source/relinking artifacts described in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The public Android workflow
generates and uploads them beside its APKs.

Release signing is deliberately external. The public workflow produces only
debug APKs. For your own release, create ignored
`platforms/android/app/sign.keystore` and `sign.properties` containing
`SIGN_STORE_PASS`, `SIGN_KEY_ALIAS`, and `SIGN_KEY_PASS`; never commit either
file. AdvancedAppCreator release keys are not distributed with the source.

### Windows

Use an x64 Native Tools for VS 2022 prompt, put winflexbison 2.5.25 on `PATH`,
set `VCPKG_ROOT`, then run:

```bat
scripts\build-windows.bat
```

The debug executable is `out\windows\debug\bin\krkr2\krkr2.exe`.

### Linux / macOS

On Linux install the development packages listed in
`.github/workflows/build-linux.yml`, then run `scripts/build-linux.sh`.
On macOS:

```bash
cmake --preset="MacOS Debug Config" -DENABLE_TESTS=OFF -DBUILD_TOOLS=OFF
cmake --build --preset="MacOS Debug Build"
```

## Privacy and crash handling

See [PRIVACY.md](PRIVACY.md). Crash dumps may be created locally by platform
crash handling, but this fork does not transmit them. The former plaintext
third-party dump uploader has been removed on Android and Windows.

## Licenses

The AdvancedAppCreator application is distributed as a
**GPL-2.0-or-later combined work**. KiriKiri/Kirikiri Z code retains its
permissive notices, and dependencies/assets retain their own compatible
licenses. Source and binary distributions must satisfy every applicable
license, including the GPL corresponding-source obligations.
See [COPYING](COPYING), [LICENSE](LICENSE), [LICENSES](LICENSES), and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
