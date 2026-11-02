# TG stream 6.1 beta 1

Version code 65. Package and original V5.6 signing certificate remain unchanged.

## Browsing and playback

Home uses a horizontal navigation bar, a cinema hero, Continue watching and a unified movie/series shelf. Chat shelves and the side rail are removed. Opening a show starts a separate account-wide title search and drains both video and document cursors automatically. The initial account-wide library still uses pagination to avoid downloading an entire account on startup. Errors stop automatic paging and expose Retry; repeated cursors stop the scan.

Episode rows represent season/episode pairs. All file copies remain accessible in the version picker. Queue navigation keeps the selected quality/encoding preference, skips duplicate copies and crosses season boundaries. Previous and next controls appear only for series. The player continues filling its episode queue in the background if playback starts before discovery finishes.

Phone video-surface gestures support left/right double-tap −10/+10 seconds and vertical left brightness/right media volume. Sliders, buttons and the time bar retain their own touch handling. Remote lock suppresses gestures.

## Progress sync

Progress and watched state are stored in the signed-in user's Telegram Saved Messages. The app searches only its TGSTREAM_WATCH_V1 records. Each record contains source chat/message references, position, duration, watched and update timestamp; it never uploads device-local file IDs, paths, captions, login data or credentials. Existing records are edited. Writes are coalesced and rate-limited; unsuccessful sync remains pending for retry.

Devices must use the same Telegram account and this release. Home refreshes progress on entry; playback pulls before starting, with an eight-second local-play fallback when the network is slow. Cross-device state resolves source messages on the receiving device rather than reusing another device's file ID. Newer explicit unwatched state can replace older watched state. This is eventual sync and is not a guarantee of instantaneous simultaneous playback coordination.

## Subtitles

Automatic selection searches document history in the selected video's chat. Filename or caption must match the title and exact season/episode, with incompatible years rejected. ZIP season packs are read in memory with compressed, expanded and entry-count bounds. A matching inner subtitle is selected without extracting archive paths. Existing subtitle import, track selection, style, timing and per-video manual/off choices remain available.

If the chat contains no matching subtitle, the app attempts the official MSone addon, discovering its manifest from the official install page and using IMDb identity and episode coordinates. MSone availability, downloadable formats and manifest configuration are controlled by that service. Its live endpoint returned HTTP 403 in this build environment, so end-to-end MSone downloads are **not verified**. The player exposes automatic-subtitle status, Retry, the MSone website and manual import. No TMPlayer gateway key is copied or required. RAR packs are not automatically extracted.

## Verification

Host tests include 117-episode version grouping, automatic multi-page series discovery, subtitle mismatch rejection, ZIP selection and expansion limits, gesture direction classification, watched-copy identity and Saved Messages request/import behavior. Release lint and assembly are required before signing. ARM-native rendering/decoding tests that cannot run on the host must be reported as skipped. No physical phone, projector or TV test is implied by host checks.
