## Konkegram for Android

Konkegram is an unofficial Android messenger fork based on Telegram for Android.
The app is branded as Konkegram / Konkegram Beta and uses the application id
`org.konkegram.messenger`.

### Fork Changes

- App name: `Konkegram`
- Beta app name: `Konkegram Beta`
- Android application id: `org.konkegram.messenger`
- Telegram API application: `Konkegram`
- Proxy settings include a user-facing `Try connection through IPv6` option.
- Cloud language strings are disabled so local Konkegram branding is used in UI.

### Upstream

This repository is based on Telegram for Android:

- Upstream source: https://github.com/DrKLO/Telegram
- Telegram API docs: https://core.telegram.org/api
- MTProto docs: https://core.telegram.org/mtproto
- API application registration: https://my.telegram.org/

Konkegram is not an official Telegram application.

### Build Notes

Before publishing APKs, use your own signing keys, Firebase configuration and
Telegram API credentials.

Firebase / Google Services packages expected by this fork:

- `org.konkegram.messenger`
- `org.konkegram.messenger.beta`
- `org.konkegram.messenger.web`

The library module may still contain technical `org.telegram.*` Java package
names inherited from upstream. These are implementation package names, not the
installed Android application id.

### Compilation

1. Open the project in Android Studio.
2. Put the release keystore into `TMessagesProj/config` if building release APKs.
3. Fill `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_STORE_PASSWORD`
   in `gradle.properties`.
4. Put the current `google-services.json` into the app modules used by the build.
5. Verify `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`
   contains your API credentials.
6. Build the required flavor from Android Studio or Gradle.

### Localization

Konkegram keeps local string resources in the repository for branding-sensitive
UI. Do not rely on upstream cloud language strings for branded screens.
