# Device Name Change

[English](README.md)

适用于 HyperOS 设置的 Xposed 模块，解除设备名称的字数限制，跳过云端名称校验，并阻止开启时重置设备名称。

## 功能说明

设置应用把设备名称限制在 32 个字符以内（`MiuiDeviceNameEditFragment$LengthTextWatcher`），并会通过云端合规校验拒绝自定义名称。本模块作用于 `com.android.settings` 进程：

- 解除设备名称的字数限制
- 跳过云端名称校验，名称直接写入
- 阻止设备开启时把设备名称重置为默认值

每个功能都有独立开关，未同意用户协议与隐私政策前不会生效。

## 适用版本

- Android 10 - 16（minSdk 29，targetSdk 36）
- HyperOS 设置（`com.android.settings`）
- 支持现代 Xposed API 102 的 LSPosed

## 安装

1. 安装 APK。
2. 在 LSPosed 中启用本模块，作用域勾选「设置」（`com.android.settings`）。
3. 打开本应用，阅读并同意用户协议与隐私政策，按需开启功能开关。
4. 若开关未立即生效，用「快捷跳转」里的「重启设置」重启设置进程（需要 root）。

## 下载

见 Releases 页面。

## 构建

```
cd app
tools/build.sh
```

需要 JDK 17、Android SDK build-tools 与 `android.jar`。把 `libxposed-api-102.jar`（`io.github.libxposed:api:102.0.0`）和 `libxposed-service-102.jar`（`io.github.libxposed:service:102.0.0`）放到 `app/tools/`。仓库不含签名密钥。

## 说明

- 蓝牙名称与热点名称由系统自行设置，本模块不做修改。
- 「重启设置」需要 root，执行的是 `am force-stop com.android.settings`。

## 致谢

- [libxposed/api](https://github.com/libxposed/api) — 现代 Xposed 模块 API
- [LSPosed](https://github.com/LSPosed/LSPosed)

## 许可

MIT
