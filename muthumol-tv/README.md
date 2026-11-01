> **TG stream 6.0-beta4 / versionCode 63:** adaptive phone/TV library, app rename, Android target 35 and updater access from the login-unavailable screen. See [current status](CURRENT_RELEASE_STATUS.md). Older sections below are historical; login-enabled release publication remains blocked by missing publisher credentials and GitHub API policy.

> **6.0-beta3 / versionCode 62:** show → season → episode browsing and phone/QR-only login UI are implemented. See [current verification](CURRENT_RELEASE_STATUS.md). Signed diagnostic build passed host checks; Telegram build credentials and secure GitHub signing persistence remain blocked. Do not treat it as a functional published login release.

> **2026-11-01 UI revision:** See [current verification](UI_REVISION_REPORT.md) and [TMPlayer comparison](FEATURE_COMPARISON.md). This revision is built unsigned; the signing and test claims below describe the supplied beta1 archive, not this revision. Android version identifiers are now 61 / 6.0-beta2; no updater release has been published.

# Muthumol TV 6.0 Beta 1

An Android TV-focused update of the user's TG stream 5.6 source. This is a feature expansion beta, not a complete implementation of every TMPlayer feature discussed in the conversation.

## Included

- Cinematic, sharper poster backdrop with crossfade and slow pan/zoom; animated glass panels, card focus and page entry. Reduced-motion preference.
- Remote navigation for search, collections, downloads and settings. Long-press movie actions and video details.
- Loaded library expanded from 10 to 120 recent videos. Movies/series, Continue, Watch later, Watched and Favourites filters; word-based search, sorting and system voice-search intent where supported.
- Clear Dialogue, Clear Dialogue Strong and Night audio profiles: centre-aware surround-to-stereo mixing, gentle presence EQ, slow level compensation, linked-channel compression and a soft peak ceiling before PCM16 quantisation. Original mode retains the old linear boost path. Processing is not AI source separation or a guarantee of intelligibility for every soundtrack.
- Audio-track selection, 100–300% boost and TV-speaker versus multichannel PCM choice.
- Playback speed 0.25–4x with fine numeric input and remembered speed, optional silence skipping.
- Episode list and next-episode countdown with cancel, loaded-neighbour ordering, autoplay preference.
- Exact-time seek input, undo of a recent large seek, timestamp bookmarks with notes/delete, A–B repeat, sleep timer and hold-OK remote unlock.
- Chapter menu when ID3 chapter metadata is exposed by Media3; seek-based paused frame navigation. These are not a comprehensive MKV/MP4 chapter parser or guaranteed decoded-frame stepping.
- Original aspect presets plus 21:9 and 1:1, supported-device PiP, live speed/buffer/details.
- Permanent offline download queue with space checks, cancel/retry/delete, offline playback and read-only external-player handoff for completed permitted downloads. Queue operates while the library is visible, not as a background download service. Streaming takes priority.
- SAF settings/history backup and explicit restore confirmation, including preference-type preservation. Telegram login credentials, database and API keys are excluded.
- On-device QR login from TDLib's auth link; phone-number login remains.
- Existing updater and diagnostics retained. No Cast SDK or Cast UI added.
- Existing subtitle handling preserved: Telegram matching/import, track choice, delay, style and position.

## Compatibility and honest limits

- Package: `com.muthumol.player.dummy`; versionCode 60; versionName 6.0-beta1; minimum API 25; target API 28.
- Native libraries remain ARMv7, from the original 5.6 source. Requires a TV capable of running 32-bit ARM apps. ARM64-only devices are not supported by this APK.
- Same existing signing certificate is used. No keystore or credentials are included in this source archive.
- Physical-TV startup, network streaming, audible dialogue quality, native audio decoding, HDMI output, QR authentication, voice availability and PiP have not been tested against the user's TV/account.
- Still outstanding from the requested full TMPlayer comparison: split-byte-file playback, 3D transforms, intro/credits timing providers, full metadata/ratings/correction integration, multi-device resume sync, parental/PIN mode, Android TV launcher recommendations, full MP4/MKV chapter parsing, true frame stepping, native ARM64 builds and full feature/device parity.
- Existing EAC3 timestamp concern has not been proven fixed. Network buffering and hardware codec limits remain possible.
- New playback tools are isolated from subtitle methods, but integration on the TV still needs confirmation.

## Build

Use JDK 17, Android SDK platform 35 / build tools 35.0.0, Gradle 8.9, AGP 8.7.3. Set `sdk.dir` in an uncommitted `local.properties`, and `MUTHUMOL_SIGNING_KEY` to the user's existing signing keystore. The source never generates a replacement signing identity.

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The four original native decoder smoke tests require a compatible Android/ARM runtime and are skipped on this x86 host. See BUILD_VERIFICATION.json and TEST_REPORT.md for the actual build's verification results.
