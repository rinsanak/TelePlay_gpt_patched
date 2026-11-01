# Original signing identity recovered

The private Drive backup TelePlay Projector App/build-signing/debug.keystore was recovered and its PrivateKeyEntry certificate verified to match V5.6 exactly: 26e7b63fcdd306fe3f52a0172a3180160c0aee359a6c00f933ded41e8c0b8318. Alias: androiddebugkey. Protected local copy: /workspace/private-signing-original/debug.keystore. Private material remains outside source and backed up in the existing private Drive folder.

The user refuses uninstall. The original identity is mandatory. The previously proposed replacement key is obsolete; no migration is needed. The old setup form is unnecessary.

Telegram Repository secrets TG_API_ID and TG_API_HASH are already supplied. CI validates them and builds/tests/lints a configured unsigned release. Download the TG-stream-original-key-signing-input artifact, sign it locally using the recovered original key, and verify certificate/package/version. Retain the signed APK blob on a candidate branch and commit muthumol-tv/release-candidate.json with its blob SHA and checksum. The workflow retrieves only from this repository with its own GitHub token. CI revalidates source, signing identity, package, version, checksum, tests and lint, and publishes APK/source/latest.json atomically. An unsigned preparation run never modifies the feed. Confirm the live feed after signed publication.

Alternatively, an owner may securely configure the original key and passwords as Actions signing secrets, using alias androiddebugkey, for entirely automatic signing. Do not put private material in workflow inputs or source. The prior configure-release-secrets.py helper was for the obsolete replacement key and must not be used for this original key.

Published beta5/code64 successfully in run 41335770111. The live manifest and APK blob match the verified original-certificate signed APK. Remove the previous release-candidate.json before preparing a future release; commit the next candidate only after original-key signing. After publication, the consumed candidate file is removed with a CI-skip housekeeping commit to avoid republishing the same version.
