# Signing recovered — no user form required

The original key was recovered from the private Drive folder TelePlay Projector App/build-signing/debug.keystore. Its certificate matches V5.6. The previously offered signing-setup.html is obsolete; the user does not need to fill it. TG_API_ID and TG_API_HASH Repository secrets have already been supplied.

Use the configured CI APK, protected local key, and tools/sign-original-apk.py. Publish through the release workflow with full original-certificate checks. See RELEASE_UPDATES.md.
