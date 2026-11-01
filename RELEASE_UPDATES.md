# Recurring Muthumol TV releases

Source: `muthumol-tv/` on main. APK/source/manifest destination: existing `tg-stream-updates` branch. Current candidate: TG stream 63 / 6.0-beta4.

## Signing migration authorized by the user

The original V5.6 private key was not found in the available repository history or supplied files. GitHub API access is currently blocked by the cloud network policy, so server-side Actions secret inventory could not be inspected. The user explicitly authorized a replacement if recovery was not possible. A new persistent key was generated and protected outside the checkout under `/workspace/private-signing`; it has NOT yet been saved to GitHub Secrets. Preserve these files; do not regenerate the key.

New public certificate SHA256: `70db42e5a1ee6f2fe4e9b5b63ac6a90360e89466d9a1bb1ee5147a57d0316460`. Old V5.6 SHA256: `26e7b63fcdd306fe3f52a0172a3180160c0aee359a6c00f933ded41e8c0b8318`. The new identity requires one uninstall/reinstall and Telegram sign-in again; it cannot update the old installation in place. After migration all later APKs must use the new persistent key.

## Required secure GitHub configuration

Actions secrets: `MUTHUMOL_KEYSTORE_BASE64`, `MUTHUMOL_STORE_PASSWORD`, `MUTHUMOL_KEY_PASSWORD`, `TG_API_ID`, `TG_API_HASH`. Actions variable `MUTHUMOL_KEY_ALIAS`: `muthumol-release`. Encode the protected keystore as base64 only when sending it to the secret API; never commit key material or passwords.

Telegram still requires the publisher's own API ID/hash internally. Phone and QR authenticate the user's account, not the application. The TV no longer asks users for developer credentials. Build-time values come from the publisher's Actions secrets; APK-embedded application credentials cannot be treated as unextractable. Preserve existing configured credentials on legacy installations. A build without application credentials has no API-entry form and explains that sign-in is unavailable. The release workflow and publisher reject absent credentials before changing the live feed.

## Every release

Increase versionCode and versionName, push source to main, and verify the workflow succeeds. It runs tests/lint and signed assembly, then checks the pinned certificate, package, version and checksum. It publishes the APK, source ZIP and latest.json in one feed-branch commit. Wrong signatures, missing prerequisites, test failures and stale version codes leave the feed unchanged. Do not use expiring attachment URLs. Native ARM tests skipped on x86 do not establish TV playback.

GitHub API access to `api.github.com` was requested in the environment draft. The draft must be applied before this agent can inspect or save Actions secrets. Source Git push access works independently. Once secure prerequisites are supplied, rerun the workflow and verify the live manifest; source publication alone is not an APK release.

After API access is enabled, the agent can securely upload the already generated signing material using `python muthumol-tv/tools/configure-release-secrets.py /workspace/private-signing`. This helper sends values over stdin to gh secret set; it does not print or commit them. Existing Telegram secret bindings are preserved unless actual TG_API_ID/TG_API_HASH are supplied in the process.
