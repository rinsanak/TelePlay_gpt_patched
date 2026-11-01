# Muthumol TV release requirements

The user wants every app update available both as an APK and through the installed app's Check for update.

- Edit the active source under `muthumol-tv/`; the root TelePlay ZIP is the old archived project.
- Preserve existing subtitle behavior. Do not add Cast unless the user changes this requirement.
- Every app release increments versionCode and updates versionName in app/build.gradle. Keep applicationId unchanged.
- Run unit tests, release lint and release assembly. Report skipped ARM-native tests separately from passed tests; do not claim physical-TV testing from host tests.
- Publish through `.github/workflows/muthumol-release.yml`. It verifies the original certificate, package, tests, lint, monotonically increasing version and checksum before publishing APK, source and latest.json together to `tg-stream-updates`.
- The user explicitly authorized a new persistent signing key on 2026-11-01 if the original could not be recovered. Its public SHA256 is 70db42e5a1ee6f2fe4e9b5b63ac6a90360e89466d9a1bb1ee5147a57d0316460. Migration from V5.6 requires one reinstall; never claim it can install over the old identity. After migration keep this same key for all releases. Store it via Actions secrets, never in source. Local protected signing material is under /workspace/private-signing pending secure GitHub persistence; do not regenerate it.
- Read RELEASE_UPDATES.md for the certificate pin and workflow. Never replace the updater URL with an expiring attachment link.
- After pushing, distinguish source publication from a successful signed-APK/feed publication. Verify the live manifest before claiming an in-app update is available.

- End-user login must only use phone/QR (plus Telegram-requested verification). TG_API_ID and TG_API_HASH belong to build configuration, not a TV input form. Never invent or borrow Telegram API credentials. Do not publish a build whose login is unconfigured.
- Home groups shows such as LOST into one card. Opening a show selects a season, then an episode; preserve this hierarchy in future updates.
