# Device Name Change

An Xposed module for HyperOS Settings that removes the length limits on the device name, Bluetooth name and hotspot name.

## Introduction

The Settings app limits these names by byte length and rejects custom names through a Xiaomi cloud compliance check. This module runs inside `com.android.settings` and lifts those limits.

## Supported Versions

- Android 10 - 16 (minSdk 29, targetSdk 36)
- HyperOS Settings (`com.android.settings`)
- LSPosed (legacy Xposed API)

## Install

1. Install the APK.
2. Enable the module in LSPosed and pick scope `设置` (`com.android.settings`).
3. Open Device Name Change, accept the user agreement, then enable the switches you need.

## Build

```
cd app
tools/build.sh
```

Needs JDK 17, Android SDK build-tools and a platform `android.jar`. The signing key is not included in this repository.

## License

MIT
