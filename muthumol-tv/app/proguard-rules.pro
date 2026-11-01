-keep class org.drinkless.tdlib.** { *; }
-keep class androidx.media3.exoplayer.Renderer { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# Keep useful opt-in diagnostics and name-based AudioSink/Renderer proxy dispatch.
-dontobfuscate
-keep interface androidx.media3.exoplayer.audio.AudioSink { *; }
