# Muthumol TV 6.0 Beta 1: verification

Signed release build, R8/resource shrinking, and release lint passed.

{'tests': 143, 'failures': 0, 'errors': 0, 'skipped': 4}

The skipped tests are ARM-native audio decoder smoke tests; the build host is x86.

Signing certificate matches the actual TG_stream_V5.6.apk. Both native libraries are byte-for-byte identical to the baseline APK. Nine existing subtitle method bodies were compared unchanged.

Physical TV, Telegram account authentication/streaming, audible sound quality, HDMI, voice search and PiP were not exercised. This is an incomplete parity beta; see README.md for remaining features and compatibility limits.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
|AudioNativeSmokeTest|4|0|0|4|
|AdaptiveLoadControlTest|2|0|0|0|
|AppUpdaterTest|13|0|0|0|
|ArtworkTest|9|0|0|0|
|AudioTimingSinkTest|2|0|0|0|
|BetaDiagnosticsTest|2|0|0|0|
|BoostProcessorTest|7|0|0|0|
|ChannelScanTest|3|0|0|0|
|CinemaRenderTest|2|0|0|0|
|CinemaUiTest|6|0|0|0|
|DownloadAndCacheTest|16|0|0|0|
|DownloadWindowTest|3|0|0|0|
|MovieResetAndDiagnosticsTest|4|0|0|0|
|PlaybackPolicyTest|6|0|0|0|
|PlaybackWatchdogTest|4|0|0|0|
|PlayerApi25Test|40|0|0|0|
|RecentMoviesTest|4|0|0|0|
|StereoProcessorTest|2|0|0|0|
|TelegramStreamingTest|5|0|0|0|
|TvUpgradeTest|9|0|0|0|
