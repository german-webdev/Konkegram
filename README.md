# Konkegram for Android

Konkegram is an unofficial Android messenger based on the open-source Telegram
for Android client.

## Highlights

- Konkegram branding for the main and beta applications.
- Optional IPv6-only connection mode in proxy settings.
- Compatibility with Telegram accounts, chats and services.

## Downloads

Signed builds are available on the
[GitHub Releases](https://github.com/german-webdev/Konkegram/releases) page.

## Building

Open the project in Android Studio and use the Android SDK, NDK and CMake
versions declared by the project. Configure your own Telegram API, Firebase and
signing credentials outside version control before building a distributable
application.

Signing keys, passwords, service-account credentials and other private material
must be kept outside version control. Maintainer release procedures are not
documented in this public README.

## Upstream

Konkegram is based on [Telegram for Android](https://github.com/DrKLO/Telegram).
Documentation for the Telegram API and MTProto protocol is available at:

- [Telegram API](https://core.telegram.org/api)
- [MTProto](https://core.telegram.org/mtproto)

Konkegram is an independent project and is not affiliated with or endorsed by
Telegram.

## License

The source code is distributed under the terms of the
[GNU General Public License v2.0](LICENSE). Third-party components may be
covered by their respective licenses.
