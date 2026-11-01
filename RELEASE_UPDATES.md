# Recurring Muthumol TV updates

Source lives in `muthumol-tv/` on main. The existing TV updater continues reading `latest.json` on `tg-stream-updates`. Keep the same application ID and original signing key.

## One-time signing setup

In GitHub repository Settings > Secrets and variables > Actions, create **MUTHUMOL_KEYSTORE_BASE64** from the ORIGINAL keystore, encoded as base64. This is sensitive signing material: use the secret UI, never a commit, issue or message. The existing project expects alias `androiddebugkey`, store password `android`, key password `android`. The published V5.6 certificate was independently verified as SHA256 `26e7b63fcdd306fe3f52a0172a3180160c0aee359a6c00f933ded41e8c0b8318`. A newly generated debug key will NOT match.

The original private key cannot be recovered from an APK. Without it, an update over the installed app is impossible. Do not replace the key or publish an unsigned/mismatched APK to work around this.

## Every update

1. Make source changes under `muthumol-tv/`.
2. Increase `versionCode` and set `versionName` in `muthumol-tv/app/build.gradle`. This revision uses 61 / 6.0-beta2.
3. Push to main. The Muthumol workflow runs tests, lint and a signed release build. It verifies the installed-app certificate, APK package/version and SHA256.
4. After checks pass, one commit on `tg-stream-updates` publishes APK, versioned source ZIP and latest.json together. The old app's Check for update uses this manifest. The source archive and reports are also retained as workflow artifacts.
5. On the TV choose Check for update, then allow Android's installer prompt. This is not a silent install.

Missing secrets, wrong signatures, failed tests/lint or non-increasing version codes stop publication. A source push alone does not mean the APK/feed was published: check that the workflow succeeded and the feed shows the intended version. The old feed stays active if publication fails. Parallel releases are serialized; no force pushes are used. Workflow changes are reviewed through normal source control.

The older `publish-tg-stream.yml` workflow on the feed branch only triggers when that workflow file itself changes. This new publisher does not modify it or rely on expiring chat attachment URLs.
