# TG stream 6.0-beta5 / versionCode 64

Home now uses TMPlayer's default dark colour roles: #0C0E10 background, #15181B surfaces, #79D0F5 accent, #F2F4F5 text and #AEB5BB secondary text. A TV navigation rail and artwork hero lead into Recently added, Continue watching and per-chat poster shelves. Phone navigation scrolls horizontally with 48dp touch targets. Existing promotional poster preference, phone compatibility, package ID and subtitle/player implementation are preserved.

TelegramLibrary searches account-wide videos and video documents with independent TDLib cursors. A null chatList includes the archive. Chats loads both main and archive lists in pages and retains groups, people and Saved Messages as well as channels. Each chat and All movies have a paginated poster grid. Series still open seasons then episodes; Back returns to the originating grid. Requests use a background worker, discard obsolete query results and preserve page cursors after failures. Loading does not download movie files. Refresh and Load more are explicit; no timer fetches or reorders remote results.

This is a native View implementation informed by TMPlayer's source at 268b02d9ca8835385802037ae5241d142fa0c066; full pixel equality or complete feature parity is not claimed. Provider metadata, title/version matching across different encodes, a rotating featured carousel, folder filters and starred-chat management remain follow-up work. Movies in the same file identity are deduplicated; different encodes remain separate. Initial Home loads one page per media filter, not the entire account. Further pages are available with Load more.

## Verification

See TMPLAYER_HOME_VERIFICATION.json for current automated results. Host rendering covers TV 960x540, compact TV 960x405 and phone 412x915. Repository tests exercise account-wide video/document fetching, independent cursors, error retries, stale query results, archive and non-channel chats, and series navigation from a chat grid. Physical Telegram account login, streaming, audio, HDMI and remote responsiveness have not been tested on a device. The four ARM-native decoder tests remain skipped on the x86 host. The packaged native binaries remain ARMv7 only.

## Release status

Original V5.6 signing key recovered from private Drive TelePlay Projector App/build-signing/debug.keystore. Its PrivateKeyEntry certificate matches V5.6 exactly (26e7b63fcdd306fe3f52a0172a3180160c0aee359a6c00f933ded41e8c0b8318). Keep alias androiddebugkey and the original identity; no uninstall migration. The old replacement-key setup is obsolete.

The user has supplied TG_API_ID and TG_API_HASH in Repository secrets; CI confirmed the configured values pass format validation. CI run 41335165595 is preparing a configured unsigned APK with tests and release lint. Protected local signing with the original key and verified signed feed publication are pending. Source publication and unsigned preparation do not mean Update now is available. See RELEASE_UPDATES.md.
