# Konkegram release downloads

Konkegram releases use their own semantic version, starting with `1.0.0`.
The Telegram for Android version used as the source base is tracked separately
and does not determine the public Konkegram version.

## APK variants

| Download | Device support | Android version |
| --- | --- | --- |
| `konkegram-v1.0.3-release-universal.apk` | ARM64, ARMv7, x86 and x86_64 | Android 5.0 or newer |
| `konkegram-v1.0.3-release-arm64-v8a.apk` | Most modern 64-bit ARM phones and tablets | Android 5.0 or newer |
| `konkegram-v1.0.3-release-armeabi-v7a.apk` | Older 32-bit ARM devices | Android 5.0 or newer |

The universal APK is recommended when the device architecture is unknown. The
architecture-specific APKs contain the same features and can reduce download
and installed size.

Advanced users can check the primary device ABI with:

```shell
adb shell getprop ro.product.cpu.abi
```

Updates must be signed with the same release key as the installed application.
Android will reject an APK signed with a different key.
