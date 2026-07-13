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
2. Put the release keystore into `TMessagesProj/config` if building release APKs
   and set `RELEASE_STORE_FILE=../TMessagesProj/config/konkegram-release.keystore`
   in your local, uncommitted Gradle properties.
3. Fill `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_STORE_PASSWORD`
   in `gradle.properties`.
4. Put the current `google-services.json` into the app modules used by the build.
5. Verify `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`
   contains your API credentials.
6. Build the required flavor from Android Studio or Gradle.

### GitHub Release Builds

The `Android Release` workflow builds signed `afatRelease` APK and AAB files.
Manual runs upload both files as workflow artifacts. Pushing a tag matching
`v*` also creates a GitHub Release and attaches the APK, AAB and SHA-256 file.

Configure these repository secrets under `Settings -> Secrets and variables ->
Actions` before running the workflow:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `GOOGLE_SERVICES_JSON_BASE64`

On Windows, create the two Base64 values with PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("TMessagesProj/config/konkegram-release.keystore")) | Set-Clipboard
[Convert]::ToBase64String([IO.File]::ReadAllBytes("TMessagesProj/google-services.json")) | Set-Clipboard
```

To build without publishing, open `Actions -> Android Release -> Run workflow`.
To publish a release, first update `APP_VERSION_CODE` and `APP_VERSION_NAME` in
`gradle.properties`, commit the change, then push a matching version tag:

```shell
git tag v12.8.1
git push origin v12.8.1
```

Keep the release keystore and its credentials backed up. Android updates must
always be signed with the same key.

Do not publish with the upstream `TMessagesProj/config/release.keystore` stored
in this repository: its key is already public through Git history. Generate a
new private keystore before the first Konkegram release, store it only in a safe
backup and in GitHub Secrets, and never commit it to the repository.

### Localization

Konkegram keeps local string resources in the repository for branding-sensitive
UI. Do not rely on upstream cloud language strings for branded screens.
