package com.muthumol.player.dummy;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.io.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class MovieResetAndDiagnosticsTest {
  @Test public void resetForgetsEveryMovieButPreservesAccountChannelAndGlobalSettings()throws Exception {
    Context c=RuntimeEnvironment.getApplication();SharedPreferences p=c.getSharedPreferences("player",0);
    p.edit().clear().putLong("pos:old",9000).putLong("pos:tg://file/17/name",8000)
      .putInt("aspect:old",4).putLong("subdelay:another",500).putString("externalSubtitleUri:old","file:///tmp/sub.srt")
      .putString("externalSubtitleMime:old","application/x-subrip").putBoolean("externalSubtitleOff:old",true)
      .putBoolean("stereo",false).putInt("boost",200).putString("audioLanguage","ml").commit();
    SharedPreferences channel=c.getSharedPreferences("channel",0);
    channel.edit().clear().putLong("id",99).putString("title","Movies").putString("movie","{}").putString("subtitlePath","old").commit();
    SharedPreferences account=c.getSharedPreferences("account-fixture",0);account.edit().putString("credential","kept").commit();
    assertEquals(7,MovieData.clearPreferences(c));
    for(String key:p.getAll().keySet())assertFalse(MovieData.isMovieKey(key));
    assertFalse(p.getBoolean("stereo",true));assertEquals(200,p.getInt("boost",0));assertEquals("ml",p.getString("audioLanguage",""));
    assertEquals(99,channel.getLong("id",0));assertFalse(channel.contains("movie"));assertFalse(channel.contains("subtitlePath"));
    assertEquals("kept",account.getString("credential",""));
    Movie m=new Movie();m.identity="old";assertEquals(0,m.savedPosition(c));
  }
  @Test public void resetDeletesMediaAndSubtitleBytesButKeepsLogExports()throws Exception {
    Context c=RuntimeEnvironment.getApplication();File segment=MediaCache.directory(c,"fixture");segment.mkdirs();
    new FileOutputStream(new File(segment,"index.bin")).close();
    File subs=new File(c.getFilesDir(),"channel-subtitles");subs.mkdirs();new FileOutputStream(new File(subs,"old.srt")).close();
    File imported=new File(c.getCacheDir(),"imported-subtitle.srt");new FileOutputStream(imported).close();
    File exports=new File(c.getCacheDir(),"beta-exports");exports.mkdirs();File log=new File(exports,"beta-stream-test.txt");new FileOutputStream(log).close();
    MovieData.clearFiles(c);assertFalse(segment.exists());assertFalse(subs.exists());assertFalse(imported.exists());assertTrue(log.exists());
  }
  @Test public void resetAlsoForgetsOldBytePositionAndDownloadStatistics(){
    StreamStats stats=new StreamStats();stats.setPlaybackPosition(5000,10000,1000000);stats.stalls=3;stats.downloadRequests=8;stats.copiedBytes=999;
    stats.meter.add(1024,1000);stats.resetMovie();assertEquals(-1,stats.playbackBytePosition());assertEquals(0,stats.transferredBytes());assertEquals(0,stats.stalls);assertEquals(0,stats.downloadRequests);assertEquals(0,stats.copiedBytes);
  }
  @Test public void traceIncludesCauseFramesAndSuppressedErrorsWithoutPrivateUrlsOrPaths(){
    IllegalArgumentException cause=new IllegalArgumentException("bad https://host/private?token=secret /data/user/0/movie.mp4 phone=+966555555555");
    cause.setStackTrace(new StackTraceElement[]{new StackTraceElement("android.media.AudioTrack","write","AudioTrack.java",123)});
    RuntimeException top=new RuntimeException("playback",cause);top.addSuppressed(new IOException("read failed"));
    String trace=DiagnosticText.trace(top);assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException"));
    assertTrue(trace.contains("AudioTrack.write(AudioTrack.java:123)"));assertTrue(trace.contains("Suppressed: java.io.IOException"));
    assertFalse(trace.contains("secret"));assertFalse(trace.contains("movie.mp4"));assertFalse(trace.contains("966555555555"));
  }
}
