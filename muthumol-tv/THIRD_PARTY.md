# Third-party components

- TDLib: tdlib/td, Boost Software License 1.0. Community prebuilt binary and matching Java bindings: io.github.tdlib-android:core:0.1.1, https://github.com/AkashPriyadarshii/tdlib-android . Pinned AAR SHA256: 1ec6d6c6c83b655349d8a91182145cdd5400392246136ad79432253ad6004106 . Only Java jar and ARMv7 native binary consumed; no Android API26 wrapper.
- AndroidX Media3 1.4.1: Android Open Source Project, Apache License2.0, https://developer.android.com/media/media3 . FFmpeg audio extension included; no software-video extension.
- App face icon: edited from user-supplied photograph using built-in image generation; prompt: square photographic face crop, preserve identity/expression/colors, no text/logo/borders. Resource derivative192px JPEG, approx8KB.

## Audio decoder
Media3 1.4.1 decoder_ffmpeg source from https://github.com/androidx/media (Apache-2.0); FFmpeg 6.0.1 built without GPL/nonfree options (LGPL-2.1-or-later), AAC, AC3, EAC3, DTS, TrueHD, MP3/MP2/MP1, Vorbis, Opus, AMR, FLAC, ALAC and G.711 audio decoders. Native build instructions and matching sources included; binary contains no software VIDEO decoder.

Java FFmpeg extension files are pinned to AndroidX Media3 tag 1.4.1, with an UnstableApi annotation added for lint. FFmpeg source archive and LGPL notice are included.

Java FFmpeg extension files are pinned to AndroidX Media3 tag 1.4.1, with an UnstableApi annotation added for lint. FFmpeg source archive and LGPL notice are included.

Java FFmpeg extension files are pinned to AndroidX Media3 tag 1.4.1, with an UnstableApi annotation added for lint. FFmpeg source archive and LGPL notice are included.


## Online artwork (5.2)
TVmaze API data: https://www.tvmaze.com/api , CC BY-SA. Source links are shown in Settings > Artwork source and a credit is displayed on Home.
Wikipedia PageImages API: https://www.mediawiki.org/wiki/API:Pageimages . Image-specific authorship/licensing is on each linked article/file page. No images are bundled in the APK.
Test fixtures only: Baahubali: The Beginning poster from its Wikipedia PageImages response; Breaking Bad show/episode data and Pilot still from TVmaze. These fixtures are excluded from the APK.

5.3: Series promotional cover from TVmaze replaces episode stills. Prior scene image fixtures are no longer included. Home source attribution remains available via Settings > Artwork source.
