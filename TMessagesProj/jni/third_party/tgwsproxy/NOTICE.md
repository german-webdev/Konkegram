# TG WS Proxy Android

This directory contains source code and Android native libraries derived from
`amurcanov/tg-ws-proxy-android` at commit
`24ee8dc51ab7b4de92df5849cccba56c73b132b6`.

Upstream: https://github.com/amurcanov/tg-ws-proxy-android

The component is distributed under GNU GPL version 3. See `LICENSE` in this
directory. The original `tg-ws-proxy` project by Flowseal is MIT-licensed.

The prebuilt `libtgwsproxy.so` files are generated from the accompanying Rust
source. Konkegram enables TLS certificate verification rather than using the
upstream permissive verifier. It also skips community relay-domain
initialization and refresh while Cloudflare fallback is disabled. Direct WSS
routing covers all five production Telegram DCs while startup prewarming stays
limited to the common DC2/DC4 routes.
