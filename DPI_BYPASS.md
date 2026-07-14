# DPI Bypass transport

Konkegram can route Telegram connections through an in-process WebSocket
bridge. The option is available in **Settings > Data and Storage > Proxy
Settings > DPI Bypass**.

## Data path

```text
tgnet -> 127.0.0.1 MTProto proxy -> official Telegram TLS WebSocket -> Telegram DC
                                      \-> optional Cloudflare fallback
```

The local listener accepts Telegram's obfuscated MTProto transport, extracts
the destination data-center identifier and forwards the encrypted MTProto
stream through a WebSocket. Official Telegram WebSocket endpoints are always
tried first.

DPI Bypass and its Cloudflare fallback are enabled by default when no explicit
user choice has been saved. The fallback has a separate switch below DPI
Bypass. When enabled, it uses the relay-domain list bundled with the TG WS
Proxy component and may refresh that list from its upstream GitHub repository.
When disabled, Konkegram neither initializes nor refreshes community relay
domains. A relay does not receive message plaintext, but it can observe
connection metadata such as the client IP, timing and traffic volume.

TLS certificates are verified against the bundled Mozilla root store;
Konkegram does not use the permissive certificate verifier from the upstream
Android application.

The DPI Bypass and user-configured proxy routes are mutually exclusive because
tgnet can use only one proxy transport for a connection. The IPv6 preference is
independent. Disabling DPI Bypass returns tgnet to the normal direct route; a
saved user proxy can then be enabled again from the same screen.

When Android changes the active network, Konkegram restarts the embedded bridge
after a short debounce. This closes WebSockets tied to the previous network,
clears runtime DNS state and makes tgnet reconnect through the new network.

## Diagnostics

The native bridge logs under the `TgWsProxy` Android log tag in debug builds.
Its summary reports active connections, WebSocket and fallback counts, errors,
pool hits and transferred bytes.

## Third-party source

The embedded Rust source is derived from
[`amurcanov/tg-ws-proxy-android`](https://github.com/amurcanov/tg-ws-proxy-android)
at commit `24ee8dc51ab7b4de92df5849cccba56c73b132b6`. See
`TMessagesProj/jni/third_party/tgwsproxy/NOTICE.md` and `LICENSE` for details.
