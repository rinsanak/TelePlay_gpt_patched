# TG stream 6.0-beta4 / versionCode 63

## Changes

- Public application label and library brand: TG stream. Application ID and persistent signing identity are unchanged from beta3.
- Main library supports phone portrait touch use with scrollable content, 48dp navigation/filter controls and larger posters. TV/large landscape uses the existing remote layout. Rotation rebuilds the relevant library/season/episode screen. Playback remains fullscreen landscape, with existing touch-capable Media3 controls.
- Target SDK raised from 28 to 35 to address the legacy-target Android warning shown in the user's screenshot. Bluetooth broadcasts are registered with modern flags, and Bluetooth-specific events are only subscribed to when permission permits. Audio-becoming-noisy handling remains available without Bluetooth permission.
- Check for update is accessible from the unconfigured-login screen as well as Settings. The release publisher now uses TG_stream APK/source filenames and the existing pinned repository feed.
- LOST-style show/season/episode browsing remains; subtitles and Cast exclusions remain. The loaded recent-history window is still bounded.

## What is not yet ready

The Telegram publisher API ID/hash were not provided in this environment. They are required internally even though the user only sees phone/QR login. No working authenticated login or actual streaming has been validated. Unconfigured builds must not be published as functional updates. The screenshot's Sign in unavailable state cannot be fixed merely by renaming the app or raising target SDK.

GitHub API access remains denied by the environment's HTTP policy. Source Git pushes work, but Actions signing secrets cannot currently be inspected or saved. The saved environment draft requests api.github.com access; apply that change, then securely persist the existing key with tools/configure-release-secrets.py and inspect/supply the Telegram Actions secrets. Only a successful configured release workflow may change the live updater feed.

The current native libraries are ARMv7 only. ARM64-only phones are not supported by this binary. Host tests do not establish physical TV/phone playback, QR authentication, sound or HDMI behavior. No fresh diagnostic APK is being offered as a replacement for the working release. See UI_REVISION_VERIFICATION.json for current host validation.

## Current host verification

155 tests executed: 151 passed, 4 ARM-native tests skipped, 0 failed/errors. Release lint and signed release build passed. Android 15/API35 tests verify the TG stream label, target SDK 35, portrait touch controls, updater access without login, and receiver registration without Bluetooth permission. Native-rendered phone, standard TV and compact TV layout checks passed. A local temporary-checkout publisher integration check verified TG_stream APK/source filenames and manifest staging; it did not publish a remote update.

Final release lint reported 0 errors and 80 warnings. Optional Bluetooth hardware was declared explicitly so it is not an installation prerequisite. Final signed APK assembly succeeded after that manifest adjustment. The diagnostic binary was not published as a working login update.
