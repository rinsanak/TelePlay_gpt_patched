# Muthumol TV release requirements

The user wants every app update available both as an APK and through the installed app's Check for update.

- Edit the active source under `muthumol-tv/`; the root TelePlay ZIP is the old archived project.
- Preserve existing subtitle behavior. Do not add Cast unless the user changes this requirement.
- Every app release increments versionCode and updates versionName in app/build.gradle. Keep applicationId unchanged.
- Run unit tests, release lint and release assembly. Report skipped ARM-native tests separately from passed tests; do not claim physical-TV testing from host tests.
- Publish through `.github/workflows/muthumol-release.yml`. It verifies the original certificate, package, tests, lint, monotonically increasing version and checksum before publishing APK, source and latest.json together to `tg-stream-updates`.
- Reuse the ORIGINAL keystore via Actions secret MUTHUMOL_KEYSTORE_BASE64. Never create a replacement key, commit signing material, or publish an unsigned/mismatched APK as an update.
- Read RELEASE_UPDATES.md for the certificate pin and workflow. Never replace the updater URL with an expiring attachment link.
- After pushing, distinguish source publication from a successful signed-APK/feed publication. Verify the live manifest before claiming an in-app update is available.
