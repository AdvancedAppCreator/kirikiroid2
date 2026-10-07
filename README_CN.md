# KrKr2

KrKr2 是一个用于运行 KiriKiri 系列引擎软件的跨平台播放器。本仓库是
**AdvancedAppCreator 社区维护分支**，不是 KiriKiri、Kirikiri Z 或
Kirikiroid2 的官方版本；这些上游项目不维护、也不支持本分支。

[English](README.md)

## 来源

本项目源自 Kirikiroid2；Kirikiroid2 将 KiriKiri/Kirikiri Z（T Visual
Presenter）运行时移植到 cocos2d-x 和 Android。原版权及贡献者声明保留在
[LICENSE](LICENSE)。

本地历史从提交 `3d55f5c79a85c0282b68f8a4f1354449b358b7ed`
（2024-12-10）开始。现有历史能够精确验证的最后一个上游版本是
`22bcd0c509fa370dce8bd0b4c664a4878cf5cf32`，它在合并提交
`b5c9937b51d3f3b1034693e1a58573ac3cd72349` 中被记录为
`upstream/dev`。提交对象没有保存当时的远程 URL，因此本仓库不会在缺乏证据时
声称更具体的对应关系。

本分支修改了应用身份、Android 外部启动接口、翻译/OCR、平台和构建支持、插件及
发布配置。请只在
<https://github.com/AdvancedAppCreator/krkr2/issues> 报告本分支问题；
安全问题请参阅 [SECURITY.md](SECURITY.md)。

## 支持目标及构建

- Android：API 29+，arm64-v8a、x86_64
- Windows：Visual Studio 2022，x86_64
- Linux：GCC，x86_64
- macOS：Xcode，arm64（尽力维护）

所有平台都需要 CMake 3.31.1+、Ninja、Python 3、NASM，以及位于
`aa40adda5352e87655b8583cfb2451d5e9e276fd` 基线的 vcpkg。请设置
`VCPKG_ROOT`。

Android 使用 JDK 17、Android SDK 34 和 NDK `28.0.13004108`。设置
`ANDROID_SDK_ROOT`（或 `ANDROID_HOME`）及 `ANDROID_NDK`（或
`ANDROID_NDK_HOME`），然后执行：

```bash
./platforms/android/gradlew -p ./platforms/android assembleDebug
```

旧文档中的 SDK 33 已不适用。公共 CI 只生成调试 APK。发布签名由发布者在仓库
外管理；如需自行构建发布版，请创建已忽略的
`platforms/android/app/sign.keystore` 和 `sign.properties`，后者包含
`SIGN_STORE_PASS`、`SIGN_KEY_ALIAS`、`SIGN_KEY_PASS`。切勿提交签名材料。

Windows 请在 “x64 Native Tools for VS 2022” 中将 winflexbison 2.5.25
加入 `PATH`，设置 `VCPKG_ROOT`，然后运行：

```bat
scripts\build-windows.bat
```

Linux 依赖列表见 `.github/workflows/build-linux.yml`，构建命令为
`scripts/build-linux.sh`。macOS 使用 `MacOS Debug Config` 和
`MacOS Debug Build` 预设。

## 隐私与许可证

详见 [PRIVACY.md](PRIVACY.md)。平台崩溃处理可能在本地创建转储，但本分支不会
传输转储；Android 与 Windows 原有的第三方明文上传路径已删除。

AdvancedAppCreator 应用程序整体作为 **GPL-2.0-or-later 组合程序**发布。
KiriKiri/Kirikiri Z 代码保留其宽松许可证声明，依赖和资源保留各自兼容的许可证。
分发者必须满足所有适用条款，包括 GPL 的对应源代码义务。请阅读
[COPYING](COPYING)、[LICENSE](LICENSE)、[LICENSES](LICENSES) 及
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
