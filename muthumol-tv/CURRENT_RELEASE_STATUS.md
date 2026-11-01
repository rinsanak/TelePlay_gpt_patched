# TG stream 6.0-beta5 / versionCode 64

Home now uses TMPlayer's default dark colour roles: #0C0E10 background, #15181B surfaces, #79D0F5 accent, #F2F4F5 text and #AEB5BB secondary text. A TV navigation rail and artwork hero lead into Recently added, Continue watching and per-chat poster shelves. Phone navigation scrolls horizontally with 48dp touch targets. Existing promotional poster preference, phone compatibility, package ID and subtitle/player implementation are preserved.

TelegramLibrary searches account-wide videos and video documents with independent TDLib cursors. A null chatList includes the archive. Chats loads both main and archive lists in pages and retains groups, people and Saved Messages as well as channels. Each chat and All movies have a paginated poster grid. Series still open seasons then episodes; Back returns to the originating grid. Requests use a background worker, discard obsolete query results and preserve page cursors after failures. Loading does not download movie files. Refresh and Load more are explicit; no timer fetches or reorders remote results.

This is a native View implementation informed by TMPlayer's source at 268b02d9ca8835385802037ae5241d142fa0c066; full pixel equality or complete feature parity is not claimed. Provider metadata, title/version matching across different encodes, a rotating featured carousel, folder filters and starred-chat management remain follow-up work. Movies in the same file identity are deduplicated; different encodes remain separate. Initial Home loads one page per media filter, not the entire account. Further pages are available with Load more.

## Verification

See TMPLAYER_HOME_VERIFICATION.json for current automated results. Host rendering covers TV 960x540, compact TV 960x405 and phone 412x915. Repository tests exercise account-wide video/document fetching, independent cursors, error retries, stale query results, archive and non-channel chats, and series navigation from a chat grid. Physical Telegram account login, streaming, audio, HDMI and remote responsiveness have not been tested on a device. The four ARM-native decoder tests remain skipped on the x86 host. The packaged native binaries remain ARMv7 only.

## Release status

The live updater feed was still V5.6/code 59 when checked. The existing release workflow failed while attempting to install the obsolete Android SDK package `tools`; this revision requests only `platform-tools`, pins the runner to Ubuntu 24.04 and validates tests/lint/unsigned assembly before checking signing prerequisites. Signed publication still uses the existing verifier and atomically commits APK, source and manifest only after validating credentials and the pinned signing certificate.

Actions logs for run 41329290938 showed empty TG_API_ID, TG_API_HASH and signing-password environment bindings. The protected persistent key from the earlier environment is not present in this workspace. No new key was generated and no unconfigured/unsigned APK was published as a working update. Configure the existing signing identity and Telegram application credentials through Actions secrets; then run the release workflow. Check the actual workflow result and live latest.json before claiming Update now is available.

The previously authorized persistent certificate is 70db42e5a1ee6f2fe4e9b5b63ac6a90360e89466d9a1bb1ee5147a57d0316460. Migration from the old V5.6 identity requires one reinstall; subsequent releases must retain the persistent key. See RELEASE_UPDATES.md for secure setup.
