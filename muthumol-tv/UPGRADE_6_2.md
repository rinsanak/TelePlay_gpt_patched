# TG stream 6.2-beta3 — TMPlayer detail-page refinement

Version code 68; applicationId and original signing identity are preserved. This beta contains the current selection changes. It is not a declaration that all 102 selected features are complete. The complete selection and remaining work are in `feature-selection.json` and `FEATURE_SELECTION_PROGRESS.md`.

This update reworks the series page to follow the supplied TMPlayer phone reference: a wide show-art hero with a back control and poster/title lockup, compact metadata and ratings, a prominent continue button, round show actions, synopsis, season and quality chips, and compact landscape episode cards. TVmaze backgrounds are cached separately from posters for the hero. Home rail width, fallback cover art, adjacent multi-episode grouping and progressive series lookup improvements remain included.

Earlier selected-feature changes include configurable movie/episode clip filtering and a hidden-items review, grouped movie copies/shared resume, language-first next episodes, bulk watched/Watch later, per-show preferences, appearance/scaling controls, dynamic banner controls, hold-for-2x/horizontal seek, cached previews and chapter parsing, software audio/video decoder integration and ARM64, subtitle release ranking and invalid ZIP candidate fallback, session/storage/history controls and background/launcher/stream-handoff integrations.

MSone live availability, physical-device compatibility and lifecycle/output behavior are not established by host checks. Frame stepping remains timestamp seeking; anime-specific skip integration, expanded chapter formats, comprehensive ratings and background lifecycle work remain. See the progress file for exact limits. Excluded options are removed from active menus; legacy-copy cleanup does not create a new offline queue.

Publish this build only after the complete unit suite, release lint and assembly succeed and the recovered original certificate is verified. Never replace the feed with an unsigned or untested candidate. No uninstall is required for an original-key update over a compatible earlier original-key build.
