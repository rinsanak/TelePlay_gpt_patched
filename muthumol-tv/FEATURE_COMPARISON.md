# Muthumol TV: comparison and UI revision

Compared on 2026-11-01 with TMPlayer commit
`268b02d9ca8835385802037ae5241d142fa0c066`.
This is a source comparison, not a claim that every reference feature was device-tested.
The historical conversation previews were available; a tool to open their full threads was not.

## Current beta5 additions

See CURRENT_RELEASE_STATUS.md and CHANGELOG_V6.0_beta5.md. Beta5 adds account-wide video/document search with archive inclusion, paginated chats and poster grids, multiple Home shelves and the reference dark colour roles. The comparison below records the earlier beta2 revision and is historical. Full TMPlayer parity is not claimed.

## Earlier beta2 revision

- Home navigation now labels Search, Collections, Downloads and Settings.
- All, Continue, Movies, Series, Watch later and Favourites are directly accessible on Home.
- The selected collection has a visible state and explicit D-pad routes.
- The hero uses a consistent cinema palette, a clear primary action and a separate More options action.
- Empty collections explain how to populate them, stop offering unrelated playback and provide Show all.
- Local search accepts one insertion, deletion, substitution or transposition in words of four or more characters. Exact matches rank ahead of approximate ones with the default sort. Every query word must match; this deliberately avoids silently ignoring an unrelated word. Matching input is bounded to control work on a low-power TV.
- Compact 960x405 and standard 960x540 layouts are rendered and checked for clipped controls.
- Stronger backdrop shading improves contrast behind the library. Motion preference is respected by Home button transitions.
- Fixed an existing shelf invalidation edge case: removing the last result must clear the previous cards.

No TMPlayer implementation code was copied. The new UI and matcher use this application's existing Android View architecture.

## Feature gaps, excluding subtitles and Cast

| Capability | Uploaded 6.0 beta1 / current revision | TMPlayer reference evidence |
| --- | --- | --- |
| Progressive Telegram streaming, seeking, audio-track selection | Already present; playback code unchanged | `docs/FEATURES.md`, Android player |
| QR and phone login | Already present; unchanged | `docs/FEATURES.md` |
| Episode order, next episode countdown | Already present for loaded neighbours | `docs/FEATURES.md` |
| Collections and resume | Already present locally; now exposed directly on Home | `docs/FEATURES.md`, `ContinueCard.kt` |
| Typo-tolerant title search | Added locally; no cross-chat search or permissive wrong-keyword ranking | `docs/FEATURES.md` search section |
| Multi-chat Home, starred chats, Telegram folders, large-account paging | Missing. Current channel selector and recent-video window are bounded, not full large-library browsing | `docs/FEATURES.md`, chat/home UI and scale tests |
| Metadata, synopsis, ratings, provider matching/correction | Partial: existing Wikipedia/TVmaze artwork. Rich TMDB/TVmaze/AniList metadata and correction are missing | `OnlineMetadata.kt`, `MetadataStore.kt`, `OnlineMetadataSettings.kt` |
| Intro/credits skip timing | Missing | `SkipTimes.kt`, `Skip.kt`, `SkipPill.kt` |
| Split byte-file playback | Missing | `SplitPieces.kt`, `StorageSplit.kt` |
| 3D stereo transforms | Missing | `Stereo3d.kt`, `Stereo3dEffect.kt` |
| Cross-device resume sync | Missing; existing backup is manual | `ResumeSync.kt`, `ResumeSyncDoc.kt` |
| App PIN and kids lock | Missing; remote-control lock is not parental control | `AppLock.kt`, `KidsLock.kt`, `PinPad.kt` |
| TV launcher recommendations | Missing | TV home row described in `docs/FEATURES.md` setup tour |
| Full MP4/MKV chapters | Partial: existing ID3 chapter handling only | `Mp4Chapters.kt`, `MkvChapters.kt` |
| Accurate decoded-frame stepping | Existing seek-based approximation; not decoded-frame stepping | Reference player/frame navigation |
| Permanent downloads | Existing local queue, not a background service. Full storage destinations and bulk workflows remain limited | `docs/FEATURES.md`, storage/download implementations |
| ARM64-only devices | Unsupported by the supplied native binaries; ARMv7 only | Reference Android build/native packaging |
| Dedicated mobile UI, desktop apps and web app | Not provided by this uploaded TV app | `docs/FEATURES.md`, `README.md` |
| Guided setup/tour, UI size/themes and low-storage options | Partial: existing reduced motion and cache tools, no equivalent guided setup or adaptive UI size | `docs/FEATURES.md` setup section |

Subtitle handling is intentionally unchanged, as requested. Cast is intentionally excluded.
UI polish does not establish parity with the missing capabilities above.

## Why Check for update did not show 6.0

The source reads `rinsanak/TelePlay_gpt_patched`, branch `tg-stream-updates`, file `latest.json`.
The live response checked during this task advertises versionCode **59**, versionName **5.6**,
and `apks/TG_stream_V5.6.apk`. The uploaded source uses versionCode **60** / **6.0-beta1**.
A source ZIP or a file in a chat does not publish an updater release. No update feed or repository was changed in this task.

## Recommended next substantial work

1. Build a paged multi-channel library and search without eager loading an account or moving focused items during background refresh.
2. Add opt-in metadata matching, correction and full details with clear provider attribution.
3. Rebuild and validate the complete native stack for ARM64; retain ARMv7 compatibility.
4. Add PIN/kids mode, TV launcher integration and intro/credits providers with focused tests.
5. Treat split-file playback, resume sync, real chapter parsing and 3D rendering as separate player changes, with device validation.

The current changes improve Home and local discovery. Physical TV remote feel, projector contrast, actual streaming, audio quality and native decoder behavior still require the user's compatible TV/account.

