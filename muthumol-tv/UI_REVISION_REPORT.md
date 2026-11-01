# UI revision verification, 2026-11-01

The uploaded 6.0 beta1 source was inspected against TMPlayer commit 268b02d9ca8835385802037ae5241d142fa0c066. Full historical conversations could not be opened because no read_thread tool was available; their supplied previews were treated as context. See FEATURE_COMPARISON.md for the actual gaps and current changes.

## Current results

- 147 unit/Robolectric tests executed: **143 passed, 4 skipped, 0 failed, 0 errors**.
- The four ARM-native decoder smoke checks are skipped on the x86 build host.
- Release lint completed: **0 errors, 75 warnings**. This is not a warning-free source tree.
- Release compilation, R8 optimization, resource shrinking and unsigned APK packaging succeeded.
- Standard 960x540 and compact 960x405 Android layouts rendered. Both tests assert that the primary action and poster row are visible without clipping.
- New tests cover typo matching/ranking, irrelevant queries, collection empty states, stale playback prevention, search reset and D-pad routes. Existing palette tests were updated to assert the intended stable Home accent instead of rotating colours.
- PlayerActivity.java and all other existing player/subtitle implementation files are byte-for-byte unchanged from the supplied ZIP. Cast was not added.
- Both packaged ARMv7 native libraries match the supplied source binaries byte-for-byte. No ARM64 support is claimed.
- No signing key was available or generated. The APK is **unsigned**, cannot be installed as an update, and is not a published release.
- Android version identifiers are now 61 / 6.0-beta2. Publication still needs the original signing identity and a successful release workflow.
- No physical TV/account, streaming, sound quality, HDMI, QR authentication or native decoder run was performed. Host-side UI rendering does not establish those behaviors.

Machine-readable counts and artifact hash are in UI_REVISION_VERIFICATION.json. BUILD_VERIFICATION.json and TEST_REPORT.md are historical records supplied inside the original archive.

## Reproduce

Use a complete JDK 17, Gradle wrapper 8.9, SDK platform 35 and AGP's required build tools (34.0.0 was selected by AGP 8.7.3; 35.0.0 was also installed). Set ANDROID_HOME or uncommitted sdk.dir. Retain your environment's proxy and CA trust when needed.

```sh
bash gradlew --no-daemon --max-workers=2 -I tools/unsigned-release.gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The init script uses Android's finalizeDsl hook to disable release signing only, after the project has configured its signing identity. It does not change tests, lint, TLS checks, or source. For an actual signed release, securely provide the existing MUTHUMOL_SIGNING_KEY and omit the unsigned init script. Do not generate a replacement key for an installed app.

## Environment setup

The original TelePlay repository remains unchanged. Its archive was extracted to a separate development directory. Frozen-lockfile npm installation, production build, TypeScript check, local HTTP checks and Chromium login-page rendering passed. Python dependencies were installed in an isolated venv, with GCC for TgCrypto and SQLAlchemy's supported asyncio extra. Dependency imports and a real async SQLite query passed.

Full TelePlay backend startup remains blocked by missing Telegram/JWT settings and unsupported current MTProto TCP routing. No credentials were invented and no healthy-backend claim was made. Reusable install/start instructions and required variable names were saved to the environment configuration draft. Saving a draft does not publish the environment; review/save it in environment settings and publish when appropriate.
