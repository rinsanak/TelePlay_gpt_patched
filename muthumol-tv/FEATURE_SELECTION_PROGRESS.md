# TG stream selected-feature checkpoint — 2026-11-02

**UI revision: 6.2-beta3, version code 68.** The series page now follows the supplied TMPlayer mobile reference with a wide art hero, title/poster lockup, metadata and ratings, continue control, round actions, synopsis, season/quality chips, and landscape episode cards. Show backgrounds cache separately from posters. Home rail width, fallback cover art, adjacent multi-episode grouping, and progressive series lookup improvements remain. The original signing key and package remain selected.

Inventory: `tg-stream-complete-feature-selection-v2-20261102`.
Authoritative selected/excluded IDs: `feature-selection.json` (102 selected, 35 excluded).
Source baseline: `a4a7ebe`, published 6.1-beta1 / 65. Previous beta: 6.2-beta2 / 67. Current source: 6.2-beta3 / 68. The updater feed is updated only after the signed build passes validation.

The submitted selection is applied to this local development branch. This is not a completion or device-compatibility certification for all 102 features. At this checkpoint nothing had been pushed and the updater feed was unchanged. The subsequent user request authorizes preparing and publishing a verified beta through Check for update.

## Work implemented in the development source

- Main Telegram chat-list search excludes archives. Short-video filtering uses known duration first, then configurable size limits for unknown durations; hidden loaded videos can be reviewed without deleting Telegram originals.
- Library movie/episode versions share logical identities and resume state. Next-episode version matching prioritizes language. Bulk watched/Watch later controls and per-show playback preferences are present. Existing episode watched keys are read during migration.
- Excluded favourites, voice search, Home row customization, offline queue, PiP, bookmarks, A–B repeat, sleep timer and skip-silence options have no active entry in the revised Home/player menus. Dormant implementation classes may remain as dependencies or legacy-data readers.
- Home carousel updates after asynchronous loading; appearance, high contrast, font/interface scaling and the rotating player border are present. Hero transitions obey reduced motion. Metadata correction and public description/rating lookups are present, with a seven-day metadata cache.
- Playback additions include hold-for-2×, horizontal seek settings, bounded cached-frame preview, MP4 Nero `chpl`/Matroska chapter reading, refresh-rate matching, decoder recovery/software fallback, audio delay and independent levelling controls. Existing frame stepping remains seek-based.
- Automatic subtitles search the same chat, rank Malayalam/release matches, validate season/episode/year, read bounded ZIP packs, skip unreadable candidates and cache successful choices. MSone fallback and its manual picker use the official provider domain; manual choices retain precedence. Embedded and external subtitle controls remain.
- Session management, storage review/legacy watched-copy cleanup, content alerts, TV preview-channel publication, history removal/Undo, background audio and loopback external-stream handoff have source implementations. TV card intents now have an entry handler. These service/UI integrations require device testing.
- ARM64 TDLib is included alongside ARMv7. Media3 1.7.1 and pinned NextLib software audio/video decoders are integrated; third-party notices were updated.

## Interpretation retained from the submitted selection

D09 and C04 operate on legacy saved copies/cleanup and storage status. They do not introduce D01–D05's excluded offline queue or background-download UI. D11 hands a live Telegram stream to an external player through a temporary authenticated loopback endpoint; D10's offline-file opening UI is excluded.

## Verification

| Check | Result |
| --- | --- |
| Debug and release Java compilation | Passed |
| Complete Robolectric/JUnit suite | 176 tests, 0 failures, 4 native ARM skips |
| Release lint | Passed |
| Signed release assembly | Passed with the recovered original key |
| Original certificate and package | Verified by the update staging tool |
| Physical phone/TV interaction, live artwork provider and MSone | Not verified on the user's device |

The selected-feature implementation still needs the device-level checks listed below.

## Remaining implementation and verification work

- P19: previews currently depend on already cached extractable frames; no dedicated thumbnail index exists.
- P20: chapter coverage is limited to Nero `chpl`, Matroska chapter metadata before the first Cluster, and existing player metadata. QuickTime text-track chapters and late Matroska SeekHead chapter lookup remain.
- P21: exact decoded-frame advancement is not implemented; current controls perform an exact-timestamp seek using the advertised FPS.
- P22: TheIntroDB integration exists. Separate anime identity/absolute-episode mapping and AniSkip integration remain.
- H08: IMDb/Rotten Tomatoes/Metacritic scores are shown only when available from public Wikidata claims. Reliable comprehensive coverage is not established.
- C05: TV recommendations need actual launcher validation, including channel browsability and Continue behavior.
- P30/D11: background-audio and external-player ownership/lifecycle need device validation; foreground-service lifecycle under Activity destruction is not complete.
- A09: audio-delay sign and audible lip-sync require actual output-device verification.
- C12: ELF alignment and target SDK checks do not establish modern-Android runtime compatibility. Installation/login/playback must be tested on the affected device.
- Entire selected/excluded inventory needs an end-to-end feature audit after dependencies become available. Updated archive/Home/subtitle unit fixtures have not been run under Robolectric.
- Corresponding-source packaging for the bundled NextLib/FFmpeg build must be finalized before distributing a release.

## Release checks

Publish only after the final version-code 67 APK passes the same complete unit, lint, signing, package and checksum checks. Never publish an unsigned candidate. Existing compatible installs use the original key and do not require uninstall.
