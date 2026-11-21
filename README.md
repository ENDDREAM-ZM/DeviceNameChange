# Device Name Change

[中文说明](README.zh-CN.md)

An Xposed module for HyperOS Settings that removes the length limit on the device name, skips the cloud name check, and blocks the name being reset at boot.

## Introduction

The Settings app limits the device name to 32 characters (`MiuiDeviceNameEditFragment$LengthTextWatcher`) and rejects custom names through a cloud compliance check. This module runs inside `com.android.settings` and:

- removes the device name length limit
- skips the cloud name check, so any name is written directly
- blocks the device name from being reset to the default when the device boots

Every feature is behind its own switch. Nothing is applied until the user accepts the user agreement and privacy policy.

## Supported Versions

- Android 10 - 16 (minSdk 29, targetSdk 36)
- HyperOS Settings (`com.android.settings`)
- LSPosed with modern Xposed API 102 support

## Install

1. Install the APK.
2. Enable the module in LSPosed and pick scope `设置` (`com.android.settings`).
3. Open Device Name Change, read and accept the user agreement and privacy policy, then enable the switches you need.
4. If a switch does not take effect immediately, use `重启设置` in `快捷跳转` to restart the Settings process (needs root).

## Download

See the Releases page.

## Build

```
cd app
tools/build.sh
```

`tools/build.sh` needs JDK 17, Android SDK build-tools and a platform `android.jar`. Put `libxposed-api-102.jar` (`io.github.libxposed:api:102.0.0`) and `libxposed-service-102.jar` (`io.github.libxposed:service:102.0.0`) at `app/tools/`. The signing key is not included in this repository.

## Notes

- `重启设置` needs root: it runs `am force-stop com.android.settings`.

## Credits

- [libxposed/api](https://github.com/libxposed/api) - modern Xposed module API
- [LSPosed](https://github.com/LSPosed/LSPosed)

## License

MIT
