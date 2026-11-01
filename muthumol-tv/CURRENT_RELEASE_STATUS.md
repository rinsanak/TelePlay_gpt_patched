# 6.0-beta3 status

151 tests: 147 passed, 4 ARM-native checks skipped, 0 failed. Release lint, R8, resource shrinking and signed APK assembly passed. The signed APK certificate is pinned in the publisher. Existing subtitle/player implementations remain unchanged. Physical TV/account login/streaming were not tested.

Home now groups shows by normalized name/year. A show opens season selection; a season opens ordered episodes. The player receives the show's loaded episode queue. Back navigation returns to seasons and Home. Episode long press retains individual actions. The lists cover the existing bounded recent-history window and remembered episodes; this is not a full-channel scan or a promise that unloaded seasons will appear.

The TV API ID/hash input form is gone. The build reads TG_API_ID/TG_API_HASH from publisher environment; legacy configured installs preserve their stored values. A build without those values displays Sign in unavailable rather than an API form. No real publisher Telegram credentials were available, so the current signed APK is diagnostic only and must not be published as login-ready. The publisher rejects this missing prerequisite.

No old keystore was found in supplied files/repository history. GitHub Actions secret inventory could not be inspected because api.github.com is blocked by the environment network policy. The user authorized a replacement: it was generated securely outside the repository and is NOT yet persisted in GitHub Secrets. Its public certificate SHA256 is 70db42e5a1ee6f2fe4e9b5b63ac6a90360e89466d9a1bb1ee5147a57d0316460. This identity needs one reinstall from V5.6; future releases must reuse it.

The environment draft now requests api.github.com access and Telegram variable names. Apply the network change in environment settings; then use tools/configure-release-secrets.py to persist the existing key securely, check existing Telegram Actions bindings, supply missing publisher credentials securely, run the workflow and verify latest.json. Source Git push works; API access and a successful signed feed publication are separate. Historical verification documents refer to older revisions.
