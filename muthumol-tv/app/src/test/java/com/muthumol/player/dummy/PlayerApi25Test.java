package com.muthumol.player.dummy;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import androidx.media3.common.*;
import androidx.media3.common.text.Cue;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=25)
@LooperMode(LooperMode.Mode.PAUSED)
public class PlayerApi25Test {
  ActivityController<PlayerActivity> controller;
  PlayerActivity activity;
  @Before public void start() throws Exception {
    RuntimeEnvironment.getApplication().getSharedPreferences("player",0).edit().clear().commit();
    // Beta4 stored this flag after a false-positive seek watchdog. Beta5 must ignore it.
    RuntimeEnvironment.getApplication().getSharedPreferences("player",0).edit().putBoolean("codecAsync",false).putBoolean("hwplus",true).putFloat("saturation",16).putFloat("contrast",.5f).putFloat("brightness",.5f).commit();
    Movie m=new Movie();m.title="Test video";m.uri="file:///tmp/api25-test-video.mp4";
    Intent intent=new Intent(RuntimeEnvironment.getApplication(),PlayerActivity.class);
    intent.putExtra("movies",new JSONArray().put(m.json()).toString());
    controller=Robolectric.buildActivity(PlayerActivity.class,intent).create();
    activity=controller.get();
  }
  @After public void stop() { if(activity!=null) activity.release(); if(controller!=null) controller.destroy(); }
  @Test public void oldColorConfigurationIsRemovedAndDirectSurfaceIsUsed(){
    assertFalse(activity.hwPlus);assertFalse(activity.prefs.getBoolean("hwplus",true));
    assertFalse(activity.prefs.contains("saturation"));assertFalse(activity.prefs.contains("brightness"));assertFalse(activity.prefs.contains("contrast"));
    assertTrue(activity.view.getVideoSurfaceView() instanceof android.view.SurfaceView);
    assertFalse(activity.prefs.contains("audioeffect"));
  }
  @Test public void cachedMetadataProgressPreventsRestartButTrueStallStillRetries()throws Exception {
    java.io.File dir=java.nio.file.Files.createTempDirectory("metadata-recovery").toFile();
    new DownloadAndCacheTest.PatternLoader().load(0,ReadAheadStore.START_BLOCK,new java.io.File(dir,"0.block"),()->false);
    ReadAheadStore cache=new ReadAheadStore(dir,ReadAheadStore.START_BLOCK,ReadAheadStore.START_BLOCK,()->0,new DownloadAndCacheTest.PatternLoader(),true);
    androidx.media3.exoplayer.ExoPlayer existing=activity.player;
    activity.player=(androidx.media3.exoplayer.ExoPlayer)java.lang.reflect.Proxy.newProxyInstance(
        getClass().getClassLoader(),new Class[]{androidx.media3.exoplayer.ExoPlayer.class},(proxy,method,args)->{
          switch(method.getName()){
            case "getPlaybackState":return Player.STATE_BUFFERING;
            case "getPlaybackSuppressionReason":return 0;
            case "getPlayWhenReady":return true;
            case "getDuration":return C.TIME_UNSET;
            case "getCurrentPosition":return 0L;
            case "getTotalBufferedDuration":return 0L;
            case "getVideoDecoderCounters":case "getVideoFormat":case "getAudioFormat":return null;
            default:throw new AssertionError(method.getName());
          }
        });
    activity.streamStats.cache=cache;
    try {
      cache.setPaused(true);cache.read(0,new byte[1],0,1);
      org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(41));
      long now=android.os.SystemClock.elapsedRealtime();activity.bufferingSince=now-40000;
      activity.bufferingBytes=activity.streamStats.transferredBytes();activity.bufferingReadBytes=0;activity.retryCount=0;
      activity.ticker.run();assertEquals(0,activity.retryCount);assertEquals(now,activity.bufferingSince);
      // Same reader, no bytes for 40 seconds: the bounded recovery must still run.
      activity.bufferingSince=now-40000;activity.ticker.run();assertEquals(1,activity.retryCount);
    }finally{activity.ui.removeCallbacks(activity.ticker);activity.ui.removeCallbacks(activity.retryTask);activity.player=existing;activity.streamStats.cache=null;cache.close();}
  }
  @Test public void bufferedFreezeRecreatesDecoderAndPreservesPosition()throws Exception {
    androidx.media3.exoplayer.ExoPlayer existing=activity.player;
    try {
      activity.player=(androidx.media3.exoplayer.ExoPlayer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{androidx.media3.exoplayer.ExoPlayer.class},(proxy,method,args)->{
        switch(method.getName()){
          case "getVideoFormat":return new Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).build();
          case "getAudioFormat":case "getVideoDecoderCounters":return null;
          case "getDuration":return 1000000L;
          case "getCurrentPosition":return 12345L;
          case "getTotalBufferedDuration":return 93000L;
          case "getPlayWhenReady":return true;
          case "getPlaybackSuppressionReason":return 0;
          case "getPlaybackState":return Player.STATE_READY;
          case "isPlaying":return true;
          case "release":return null;
          default:throw new AssertionError(method.getName());
        }
      });
      activity.firstFrameSeen=true;activity.codecAsync=true;activity.retryCount=0;
      long now=android.os.SystemClock.elapsedRealtime();activity.playbackWatchdog.stalled(now,true,true,12345,-1,93000,true);
      org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(9));
      activity.ticker.run();assertEquals(1,activity.retryCount);assertFalse(activity.codecAsync);
      assertTrue(activity.prefs.getBoolean("codecAsyncFallbackV2",true));assertEquals(12345,activity.player.getCurrentPosition());assertTrue(activity.player.getPlayWhenReady());
    }finally{existing.release();}
  }
  @Test public void seekBufferingDoesNotTriggerRendererRecoveryOrChangeCodecMode()throws Exception {
    androidx.media3.exoplayer.ExoPlayer existing=activity.player;
    try {
      activity.player=(androidx.media3.exoplayer.ExoPlayer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{androidx.media3.exoplayer.ExoPlayer.class},(proxy,method,args)->{
        switch(method.getName()){
          case "getVideoFormat":return new Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).build();
          case "getAudioFormat":case "getVideoDecoderCounters":return null;
          case "getDuration":return 1000000L;
          case "getCurrentPosition":return 12345L;
          case "getTotalBufferedDuration":return 93000L;
          case "getPlayWhenReady":return true;
          case "getPlaybackSuppressionReason":return 0;
          case "getPlaybackState":return Player.STATE_BUFFERING;
          case "isPlaying":return false;
          case "release":return null;
          default:throw new AssertionError(method.getName());
        }
      });
      activity.firstFrameSeen=true;activity.codecAsync=true;activity.retryCount=0;
      long now=android.os.SystemClock.elapsedRealtime();activity.playbackWatchdog.stalled(now,true,true,12345,-1,93000,true);
      org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(9));
      activity.ticker.run();assertEquals(0,activity.retryCount);assertTrue(activity.codecAsync);
      assertTrue(activity.prefs.getBoolean("codecAsyncFallbackV2",true));assertEquals(12345,activity.player.getCurrentPosition());
    }finally{existing.release();activity.ui.removeCallbacks(activity.ticker);activity.player=existing;}
  }
  @Test public void betaPipelineSnapshotContainsCountersAndMode()throws Exception {
    BetaLog.start(activity);
    try{activity.logPipeline();BetaLog.device();}
    finally{BetaLog.collecting=false;BetaLog.writer.join(3000);}
    String log=new String(java.nio.file.Files.readAllBytes(BetaLog.logFile.toPath()),java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(log.contains("pipeline videoQueued="));assertTrue(log.contains("sampleAllocated="));assertTrue(log.contains("surface=SurfaceView"));assertTrue(log.contains("processCpuPercent="));assertFalse(log.contains("api25-test-video.mp4"));
  }
  @Test public void api25CreatesRemoteControlsWithoutAudioSlider() {
    assertNotNull(activity.player);assertEquals(5,activity.top.getChildCount());
    assertFalse(activity.hwPlus);assertTrue(activity.codecAsync);assertTrue(activity.view.getVideoSurfaceView() instanceof android.view.SurfaceView);
    assertEquals(100,activity.boost);assertTrue(activity.stereo);
    assertFalse(containsVolumeSlider(activity.root));
    assertTrue(activity.top.getChildAt(1).isFocusable());
    activity.cycleAspect();assertEquals(1,activity.aspectChoice);
    activity.cycleAspect();assertEquals(2,activity.aspectChoice);
  }
  @Test public void api25UsesTheSameMedia3PcmBufferDefaults() {
    androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider api25=PlayerActivity.audioBufferProvider(25);
    androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider api26=PlayerActivity.audioBufferProvider(26);
    assertEquals(api26.getBufferSizeInBytes(36000,androidx.media3.common.C.ENCODING_PCM_16BIT,0,4,48000,-1,1),
        api25.getBufferSizeInBytes(36000,androidx.media3.common.C.ENCODING_PCM_16BIT,0,4,48000,-1,1));
    assertEquals(api26.getBufferSizeInBytes(100000,androidx.media3.common.C.ENCODING_PCM_16BIT,0,4,48000,-1,1),
        api25.getBufferSizeInBytes(100000,androidx.media3.common.C.ENCODING_PCM_16BIT,0,4,48000,-1,1));
  }
  @Test public void audioRendererClassesAndExactColourCycleArePresent() throws Exception {
    assertNotNull(Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer").getConstructor(android.os.Handler.class,
        androidx.media3.exoplayer.audio.AudioRendererEventListener.class,androidx.media3.exoplayer.audio.AudioSink.class));
    Ui.nextColorIndex=0;
    for(int color:Ui.MENU_COLORS)assertEquals(color,Ui.nextMenuColor(activity));
    assertEquals(Ui.MENU_COLORS[0],Ui.nextMenuColor(activity));assertEquals(7,Ui.MENU_COLORS.length);
  }
  @Test public void compactBufferPanelAndNoVolumeSlider() {
    android.widget.FrameLayout.LayoutParams placement=(android.widget.FrameLayout.LayoutParams)activity.status.getLayoutParams();
    assertEquals(Gravity.BOTTOM|Gravity.RIGHT,placement.gravity);assertTrue(placement.bottomMargin>0);
    assertFalse(containsVolumeSlider(activity.root));assertEquals(View.GONE,activity.status.getVisibility());
    int old=activity.uiColor;activity.refreshBorderColors();assertNotEquals(old,activity.uiColor);
  }
  boolean containsVolumeSlider(View v){if(v instanceof android.widget.SeekBar)return true;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(containsVolumeSlider(g.getChildAt(i)))return true;}return false;}
  @Test public void remoteDpadRoutesAndHighlightAreClear(){
    controller.start().resume().visible();activity.player.stop();activity.showRemoteControls();
    View play=activity.view.findViewById(androidx.media3.ui.R.id.exo_play_pause);play.requestFocus();
    assertNotNull(play.getBackground());assertTrue(play.getScaleX()>1.2f);
    View audio=activity.top.getChildAt(2);assertSame(audio,play.focusSearch(View.FOCUS_UP));
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP));
    assertTrue("Focus after UP: "+activity.root.findFocus()+" top="+activity.top.getVisibility()+" controls="+activity.controlsVisible,audio.hasFocus());assertEquals(0xff101820,((android.widget.Button)audio).getCurrentTextColor());
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN));assertTrue(play.hasFocus());
    View seek=activity.view.findViewById(androidx.media3.ui.R.id.exo_progress);assertSame(seek,play.focusSearch(View.FOCUS_DOWN));
  }
  @Test public void controlsHideAfterFiveSecondsAndRemoteOkOnlyReveals(){
    controller.start().resume().visible();activity.player.stop();activity.showRemoteControls();boolean wasPlaying=activity.player.getPlayWhenReady();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(4999));assertTrue(activity.controlsVisible);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(402));assertFalse(activity.controlsVisible);assertEquals(View.GONE,activity.top.getVisibility());
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));assertTrue(activity.controlsVisible);assertEquals(wasPlaying,activity.player.getPlayWhenReady());assertTrue(activity.view.findViewById(androidx.media3.ui.R.id.exo_play_pause).hasFocus());
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(5401));assertFalse(activity.controlsVisible);
  }
  @Test public void tabletTapDoesNotDismissVisibleControlsAndResetsFiveSeconds(){
    controller.start().resume().visible();activity.player.stop();activity.showRemoteControls();
    assertFalse(activity.view.getControllerHideOnTouch());assertEquals(0,activity.view.getControllerShowTimeoutMs());
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(4));
    tapVideo();activity.view.performClick();assertTrue(activity.controlsVisible);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(4999));assertTrue(activity.controlsVisible);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2));assertFalse(activity.controlsVisible);
    tapVideo();assertTrue(activity.controlsVisible);
  }
  @Test public void touchHoldDoesNotHideControlsBeforeRelease(){
    controller.start().resume().visible();activity.player.stop();activity.showRemoteControls();
    long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,300,250,0);activity.dispatchTouchEvent(down);down.recycle();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6));assertTrue(activity.controlsVisible);
    android.view.MotionEvent up=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,300,250,0);activity.dispatchTouchEvent(up);up.recycle();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(4999));assertTrue(activity.controlsVisible);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2));assertFalse(activity.controlsVisible);
  }
  void tapVideo(){long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,300,250,0);android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+10,android.view.MotionEvent.ACTION_UP,300,250,0);activity.dispatchTouchEvent(down);activity.dispatchTouchEvent(up);down.recycle();up.recycle();}
  @Test public void warmReturnRestartsVisibleControlsTimeout(){
    controller.start().resume().visible();activity.player.stop();activity.showRemoteControls();
    activity.onStop();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6));
    activity.onStart();activity.player.stop();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(4999));assertTrue(activity.controlsVisible);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2));assertFalse(activity.controlsVisible);
  }
  @Test public void focusLabelsSeekFeedbackAndSubtitlePreviewUpdateLive(){
    controller.start().resume().visible();activity.player.stop();activity.showRemoteControls();
    View audio=activity.top.getChildAt(2);audio.requestFocus();assertEquals("Audio and dialogue",activity.focusLabel.getText());assertEquals(View.VISIBLE,activity.focusLabel.getVisibility());
    activity.showSeekFeedback(10000,75000);assertTrue(activity.seekFeedback.getText().toString().contains("+10s"));assertTrue(activity.seekFeedback.getText().toString().contains("1:15"));
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1001));assertEquals(View.GONE,activity.seekFeedback.getVisibility());
    activity.subtitleStyle();android.app.Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();android.widget.TextView preview=d.getWindow().getDecorView().findViewWithTag("subtitle-preview");assertNotNull(preview);int old=activity.fontSize;findButton(d.getWindow().getDecorView(),"A+").performClick();assertEquals(Math.min(40,old+2),activity.fontSize);findButton(d.getWindow().getDecorView(),"Yellow").performClick();assertEquals(android.graphics.Color.YELLOW,preview.getCurrentTextColor());d.dismiss();
  }
  android.widget.Button findButton(View v,String text){if(v instanceof android.widget.Button&&((android.widget.Button)v).getText().toString().equals(text))return (android.widget.Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){android.widget.Button b=findButton(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
  @Test public void staleAndDuplicateTelegramCountersAreNotDoubleCounted() {
    StreamStats stats=new StreamStats();stats.beginFile(11,1000);
    stats.fileProgress(11,1250,true);assertEquals(250,stats.transferredBytes());
    stats.fileProgress(11,1200,false);stats.fileProgress(11,1250,true);assertEquals(250,stats.transferredBytes());
    stats.fileProgress(11,1500,true);assertEquals(500,stats.transferredBytes());
    stats.beginFile(11,0);stats.fileProgress(11,100,true);assertEquals(600,stats.transferredBytes());
    assertTrue(stats.hasNetworkSample());stats.endFile(11);stats.fileProgress(11,1000,true);assertEquals(600,stats.transferredBytes());
  }
  @Test public void homeOpensDirectlyAndKeepsCinemaPaletteWithoutRebuildingViews() {
    ActivityController<MainActivity> hc=Robolectric.buildActivity(MainActivity.class).create();MainActivity h=hc.get();
    assertEquals("home",h.screen);assertNotNull(h.title);android.view.View existing=h.title;int old=h.uiColor;
    h.refreshHomeColors();assertEquals(TmStyle.ACCENT,old);assertEquals(old,h.uiColor);assertSame(existing,h.title);hc.destroy();
  }
  @Test public void compactControlsShareTimeRowAboveBottomSeekBar() {
    controller.start().resume().visible();
    View bottom=activity.view.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);
    View seek=activity.view.findViewById(androidx.media3.ui.R.id.exo_progress);
    View controls=activity.view.findViewById(androidx.media3.ui.R.id.exo_center_controls);
    assertTrue(isInside(bottom,seek));assertTrue(isInside(bottom,controls));
    View row=(View)controls.getParent();ViewGroup stack=(ViewGroup)seek.getParent();
    assertSame(stack,row.getParent());assertTrue(stack.indexOfChild(row)<stack.indexOfChild(seek));
    assertSame(row,activity.view.findViewById(androidx.media3.ui.R.id.exo_position).getParent());
    assertSame(row,activity.view.findViewById(androidx.media3.ui.R.id.exo_duration).getParent());
    assertEquals(Ui.dp(activity,48),bottom.getLayoutParams().height);
    assertTrue(activity.view.findViewById(androidx.media3.ui.R.id.exo_rew) instanceof android.widget.ImageButton);
    assertTrue(activity.view.findViewById(androidx.media3.ui.R.id.exo_ffwd) instanceof android.widget.ImageButton);
    assertNull(bottom.getBackground());assertNull(activity.top.getBackground());assertFalse(containsVolumeSlider(activity.root));
    for(int id:new int[]{androidx.media3.ui.R.id.exo_rew,androidx.media3.ui.R.id.exo_play_pause,androidx.media3.ui.R.id.exo_ffwd}){View icon=activity.view.findViewById(id);if(!icon.hasFocus())assertNull(icon.getBackground());}
    for(int i=1;i<activity.top.getChildCount();i++){
      View icon=activity.top.getChildAt(i);assertEquals(Ui.dp(activity,34),icon.getLayoutParams().height);
      android.graphics.drawable.GradientDrawable bg=(android.graphics.drawable.GradientDrawable)icon.getBackground();assertEquals(android.graphics.drawable.GradientDrawable.OVAL,bg.getShape());assertEquals(icon.hasFocus()?android.graphics.Color.WHITE:android.graphics.Color.TRANSPARENT,bg.getColor().getDefaultColor());
    }
    assertEquals(10000,activity.player.getSeekBackIncrement());assertEquals(10000,activity.player.getSeekForwardIncrement());
    activity.showRemoteControls();int previous=activity.uiColor;activity.view.hideController();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));activity.showRemoteControls();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
    assertNotEquals(previous,activity.uiColor);
  }
  private static boolean isInside(View parent,View child){for(android.view.ViewParent p=child.getParent();p!=null;p=p.getParent())if(p==parent)return true;return false;}
  @Test public void homePageHasRequestedActionsAndNoTelegramReadyBadge() {
    org.robolectric.android.controller.ActivityController<MainActivity> homeController=Robolectric.buildActivity(MainActivity.class).create();
    MainActivity home=homeController.get();home.openHome();
    java.util.List<String> labels=new java.util.ArrayList<>();
    android.view.ViewGroup menu=(android.view.ViewGroup)home.playFromStartButton.getParent();
    for(android.widget.Button button:home.homeActions)labels.add(button.getContentDescription().toString());
    assertTrue(labels.contains("Play from start"));assertTrue(labels.contains("Continue"));
    assertTrue(labels.contains("Refresh latest videos"));assertTrue(labels.contains("Settings"));
    assertFalse(labels.contains("Clear cache"));assertFalse(labels.toString().contains("TELEGRAM READY"));
    homeController.destroy();
  }
  @Test public void settingsPopupContainsRequiredItems(){
    org.robolectric.android.controller.ActivityController<MainActivity> hc=Robolectric.buildActivity(MainActivity.class).create();
    MainActivity home=hc.get();home.openHome();home.settingsPopup();
    android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertNotNull(dialog);assertTrue(dialog.isShowing());
    assertArrayEquals(new String[]{"Clear cache","Log start","Stop and export","Movie filters","Check for update","Artwork source"},MainActivity.HOME_SETTINGS_ACTIONS);
    hc.destroy();
  }
  @Test public void playFromStartOverridesSavedResumePosition(){
    String key="pos:"+activity.movie().cacheIdentity();activity.prefs.edit().putLong(key,420000).commit();
    Intent intent=new Intent(activity,PlayerActivity.class).putExtra("movies",new JSONArray().put(activity.movie().json()).toString()).putExtra("index",0).putExtra("startFromBeginning",true);
    activity.onNewIntent(intent);
    assertEquals(0,activity.resumePosition);
  }
  @Test public void perMovieAspectAndSubtitleDelaySurvivePlayerRecreation() {
    activity.selectAspect(4,true);activity.subtitleDelayMs=1700;activity.saveSubtitleDelay();
    activity.release();activity.aspectChoice=0;activity.subtitleDelayMs=0;activity.restoreMovieSettings();
    assertEquals(4,activity.aspectChoice);assertEquals(1700,activity.subtitleDelayMs);
    activity.start();assertEquals(4,activity.aspectChoice);assertEquals(1700,activity.subtitleDelayMs);
  }
  @Test public void preferredLanguagesAndOffStateReachTrackSelector() {
    activity.prefs.edit().putString("audioLanguage","ml").putString("subtitleLanguage","en").putBoolean("subtitleOff",true).commit();
    activity.applyLanguagePreferences();
    TrackSelectionParameters p=activity.player.getTrackSelectionParameters();
    assertEquals("ml",p.preferredAudioLanguages.get(0));assertEquals("en",p.preferredTextLanguages.get(0));
    assertTrue(p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT));
  }
  @Test public void subtitleMoveUsesFractionAndResetKeepsSourceCue() {
    Cue original=new Cue.Builder().setText("Dialogue").build();
    activity.subtitleOffset=-20;activity.moveSubtitles(Collections.singletonList(original));
    List<Cue> moved=ReflectionHelpers.getField(activity.view.getSubtitleView(),"cues");
    assertEquals(.7f,moved.get(0).line,.001f);
    activity.subtitleOffset=0;activity.moveSubtitles(Collections.singletonList(original));
    List<Cue> reset=ReflectionHelpers.getField(activity.view.getSubtitleView(),"cues");
    assertSame(original,reset.get(0));
  }
  @Test public void backgroundAndRecoveryKeepPositionAndPauseState() {
    activity.player.seekTo(42000);activity.player.pause();
    activity.recoverPlayback(false);assertEquals(42000,activity.player.getCurrentPosition());assertFalse(activity.player.getPlayWhenReady());
    Bundle state=new Bundle();activity.onSaveInstanceState(state);assertEquals(42000,state.getLong("position"));assertFalse(state.getBoolean("playing"));
    androidx.media3.exoplayer.ExoPlayer existing=activity.player;
    activity.onStop();assertSame(existing,activity.player);assertFalse(existing.getPlayWhenReady());assertEquals(42000,activity.resumePosition);
    activity.onStart();assertSame(existing,activity.player);assertEquals(42000,activity.player.getCurrentPosition());assertFalse(activity.player.getPlayWhenReady());
  }
  @Test public void warmSessionExpiresAndMemoryPressureReleasesIt() {
    activity.onStop();assertNotNull(activity.player);
    activity.expireWarm.run();assertNull(activity.player);
    activity.onStart();assertNotNull(activity.player);
    activity.onStop();activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
    assertNull(activity.player);
  }
  @Test public void sameMovieIntentReusesPlayerAndNewMovieReplacesIt() throws Exception {
    androidx.media3.exoplayer.ExoPlayer existing=activity.player;
    activity.onStop();activity.onNewIntent(activity.getIntent());activity.onStart();assertSame(existing,activity.player);
    Movie next=new Movie();next.uri="file:///tmp/other-video.mp4";next.title="Other";
    Intent intent=new Intent(activity,PlayerActivity.class).putExtra("movies",new JSONArray().put(next.json()).toString());
    activity.onStop();activity.onNewIntent(intent);activity.onStart();assertNotSame(existing,activity.player);
    assertEquals(next.uri,activity.movie().uri);assertEquals(0,activity.player.getCurrentPosition());
  }

  @Test public void retryShowsAnimationWithoutBufferStatistics() {
    activity.scheduleRetry();assertEquals(View.GONE,activity.status.getVisibility());
    assertEquals(View.VISIBLE,activity.bufferPulse.getVisibility());
  }
  @Test public void backReturnsToExistingBrowserWithoutDestroyingPlayer() {
    activity.view.hideController();activity.onBackPressed();
    Intent destination=org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
    assertEquals(MainActivity.class.getName(),destination.getComponent().getClassName());
    assertTrue((destination.getFlags()&Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)!=0);
    assertFalse(activity.isFinishing());
  }

  @Test public void bluetoothDisconnectPausesAndDoesNotAutoResume() {
    activity.onStart();activity.player.play();
    activity.sendBroadcast(new Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertFalse(activity.player.getPlayWhenReady());assertFalse(activity.resumePlaying);
    activity.onStop();activity.onStart();assertFalse(activity.player.getPlayWhenReady());
  }
  @Test public void autoSubtitleRespectsMovieIdentityAndManualOff() throws Exception {
    ChannelSession session=activity.session;Movie old=session.movie;String path=session.subtitlePath;
    try {session.movie=activity.movie();session.subtitlePath="/tmp/late-subtitle.srt";session.subtitleType="application/x-subrip";
      activity.autoSubtitleBeforeStart();assertNotNull(activity.subtitleUri);
      activity.subtitleUri=null;activity.prefs.edit().putBoolean(activity.movieKey("externalSubtitleOff"),true).commit();
      activity.autoSubtitleBeforeStart();assertNull(activity.subtitleUri);
      activity.prefs.edit().putBoolean(activity.movieKey("externalSubtitleOff"),false).commit();
      session.movie=new Movie();session.movie.identity="different movie";activity.autoSubtitleBeforeStart();assertNull(activity.subtitleUri);
    }finally{session.movie=old;session.subtitlePath=path;}
  }
  @Test public void stableFileIdentityPreservesResumeAcrossTitleAndFileIdChanges() throws Exception {
    Movie one=new Movie();one.identity="telegram:stable";one.uri="tg://file/9/a";
    Movie two=Movie.from(one.json());two.title="Renamed";two.fileId=10;two.uri="tg://file/10/b";
    assertEquals(one.cacheIdentity(),two.cacheIdentity());
    two.identity="telegram:new";assertNotEquals(one.cacheIdentity(),two.cacheIdentity());
  }
  @Test public void seekIndexSurvivesRestartAndMapsTimeToRealOffsets() throws Exception {
    java.io.File dir=java.nio.file.Files.createTempDirectory("seek-index").toFile();
    try {MovieIndex first=new MovieIndex(dir,1000000);
      first.capture(new androidx.media3.extractor.SeekMap(){
        public boolean isSeekable(){return true;}public long getDurationUs(){return 600000000L;}
        public SeekPoints getSeekPoints(long us){return new SeekPoints(new androidx.media3.extractor.SeekPoint(us,us/1000));}
      });
      MovieIndex restored=new MovieIndex(dir,1000000);
      assertEquals(175000,restored.byteAt(177000000,false));assertEquals(180000,restored.byteAt(177000000,true));
      assertEquals(-1,new MovieIndex(dir,2000000).byteAt(177000000,false));
    }finally{MediaCache.delete(dir);}
  }

  @Test public void homeStoppingAfterPlayerStartsCannotPauseStream() {
    ActivityController<MainActivity> homeController=Robolectric.buildActivity(MainActivity.class).create();
    MainActivity home=homeController.get();home.openHome();activity.onStart();
    home.onStop();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(450));
    assertFalse(activity.session.stats.paused);assertTrue(activity.session.visible);
    activity.onStop();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(450));
    assertTrue(activity.session.stats.paused);homeController.destroy();
  }
  @Test public void homeActionsAndPlayerControlsAreRemoteFriendly() {
    ActivityController<MainActivity> homeController=Robolectric.buildActivity(MainActivity.class).create();
    MainActivity home=homeController.get();home.openHome();
    assertTrue(home.playFromStartButton.isFocusable());assertTrue(home.playFromStartButton.getLayoutParams().height>=Ui.dp(home,34));
    assertEquals(TmStyle.ACCENT,home.uiColor);
    assertEquals("Search library",home.homeActions[2].getContentDescription().toString());
    assertEquals(home.homeActions[2].getId(),home.continueButton.getNextFocusRightId());
    for(int i=0;i<activity.top.getChildCount();i++){android.view.View v=activity.top.getChildAt(i);if(v instanceof android.widget.TextView)assertNotEquals("BT",((android.widget.TextView)v).getText().toString());}
    for(int i=0;i<activity.top.getChildCount();i++){android.view.View v=activity.top.getChildAt(i);assertFalse(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Audio effects"));}
    homeController.destroy();
  }


  @Test public void playerHasTvMenuWithoutDefaultMobileOverflow() {
    assertEquals(5,activity.top.getChildCount());
    assertNull(activity.view.findViewById(androidx.media3.ui.R.id.exo_settings));
    assertNull(activity.view.findViewById(androidx.media3.ui.R.id.exo_overflow_show));
    assertNull(activity.view.findViewById(androidx.media3.ui.R.id.exo_overflow_hide));
    assertNotNull(activity.view.findViewById(androidx.media3.ui.R.id.exo_play_pause));
    android.app.Dialog before=org.robolectric.shadows.ShadowDialog.getLatestDialog();
    activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU));
    assertNotSame(before,org.robolectric.shadows.ShadowDialog.getLatestDialog());
  }
  @Test public void preservesEmbeddedTrackNamesAndFallback() {
    Format named=new Format.Builder().setLabel("English SDH · Original name").setLanguage("eng").build();
    assertEquals("English SDH · Original name",PlayerActivity.trackName(named,0));
    assertEquals("ml",PlayerActivity.trackName(new Format.Builder().setLanguage("mal").build(),0));
    assertEquals("Track 2",PlayerActivity.trackName(new Format.Builder().setLanguage("und").build(),1));
  }
  @Test public void audioButtonOpensTracksOnly() throws Exception {
    androidx.media3.exoplayer.ExoPlayer real=activity.player;
    Format f=new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLabel("Original AAC 5.1").build();
    Tracks tracks=new Tracks(java.util.Collections.singletonList(new Tracks.Group(new TrackGroup(f),false,new int[]{C.FORMAT_HANDLED},new boolean[]{true})));
    activity.player=(androidx.media3.exoplayer.ExoPlayer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{androidx.media3.exoplayer.ExoPlayer.class},(proxy,method,args)->method.getName().equals("getCurrentTracks")?tracks:method.invoke(real,args));
    try {
      activity.audioMenu();android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
      java.util.List<String> text=new java.util.ArrayList<>();collectMenuText(dialog.getWindow().getDecorView(),text);
      assertTrue(text.contains("Audio tracks"));assertTrue(text.contains("Original AAC 5.1 ✓"));
      for(String label:text){assertFalse(label.contains("Output:"));assertFalse(label.contains("Sound boost:"));}
      dialog.dismiss();
    }finally{activity.player=real;}
  }
  private static void collectMenuText(android.view.View v,java.util.List<String> out){
    if(v instanceof android.widget.TextView)out.add(((android.widget.TextView)v).getText().toString());
    if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectMenuText(g.getChildAt(i),out);}
  }
 @Test public void heldMediaSeekDoesNotFloodPlayerWithSeeks()throws Exception{
  androidx.media3.exoplayer.ExoPlayer original=activity.player;int[] count={0};
  activity.player=(androidx.media3.exoplayer.ExoPlayer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{androidx.media3.exoplayer.ExoPlayer.class},(proxy,method,args)->{if(method.getName().equals("seekForward")){count[0]++;return null;}try{return method.invoke(original,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});
  try{for(int n=0;n<30;n++)activity.dispatchKeyEvent(new KeyEvent(100,100+n*50,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,n));assertEquals(1,count[0]);}
  finally{activity.player=original;}
 }


 @Test public void activityHandoffKeepsPreloadUntilGraceExpires(){
  ChannelSession session=activity.session;Movie previous=session.movie;boolean ready=session.tg.ready;Set<Object> owners=new HashSet<>(session.visibleOwners);
  try{session.movie=null;session.tg.ready=false;session.visibleOwners.clear();Object home=new Object(),play=new Object();session.onVisible(home);session.onHidden(home);assertFalse(session.stats.paused);
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(399));assertFalse(session.stats.paused);
   session.onVisible(play);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2));assertFalse(session.stats.paused);
   session.onHidden(play);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));assertTrue(session.stats.paused);
  }finally{session.ui.removeCallbacks(session.pauseTask);session.ui.removeCallbacks(session.poll);session.visibleOwners.clear();session.visibleOwners.addAll(owners);session.movie=previous;session.tg.ready=ready;}
 }
}

