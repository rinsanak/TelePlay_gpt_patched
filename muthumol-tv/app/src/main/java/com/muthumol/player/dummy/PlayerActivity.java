package com.muthumol.player.dummy;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.common.text.*;
import androidx.media3.common.audio.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.analytics.*;
import androidx.media3.exoplayer.audio.*;
import androidx.media3.exoplayer.mediacodec.*;
import androidx.media3.exoplayer.source.*;
import androidx.media3.exoplayer.text.*;
import androidx.media3.exoplayer.upstream.*;
import androidx.media3.exoplayer.video.*;
import androidx.media3.ui.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.drinkless.tdlib.TdApi;
import org.json.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity implements ChannelSession.Listener {
  ExoPlayer player;
  PlayerView view;
  FrameLayout root;
  LinearLayout top;
  TextView info, status;
  BufferPulseView bufferPulse;
  static java.lang.ref.WeakReference<PlayerActivity> warm=new java.lang.ref.WeakReference<>(null);
  final Runnable expireWarm=()->{if(!this.foreground){release();stopped=true;}};
  static void discardWarmPlayer(){PlayerActivity p=warm.get();if(p!=null&&!p.foreground){p.release();p.stopped=true;p.resumePosition=0;p.resumePlaying=true;p.subtitleUri=null;p.subtitleMime=null;p.appliedAutoSubtitle="";}warm.clear();}
  int uiColor=Ui.MENU_COLORS[0];
  final Handler ui = new Handler(Looper.getMainLooper());
  final ArrayList<Movie> items = new ArrayList<>();
  int index, boost = 100, fontSize = 24;
  boolean hwPlus, showInfo;
  final TvAudioProcessor voiceProcessor=new TvAudioProcessor();
  TvPlayerTools tvTools;
  float ratioOverride;
  volatile long subtitleDelayMs;
  int aspect = AspectRatioFrameLayout.RESIZE_MODE_FIT;
  String decoder = "Hardware";
  Uri subtitleUri;
  String subtitleMime;
  final BoostProcessor boostProcessor=new BoostProcessor();
  BluetoothControls bluetooth;
  android.content.SharedPreferences prefs;
  long resumePosition;
  boolean resumePlaying = true;
  TelegramBridge tg;
  boolean stopped;
  int generation;
  StreamStats streamStats = new StreamStats();
  ChannelSession session;
  String appliedAutoSubtitle="";
  boolean stereo,codecAsync,firstFrameSeen;
  final PlaybackWatchdog playbackWatchdog=new PlaybackWatchdog();
  long lastFrameOffsetUs;int lastFrameOffsetCount;
  AdaptiveLoadControl activeLoadControl;
  String audioDecoder="";
  volatile boolean pipelineSnapshotPending;
  int aspectChoice, subtitleOffset, retryCount;
  long bufferingSince, readySince, bufferingBytes, bufferingReadBytes;
  AlertDialog playbackErrorDialog;
  final String[] aspectNames = {"Fit", "Fill / stretch", "Crop / zoom", "16:9", "4:3", "2.35:1", "Original size", "21:9", "1:1"};
  final Runnable retryTask = () -> recoverPlayback(false);
  boolean foreground=true, controlsVisible=true;

  Movie movie() {
    return items.get(index);
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);BetaLog.init(this);
    Ui.full(this);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    prefs = getSharedPreferences("player", 0);
    tg = TelegramBridge.getInstance(this);
    session=ChannelSession.get(this);
    bluetooth=new BluetoothControls(this,()->{if(player!=null&&player.getPlayWhenReady()){player.pause();resumePlaying=false;}});
    hwPlus = false;
    prefs.edit().remove("brightness").remove("contrast").remove("saturation").remove("audioeffect").putBoolean("hwplus",false).apply();
    if(Build.VERSION.SDK_INT<=25&&!prefs.getBoolean("projectorDirectSurfaceV4",false)){
      hwPlus=false;prefs.edit().putBoolean("hwplus",false).putBoolean("projectorDirectSurfaceV4",true).apply();
    }
    // Ignore the Beta4 watchdog's persisted sync-codec flag. That watchdog could
    // mistake an ordinary seek rebuffer for a renderer freeze and poison later starts.
    codecAsync=prefs.getBoolean("codecAsyncFallbackV2",PlaybackPolicy.defaultAsyncCodec(Build.VERSION.SDK_INT));
    boost = prefs.getInt("boost", 100);
    boost = Math.max(100, Math.min(300, boost));
    stereo = prefs.getBoolean("stereo", true);
    subtitleOffset = prefs.getInt("suboffset", 0);
    fontSize = prefs.getInt("font", 24);
    showInfo = prefs.getBoolean("info", false);
    try {
      JSONArray a = new JSONArray(getIntent().getStringExtra("movies"));
      for (int i = 0; i < a.length(); i++) items.add(Movie.from(a.getJSONObject(i)));
      index = Math.max(0, Math.min(items.size() - 1, getIntent().getIntExtra("index", 0)));
    } catch (Exception e) {
      finish();
      return;
    }
    if (items.isEmpty()) {
      finish();
      return;
    }
    if(session.movie!=null&&session.movie.cacheIdentity().equals(movie().cacheIdentity()))streamStats=session.stats;
    resumePosition = getIntent().getBooleanExtra("startFromBeginning",false)?0:movie().savedPosition(this);
    autoSubtitleBeforeStart();
    restoreMovieSettings();
    if(b != null) {
      resumePosition=b.getLong("position",resumePosition);
      resumePlaying=b.getBoolean("playing",true);
      String sub=b.getString("subtitle");
      if(sub!=null) { subtitleUri=Uri.parse(sub);subtitleMime=b.getString("subtitleMime"); }
    }
    tvTools=new TvPlayerTools(this);
    start();
  }

  String positionKey() {
    return "pos:" + movie().cacheIdentity();
  }

  String movieKey(String name) { return name + ":" + movie().cacheIdentity(); }

  void restoreMovieSettings() {
    aspectChoice = Math.max(0, Math.min(8, prefs.getInt(movieKey("aspect"), 0)));
    subtitleDelayMs = prefs.getLong(movieKey("subdelay"), 0);
    selectAspect(aspectChoice, false);
  }

  static DefaultAudioTrackBufferSizeProvider audioBufferProvider(int sdk){
    // Reserve a bounded PCM queue against short CPU scheduling stalls on TVs.
    return new DefaultAudioTrackBufferSizeProvider.Builder()
        .setMinPcmBufferDurationUs(500000).setMaxPcmBufferDurationUs(1000000).build();
  }

  void start() {
    BetaLog.event("player_create","optimizedBuild="+(!BuildConfig.DEBUG)+" resumeMs="+resumePosition+" warm="+(player!=null));
    stopped = false;streamStats.paused=false;
    streamStats.setPlaybackPosition(resumePosition,Math.max(0,movie().duration)*1000L,movie().size);
    generation++;
    bufferingSince = readySince = 0;firstFrameSeen=false;audioDecoder="";
    playbackWatchdog.reset(SystemClock.elapsedRealtime(),resumePosition,-1);
    final File cacheRoot=MediaCache.root(this);final String keepKey=MediaCache.key(movie().cacheIdentity());
    MediaCache.activate(keepKey);
    Thread cacheTrim=new Thread(() -> MediaCache.keepOnly(cacheRoot,keepKey),"Trim old video cache");cacheTrim.setDaemon(true);cacheTrim.start();
    uiColor=Ui.nextMenuColor(this);
    controlsVisible=true;
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    view =
        (PlayerView)
            getLayoutInflater()
                .inflate(R.layout.player_surface, root, false);
    root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setPadding(Ui.dp(this,10), Ui.dp(this,4), Ui.dp(this,10), Ui.dp(this,4));
    top.setBackground(null);
    TextView title = Ui.text(this, movie().title, 17, Color.WHITE);
    title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    title.setMaxLines(1);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setContentDescription("Now playing: "+movie().title);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    addTop("CC", "Subtitles", this::subtitleMenu);
    addTop("♫", "Audio and dialogue", () -> tvTools.audioMenu());
    addTop("⋮", "More playback options", () -> tvTools.menu());
    addTop("▣", "Display and aspect ratio", this::cycleAspect);
    top.getChildAt(top.getChildCount()-1).setOnLongClickListener(v -> { aspectRatio(); return true; });
    FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    root.addView(top, tp);
    focusLabel=Ui.text(this,"",12,Color.WHITE);focusLabel.setBackgroundColor(0xcc08111d);focusLabel.setMaxLines(1);focusLabel.setVisibility(View.GONE);FrameLayout.LayoutParams flp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);flp.topMargin=Ui.dp(this,48);flp.rightMargin=Ui.dp(this,12);root.addView(focusLabel,flp);
    seekFeedback=Ui.text(this,"",21,Color.WHITE);seekFeedback.setGravity(Gravity.CENTER);seekFeedback.setBackgroundColor(0xcc08111d);seekFeedback.setVisibility(View.GONE);root.addView(seekFeedback,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
    bindPlaybackControls();
    status = Ui.text(this, "", 13, Color.WHITE);
    status.setGravity(Gravity.LEFT);
    status.setMaxWidth(Math.min(Ui.dp(this,360),(int)(getResources().getDisplayMetrics().widthPixels*.55f)));
    status.setBackground(null);
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM|Gravity.RIGHT);
    sp.bottomMargin=Ui.dp(this,62);sp.rightMargin=Ui.dp(this,12);
    root.addView(status, sp);
    status.setVisibility(View.GONE);
    view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
    bufferPulse=new BufferPulseView(this,uiColor);
    root.addView(bufferPulse,new FrameLayout.LayoutParams(Ui.dp(this,80),Ui.dp(this,32),Gravity.CENTER));
    bufferPulse.setVisibility(View.GONE);
    info = Ui.text(this, "", 14, Color.WHITE);
    info.setBackground(null);
    FrameLayout.LayoutParams ip =
        new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
    ip.bottomMargin = Ui.dp(this, 62);ip.rightMargin=Ui.dp(this,12);
    root.addView(info, ip);info.setVisibility(View.GONE);
    setContentView(root);
    DefaultRenderersFactory factory =
        new DefaultRenderersFactory(this) {
          @Override
          protected AudioSink buildAudioSink(Context context, boolean floatOutput, boolean playbackParams) {
            boostProcessor.percent=boost;
            voiceProcessor.mode=prefs.getInt("tvAudioMode",1);voiceProcessor.percent=boost;voiceProcessor.fold=stereo;
            StereoProcessor mixer = new StereoProcessor();
            // No HDMI passthrough in Stereo mode: decoded PCM must pass through the mixer.
            BetaLog.event("audio_processor_chain","stereo="+stereo+" order="+(stereo?"channel_mix,linear_boost":"linear_boost")+" boostPercent="+boost+" boostPolicy=linear_gain_with_sample_clipping pcmAccess=bulk stereoIdentity=bypass");
            return AudioTimingSink.wrap(new DefaultAudioSink.Builder()
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setEnableFloatOutput(false)
                .setAudioTrackBufferSizeProvider(audioBufferProvider(Build.VERSION.SDK_INT))
                .setAudioProcessors(prefs.getInt("tvAudioMode",1)==0?(stereo?new AudioProcessor[]{mixer,boostProcessor}:new AudioProcessor[]{boostProcessor}):new AudioProcessor[]{voiceProcessor}).build());
          }
          @Override
          public Renderer[] createRenderers(
              Handler h,
              VideoRendererEventListener v,
              AudioRendererEventListener a,
              TextOutput t,
              androidx.media3.exoplayer.metadata.MetadataOutput m) {
            Renderer[] renderers = super.createRenderers(h, v, a, t, m);
            for (int i = 0; i < renderers.length; i++) {
              Renderer target = renderers[i];
              if (target.getTrackType() == C.TRACK_TYPE_TEXT)
                renderers[i] =
                    (Renderer)
                        Proxy.newProxyInstance(
                            Renderer.class.getClassLoader(),
                            new Class[] {Renderer.class},
                            (proxy, method, args) -> {
                              Object[] shifted = args;
                              if (args != null
                                  && (method.getName().equals("render")
                                      || method.getName().equals("resetPosition"))
                                  && args[0] instanceof Long) {
                                shifted = args.clone();
                                shifted[0] = Math.max(0L, (Long) args[0] - subtitleDelayMs * 1000L);
                              }
                              try {
                                return method.invoke(target, shifted);
                              } catch (InvocationTargetException e) {
                                throw e.getCause();
                              }
                            });
            }
            return renderers;
          }
        };
    factory
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)
        .setMediaCodecSelector(
            (mime, secure, tunnel) -> {
              List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> found =
                  MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunnel);
              for(androidx.media3.exoplayer.mediacodec.MediaCodecInfo c:found)BetaLog.event("codec_candidate","mime="+mime+" name="+c.name+" hardware="+c.hardwareAccelerated);
              if (!mime.startsWith("video/")) return found;
              ArrayList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> hardware =
                  new ArrayList<>();
              for (androidx.media3.exoplayer.mediacodec.MediaCodecInfo codec : found)
                if (codec.hardwareAccelerated) hardware.add(codec);
              if(prefs.getBoolean("tvPreferSoftware",false)){ArrayList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> sw=new ArrayList<>();for(androidx.media3.exoplayer.mediacodec.MediaCodecInfo codec:found)if(!codec.hardwareAccelerated)sw.add(codec);return sw.isEmpty()?found:sw;}
              return hardware.isEmpty()?found:hardware;
            });
    if(codecAsync)factory.forceEnableMediaCodecAsynchronousQueueing();
    else factory.forceDisableMediaCodecAsynchronousQueueing();
    BetaLog.event("playback_config","surface="+(hwPlus?"TextureView":"SurfaceView")+" asyncCodec="+codecAsync+" hwPlus="+hwPlus+" boost="+boost+" boostGain="+(boost/100f)+" boostPolicy=linear_gain_with_sample_clipping stereo="+stereo+" videoEffects=disabled"+" speed=1.0");
    ActivityManager memory=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo available=new ActivityManager.MemoryInfo();
    if(memory!=null)memory.getMemoryInfo(available);
    int memoryBudget=PlaybackPolicy.memoryBudget(memory==null?64:memory.getMemoryClass(),available.availMem,available.lowMemory);
    if(Build.VERSION.SDK_INT<=25)memoryBudget=Math.min(memoryBudget,24*1024*1024);
    BetaLog.event("sample_budget","bytes="+memoryBudget);
    BetaLog.event("playback_policy","api="+Build.VERSION.SDK_INT+" asyncCodec="+codecAsync+" audioTrackBuffer=pcm_500_to_1000ms"+" stereoMixNormalization=rms"+" boostProcessing=linear_pcm_clip");
    AdaptiveLoadControl load = new AdaptiveLoadControl(streamStats,memoryBudget);activeLoadControl=load;
    DefaultMediaSourceFactory sources =
        new DefaultMediaSourceFactory(new TelegramDataSource.Factory(this, streamStats),new IndexedExtractors(session,movie().cacheIdentity()))
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6));
    player =
        new ExoPlayer.Builder(this, factory)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setMediaSourceFactory(sources)
            .setLoadControl(load)
            .build();
    applyLanguagePreferences();
    player.setPlaybackSpeed(prefs.getFloat("tvSpeed",1f));
    player.setSkipSilenceEnabled(prefs.getBoolean("tvSkipSilence",false));
    player.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
        true);
    view.setPlayer(player);
    applyAspect();
    view.setShowNextButton(false);
    view.setShowPreviousButton(false);
    view.setControllerAnimationEnabled(true);
    TvMotion.enter(top,0);
    view.setControllerAutoShow(false);
    view.setControllerHideOnTouch(false);
    view.setControllerShowTimeoutMs(0);
    view.setControllerVisibilityListener(
        (PlayerView.ControllerVisibilityListener)
            visibility -> {
              if(visibility==View.VISIBLE && !controlsVisible)refreshBorderColors();
              controlsVisible=visibility==View.VISIBLE;
              top.setVisibility(visibility);if(visibility!=View.VISIBLE&&focusLabel!=null)focusLabel.setVisibility(View.GONE);
              if(visibility==View.VISIBLE){restoreRemoteFocus();armControlsHide();}else ui.removeCallbacks(hideControls);
            });
    player.addListener(
        new Player.Listener() {
          @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){
            if(tvTools!=null)tvTools.seeked(oldPosition.positionMs,newPosition.positionMs,reason);
            if(reason==Player.DISCONTINUITY_REASON_SEEK&&firstFrameSeen&&Math.abs(newPosition.positionMs-oldPosition.positionMs)>500)showSeekFeedback(newPosition.positionMs-oldPosition.positionMs,newPosition.positionMs);
            playbackWatchdog.reset(SystemClock.elapsedRealtime(),newPosition.positionMs,-1);
            BetaLog.event("position_discontinuity","reason="+reason+" fromMs="+oldPosition.positionMs+" toMs="+newPosition.positionMs);
          }
          @Override public void onPlayWhenReadyChanged(boolean ready,int reason){BetaLog.event("play_intent","ready="+ready+" reason="+reason);if(focusLabel!=null&&root.findFocus()!=null)showFocusLabel(root.findFocus(),true);}
          @Override public void onPlaybackSuppressionReasonChanged(int reason){BetaLog.event("play_suppressed","reason="+reason);}
          @Override
          public void onPlaybackStateChanged(int state) {
            BetaLog.event("player_state","state="+state+" positionMs="+player.getCurrentPosition()+" bufferedMs="+player.getTotalBufferedDuration()+" playWhenReady="+player.getPlayWhenReady());
            if (state == Player.STATE_BUFFERING) {
              if (bufferingSince == 0) {
                bufferingSince = SystemClock.elapsedRealtime();bufferingBytes=streamStats.transferredBytes();bufferingReadBytes=streamStats.cache==null?0:streamStats.cache.readBytes();
                if (readySince > 0 && player.getPlayWhenReady()) streamStats.stalls++;
                readySince=0;
              }
              status.setVisibility(View.GONE);
              bufferPulse.setVisibility(View.VISIBLE);
            } else if (state == Player.STATE_READY) {
              bufferPulse.setVisibility(View.GONE);
              bufferingSince = 0;
              if (readySince == 0) readySince = SystemClock.elapsedRealtime();
              ui.removeCallbacks(retryTask);
              status.setVisibility(View.GONE);
              styleSubtitles();
              applyAspect();
            } else if (state == Player.STATE_ENDED) {
              savePosition();if(tvTools!=null)tvTools.ended();

            }
          }

          @Override
          public void onPlayerError(PlaybackException e) {
            BetaLog.event("player_error","code="+e.getErrorCodeName()+" errorCode="+e.errorCode+" generation="+generation+" retryCount="+retryCount+" positionMs="+player.getCurrentPosition()+" bufferedMs="+player.getTotalBufferedDuration()+" state="+player.getPlaybackState()+" playWhenReady="+player.getPlayWhenReady());
            BetaLog.exception("player_error",e);
            if(e instanceof ExoPlaybackException){ExoPlaybackException x=(ExoPlaybackException)e;BetaLog.event("error_renderer","type="+x.type+" name="+x.rendererName+" index="+x.rendererIndex+" formatSupport="+x.rendererFormatSupport);BetaLog.format("error_format",x.rendererFormat);}
            logPipeline();BetaLog.device();
            if(codecAsync&&(e.errorCode==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||e.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED)&&retryCount<PlaybackPolicy.MAX_RETRIES){
              ExoPlayer failedPlayer=player;
              ui.post(()->{if(player!=failedPlayer||!foreground)return;resumePosition=player.getCurrentPosition();resumePlaying=player.getPlayWhenReady();retryCount++;codecAsync=false;prefs.edit().putBoolean("codecAsyncFallbackV2",false).apply();BetaLog.event("codec_fallback","from=async to=sync reason=codec_error");release();start();});return;
            }
            if (isRecoverable(e) && retryCount < PlaybackPolicy.MAX_RETRIES) {
              scheduleRetry();
              return;
            }
            showPlaybackError(e);
          }

          @Override public void onTracksChanged(Tracks tracks){
            if(!BetaLog.active())return;for(Tracks.Group group:tracks.getGroups())for(int i=0;i<group.length;i++)if(group.isTrackSelected(i)){Format f=group.getTrackFormat(i);BetaLog.event("selected_format","mime="+f.sampleMimeType+" codecs="+f.codecs+" size="+f.width+"x"+f.height+" fps="+f.frameRate+" channels="+f.channelCount+" sampleRate="+f.sampleRate+" bitrate="+f.bitrate);}
          }

          @Override public void onVideoSizeChanged(VideoSize size) { applyAspect(); }

          @Override public void onCues(CueGroup group) { moveSubtitles(group.cues); }

          void showPlaybackError(PlaybackException e) {
            bufferPulse.setVisibility(View.GONE);
            status.setText("Playback stopped");
            status.setVisibility(View.VISIBLE);
            String message =
                e.getErrorCodeName()
                    + "\n"
                    + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            if (playbackErrorDialog != null && playbackErrorDialog.isShowing()) return;
            playbackErrorDialog = Ui.dialog(PlayerActivity.this)
                .setTitle("Playback needs attention")
                .setMessage(
                    message + "\nIf this codec has no hardware decoder, use an H.264 8-bit video.")
                .setPositiveButton(
                    "Retry",
                    (d, w) -> {
                      retryCount = 0;
                      recoverPlayback(true);
                    })
                .setNegativeButton("Back", (d, w) -> finish())
                .show();
          }
        });
    player.addAnalyticsListener(
        new AnalyticsListener() {
          @Override public void onAudioInputFormatChanged(EventTime t,Format f,androidx.media3.exoplayer.DecoderReuseEvaluation reuse){BetaLog.format("audio_input",f);if(reuse!=null)BetaLog.event("audio_reuse","result="+reuse.result+" discardReasons="+reuse.discardReasons);}
          @Override public void onVideoInputFormatChanged(EventTime t,Format f,androidx.media3.exoplayer.DecoderReuseEvaluation reuse){BetaLog.format("video_input",f);if(reuse!=null)BetaLog.event("video_reuse","result="+reuse.result+" discardReasons="+reuse.discardReasons);}
          @Override public void onLoadError(EventTime t,LoadEventInfo info,MediaLoadData data,IOException error,boolean canceled){BetaLog.event("load_error","type="+data.dataType+" trackType="+data.trackType+" loadedBytes="+info.bytesLoaded+" durationMs="+info.loadDurationMs+" canceled="+canceled);BetaLog.exception("source_load",error);}
          @Override public void onSurfaceSizeChanged(EventTime t,int width,int height){BetaLog.event("surface_size","width="+width+" height="+height);}
          @Override public void onAudioEnabled(EventTime t,DecoderCounters counters){BetaLog.event("audio_enabled","generation="+generation);}
          @Override public void onVideoEnabled(EventTime t,DecoderCounters counters){BetaLog.event("video_enabled","generation="+generation);}
          @Override public void onAudioDisabled(EventTime t,DecoderCounters counters){counters.ensureUpdated();BetaLog.event("audio_disabled","queued="+counters.queuedInputBufferCount+" rendered="+counters.renderedOutputBufferCount+" skipped="+counters.skippedOutputBufferCount);}
          @Override public void onVideoDisabled(EventTime t,DecoderCounters counters){counters.ensureUpdated();BetaLog.event("video_disabled","queued="+counters.queuedInputBufferCount+" rendered="+counters.renderedOutputBufferCount+" dropped="+counters.droppedBufferCount);}
          @Override public void onAudioDecoderInitialized(EventTime t,String name,long started,long duration){audioDecoder=name;BetaLog.event("audio_decoder","name="+name+" initMs="+duration);}
          @Override public void onRenderedFirstFrame(EventTime t,Object output,long renderTimeMs){firstFrameSeen=true;BetaLog.event("first_frame","renderTimeMs="+renderTimeMs+" positionMs="+player.getCurrentPosition());}
          @Override public void onDroppedVideoFrames(EventTime t,int count,long elapsedMs){BetaLog.event("dropped_frames","count="+count+" elapsedMs="+elapsedMs+" positionMs="+(player==null?-1:player.getCurrentPosition())+" bufferedMs="+(player==null?-1:player.getTotalBufferedDuration())+" cpuPercent="+BetaLog.lastCpuPercent);}
          @Override public void onAudioUnderrun(EventTime t,int size,long duration,long elapsed){Format f=player==null?null:player.getAudioFormat();BetaLog.event("audio_underrun","bufferBytes="+size+" bufferMs="+duration+" sinceFeedMs="+elapsed+" positionMs="+(player==null?-1:player.getCurrentPosition())+" bufferedMs="+(player==null?-1:player.getTotalBufferedDuration())+" boostPercent="+boost+" audioChannels="+(f==null?-1:f.channelCount)+" audioRate="+(f==null?-1:f.sampleRate));}
          @Override public void onVideoFrameProcessingOffset(EventTime t,long total,int count){lastFrameOffsetUs=total;lastFrameOffsetCount=count;}
          @Override public void onAudioPositionAdvancing(EventTime t,long started){BetaLog.event("audio_advancing","startedMs="+started);}
          @Override public void onAudioSinkError(EventTime t,Exception error){BetaLog.exception("audio_sink_error",error);}
          @Override public void onAudioCodecError(EventTime t,Exception error){BetaLog.exception("audio_codec_error",error);}
          @Override public void onVideoCodecError(EventTime t,Exception error){BetaLog.exception("video_codec_error",error);}
          @Override public void onAudioTrackInitialized(EventTime t,AudioSink.AudioTrackConfig c){BetaLog.event("audio_track","encoding="+c.encoding+" sampleRate="+c.sampleRate+" channelConfig="+c.channelConfig+" bufferBytes="+c.bufferSize+" tunneling="+c.tunneling+" offload="+c.offload+" boostPercent="+boost+" boostGain="+(boost/100f));BetaLog.device();}
          @Override public void onAudioSessionIdChanged(EventTime t, int id) {BetaLog.event("audio_session_ready","boostPercent="+boost+" boostGain="+(boost/100f));applyBoost(id);}

          @Override
          public void onVideoDecoderInitialized(
              EventTime t, String name, long started, long duration) {
            decoder = name;BetaLog.event("video_decoder","name="+name+" initMs="+duration);
          }
        });
    try {
      setMedia();
      player.seekTo(resumePosition);
      player.prepare();
      player.setPlayWhenReady(resumePlaying);
    } catch (Exception e) {
      BetaLog.exception("player_prepare_error",e);
      Ui.message(this, "Player", e.getMessage());
    }
    ui.post(ticker);
    showRemoteControls();
  }

  void addTop(String text, String label, Runnable r) {
    Button b = Ui.roundButton(this, text, label, r);
    b.setId(View.generateViewId());
    b.setTextSize(14);b.setBackground(Ui.playerCircle(this,false));
    b.setOnFocusChangeListener((v,f) -> {
      v.setBackground(Ui.playerCircle(this,f));((Button)v).setTextColor(f?0xff101820:Color.WHITE);
      v.setScaleX(f?1.15f:1f);v.setScaleY(f?1.15f:1f);if(f)lastControlFocus=v.getId();showFocusLabel(v,f);armControlsHide();
    });
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
    p.leftMargin = Ui.dp(this, 8);
    top.addView(b, p);
  }

  void importSubtitle() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
    try { startActivityForResult(i, 12); }
    catch (Exception e) { Ui.message(this, "Subtitles", "A document provider/file manager is required."); }
  }

  void subtitleMenu() {
    choose("Subtitles", new String[]{"Tracks / Off", "Open subtitle file", "Find in Telegram", "Timing: " + subtitleDelayMs + " ms", "Appearance", "Position up / down"}, n -> {
      if(n == 0) tracks(C.TRACK_TYPE_TEXT);
      else if(n == 1) importSubtitle();
      else if(n == 2) telegramSubtitles();
      else if(n == 3) subtitleSync();
      else if(n == 4) subtitleStyle();
      else subtitlePosition();
    });
  }

  void audioMenu() { tracks(C.TRACK_TYPE_AUDIO); }

  void bindPlaybackControls() {
    for(int id:new int[]{androidx.media3.ui.R.id.exo_rew,androidx.media3.ui.R.id.exo_play_pause,androidx.media3.ui.R.id.exo_ffwd}){
      View button=view.findViewById(id);
      if(button!=null)button.setOnFocusChangeListener((v,focused)->{styleTransport(v,focused);if(focused)lastControlFocus=v.getId();showFocusLabel(v,focused);armControlsHide();});
    }
    configureRemoteNavigation();tintPlaybackControls();
  }
  void tintPlaybackControls() {
    View panel=view.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);if(panel!=null)panel.setBackground(null);
    for(int id:new int[]{androidx.media3.ui.R.id.exo_rew,androidx.media3.ui.R.id.exo_play_pause,androidx.media3.ui.R.id.exo_ffwd}){
      View button=view.findViewById(id);if(button!=null)styleTransport(button,button.hasFocus());
    }
    View bar=view.findViewById(androidx.media3.ui.R.id.exo_progress);
    if(bar instanceof DefaultTimeBar){DefaultTimeBar timeline=(DefaultTimeBar)bar;timeline.setPlayedColor(0xff66b7c5);timeline.setScrubberColor(0xff66b7c5);timeline.setBufferedColor(0xff68717a);timeline.setUnplayedColor(0xff323840);}
  }

  void refreshBorderColors() {
    uiColor=Ui.nextMenuColor(this);
    if(bufferPulse!=null)bufferPulse.setColor(uiColor);
    top.setBackground(null);status.setBackground(null);
    info.setBackground(null);
    for(int i=1;i<top.getChildCount();i++)top.getChildAt(i).setBackground(Ui.playerCircle(this,top.getChildAt(i).hasFocus()));
    tintPlaybackControls();
  }

  void applyAspect() {
    if(view == null) return;
    view.setResizeMode(aspect);
    if(ratioOverride > 0) {
      View content=view.findViewById(androidx.media3.ui.R.id.exo_content_frame);
      if(content instanceof AspectRatioFrameLayout) ((AspectRatioFrameLayout)content).setAspectRatio(ratioOverride);
    }
    View c=view.findViewById(androidx.media3.ui.R.id.exo_content_frame);
    if(c != null) {
      FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)c.getLayoutParams();
      if(aspectChoice==6 && player!=null && player.getVideoSize().width>0 && root.getWidth()>0) {
        VideoSize v=player.getVideoSize();
        p.width=Math.min(root.getWidth(),v.width);p.height=Math.min(root.getHeight(),v.height);
        p.gravity=Gravity.CENTER;
      } else {p.width=-1;p.height=-1;}
      c.setLayoutParams(p);
    }
  }

  void aspectRatio() {
    choose("Aspect ratio", aspectNames, n -> selectAspect(n, true));
  }

  void cycleAspect() { selectAspect((aspectChoice+1)%aspectNames.length, true); }

  void selectAspect(int choice, boolean save) {
    aspectChoice=choice;
    ratioOverride=choice==3 ? 16f/9 : choice==4 ? 4f/3 : choice==5 ? 2.35f : choice==7 ? 21f/9 : choice==8 ? 1f : 0;
    aspect=choice==1 ? AspectRatioFrameLayout.RESIZE_MODE_FILL : choice==2 ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT;
    applyAspect();
    if(save) {
      prefs.edit().putInt(movieKey("aspect"),choice).apply();
      Toast.makeText(this,"Aspect: " + aspectNames[choice],Toast.LENGTH_SHORT).show();
    }
  }

  void autoSubtitleBeforeStart(){if(prefs.getBoolean(movieKey("externalSubtitleOff"),false))return;String manual=prefs.getString(movieKey("externalSubtitleUri"),"");if(!manual.isEmpty()){subtitleUri=Uri.parse(manual);subtitleMime=prefs.getString(movieKey("externalSubtitleMime"),"application/x-subrip");return;}if(session==null||session.movie==null||!session.movie.cacheIdentity().equals(movie().cacheIdentity())||session.subtitlePath.isEmpty())return;subtitleUri=Uri.fromFile(new File(session.subtitlePath));subtitleMime=session.subtitleType;appliedAutoSubtitle=session.subtitlePath;prefs.edit().putBoolean("subtitleOff",false).apply();}
  public void changed(){if(foreground&&session!=null&&session.movie!=null&&!session.movie.cacheIdentity().equals(movie().cacheIdentity())){savePosition();resumePlaying=false;release();stopped=true;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return;}if(!foreground||session==null||session.movie==null||!session.movie.cacheIdentity().equals(movie().cacheIdentity())||session.subtitlePath.isEmpty()||session.subtitlePath.equals(appliedAutoSubtitle)||prefs.getBoolean(movieKey("externalSubtitleOff"),false)||!prefs.getString(movieKey("externalSubtitleUri"),"").isEmpty())return;appliedAutoSubtitle=session.subtitlePath;applySubtitle(Uri.fromFile(new File(session.subtitlePath)),session.subtitleType,true);}

  void setMedia() {
    MediaItem.Builder item = new MediaItem.Builder().setUri(movie().uri).setMediaId(movie().uri);
    if (!movie().mime.isEmpty()) item.setMimeType(movie().mime);
    if (subtitleUri != null) {
      MediaItem.SubtitleConfiguration sc =
          new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
              .setMimeType(subtitleMime)
              .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
              .setLabel("Imported subtitle")
              .build();
      item.setSubtitleConfigurations(Collections.singletonList(sc));
    }
    player.setMediaItem(item.build());
  }

  void applyBoost(int id){boostProcessor.percent=boost;voiceProcessor.percent=boost;}

  void styleSubtitles() {
    SubtitleView sv = view.getSubtitleView();
    if (sv != null) {
      sv.setApplyEmbeddedStyles(prefs.getBoolean("embeddedstyle", true));
      sv.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
      int color = prefs.getInt("subcolor", Color.WHITE);
      sv.setStyle(
          new CaptionStyleCompat(
              color,
              Color.TRANSPARENT,
              Color.TRANSPARENT,
              CaptionStyleCompat.EDGE_TYPE_OUTLINE,
              Color.BLACK,
              null));
    }
  }

  void choose(String title, String[] labels, java.util.function.IntConsumer pick) {
    Ui.menu(this,title,labels,pick);
  }

  static String trackName(Format f,int index) {
    if(f.label!=null && !f.label.trim().isEmpty())return f.label;
    if(f.language!=null && !f.language.trim().isEmpty() && !"und".equals(f.language))return f.language;
    return "Track "+(index+1);
  }

  void tracks(int type) {
    ArrayList<String> labels = new ArrayList<>();
    ArrayList<Runnable> actions = new ArrayList<>();
    if (type == C.TRACK_TYPE_TEXT) {
      labels.add("Off");
      actions.add(
          () -> {
              prefs.edit().putBoolean(movieKey("externalSubtitleOff"),true).putBoolean("subtitleOff",true).apply();
              player.setTrackSelectionParameters(
                  player
                      .getTrackSelectionParameters()
                      .buildUpon()
                      .setTrackTypeDisabled(type, true)
                      .clearOverridesOfType(type)
                      .build());
          });
    }
    for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
      if (group.getType() != type) continue;
      for (int i = 0; i < group.length; i++) {
        final int track = i;
        Format f = group.getTrackFormat(i);
        String name = trackName(f, i);
        if(group.isTrackSelected(i)) name += " ✓";
        if (!group.isTrackSupported(i)) name += " (unsupported)";
        labels.add(name);
        actions.add(
            () -> {
                String language=f.language;
                if(language!=null && !language.isEmpty() && !"und".equals(language))
                  prefs.edit().putString(type==C.TRACK_TYPE_AUDIO ? "audioLanguage" : "subtitleLanguage",language).apply();
                if(type==C.TRACK_TYPE_TEXT) prefs.edit().putBoolean(movieKey("externalSubtitleOff"),false).putBoolean("subtitleOff",false).apply();
                player.setTrackSelectionParameters(
                    player
                        .getTrackSelectionParameters()
                        .buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .clearOverridesOfType(type)
                        .setOverrideForType(
                            new TrackSelectionOverride(group.getMediaTrackGroup(), track))
                        .build());
            });
      }
    }
    if (labels.isEmpty()) {
      Ui.message(this, "Tracks", "No supported tracks available yet.");
      return;
    }
    choose(
        type == C.TRACK_TYPE_AUDIO ? "Audio tracks" : "Subtitle tracks",
        labels.toArray(new String[0]),
        n -> actions.get(n).run());
  }

  void subtitleSync() {
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,16,24,16);
    TextView value=Ui.text(this,"",22,Color.WHITE);value.setGravity(Gravity.CENTER);box.addView(value);
    Runnable refresh=() -> value.setText(String.format(java.util.Locale.US,"%+.1f s",subtitleDelayMs/1000f));refresh.run();
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);box.addView(row);
    Button minus=Ui.roundButton(this,"−","Subtitles earlier by 100 milliseconds",() -> {subtitleDelayMs=Math.max(-60000,subtitleDelayMs-100);saveSubtitleDelay();refresh.run();player.seekTo(player.getCurrentPosition());});
    Button plus=Ui.roundButton(this,"+","Subtitles later by 100 milliseconds",() -> {subtitleDelayMs=Math.min(60000,subtitleDelayMs+100);saveSubtitleDelay();refresh.run();player.seekTo(player.getCurrentPosition());});
    LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(Ui.dp(this,64),Ui.dp(this,64));bp.setMargins(12,8,12,8);row.addView(minus,bp);row.addView(plus,new LinearLayout.LayoutParams(bp));
    Ui.dialog(this).setTitle("Subtitle timing • + means later").setView(box).setPositiveButton("Done",null).show();
  }

  void saveSubtitleDelay() { prefs.edit().putLong(movieKey("subdelay"),subtitleDelayMs).apply(); }

  void subtitlePosition() {
    choose("Subtitle position",new String[]{"Move up", "Move down", "Reset original position"}, n -> {
      subtitleOffset=n==2 ? 0 : Math.max(-75,Math.min(5,subtitleOffset+(n==0 ? -5 : 5)));
      prefs.edit().putInt("suboffset",subtitleOffset).apply();
      moveSubtitles(player.getCurrentCues().cues);
      Toast.makeText(this,"Subtitle offset: " + subtitleOffset + "%",Toast.LENGTH_SHORT).show();
    });
  }

  void moveSubtitles(List<Cue> cues) {
    SubtitleView sv=view.getSubtitleView();if(sv==null) return;
    if(subtitleOffset==0) {sv.setCues(cues);return;}
    ArrayList<Cue> moved=new ArrayList<>();
    for(Cue cue:cues) {
      float base=cue.line!=Cue.DIMEN_UNSET && cue.lineType==Cue.LINE_TYPE_FRACTION ? cue.line : .9f;
      moved.add(cue.buildUpon().setLine(Math.max(.05f,Math.min(.95f,base+subtitleOffset/100f)),Cue.LINE_TYPE_FRACTION)
          .setLineAnchor(cue.lineAnchor==Cue.TYPE_UNSET ? Cue.ANCHOR_TYPE_END : cue.lineAnchor).build());
    }
    sv.setCues(moved);
  }

  void applyLanguagePreferences() {
    TrackSelectionParameters.Builder b=player.getTrackSelectionParameters().buildUpon();
    String audio=prefs.getString("audioLanguage",""),sub=prefs.getString("subtitleLanguage","");
    if(!audio.isEmpty()) b.setPreferredAudioLanguage(audio);
    if(!sub.isEmpty()) b.setPreferredTextLanguage(sub);
    b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,prefs.getBoolean("subtitleOff",false));
    player.setTrackSelectionParameters(b.build());
  }

  boolean isRecoverable(PlaybackException e) {
    return e.errorCode==PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        || e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        || e.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        || e.errorCode==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        || e.errorCode==PlaybackException.ERROR_CODE_TIMEOUT;
  }

  void scheduleRetry() {
    if(player==null || isFinishing() || retryCount>=PlaybackPolicy.MAX_RETRIES) return;
    retryCount++;readySince=0;
    ui.removeCallbacks(retryTask);
    status.setVisibility(View.GONE);bufferPulse.setVisibility(View.VISIBLE);
    ui.postDelayed(retryTask,PlaybackPolicy.retryDelayMs(retryCount));
  }

  void recoverPlayback(boolean play) {
    if(player==null || isFinishing()) return;
    long pos=player.getCurrentPosition();boolean running=play || player.getPlayWhenReady();
    BetaLog.event("retry","attempt="+retryCount+" positionMs="+pos+" play="+running+" asyncCodec="+codecAsync);
    player.stop();player.seekTo(pos);player.prepare();player.setPlayWhenReady(running);
    bufferingSince=SystemClock.elapsedRealtime();readySince=0;
  }

  void subtitleStyle() {
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
    TextView preview=Ui.text(this,"Subtitles look like this",fontSize,prefs.getInt("subcolor",Color.WHITE));preview.setTag("subtitle-preview");preview.setGravity(Gravity.CENTER);preview.setBackgroundColor(0xff050a10);preview.setShadowLayer(2,0,1,Color.BLACK);box.addView(preview,new LinearLayout.LayoutParams(-1,Ui.dp(this,90)));
    Runnable update=()->{preview.setTextSize(fontSize);preview.setTextColor(prefs.getInt("subcolor",Color.WHITE));styleSubtitles();};
    LinearLayout sizes=new LinearLayout(this);Button minus=Ui.button(this,"A−",()->{fontSize=Math.max(14,fontSize-2);prefs.edit().putInt("font",fontSize).apply();update.run();});minus.setContentDescription("Smaller subtitle text");Button plus=Ui.button(this,"A+",()->{fontSize=Math.min(40,fontSize+2);prefs.edit().putInt("font",fontSize).apply();update.run();});plus.setContentDescription("Larger subtitle text");sizes.addView(minus,new LinearLayout.LayoutParams(0,-2,1));sizes.addView(plus,new LinearLayout.LayoutParams(0,-2,1));Ui.add(box,sizes);
    String[] names={"White","Yellow","Cyan"};int[] colors={Color.WHITE,Color.YELLOW,Color.CYAN};LinearLayout palette=new LinearLayout(this);for(int i=0;i<colors.length;i++){final int n=i;Button choice=Ui.button(this,names[i],()->{prefs.edit().putInt("subcolor",colors[n]).apply();update.run();});choice.setTextSize(13);choice.setPadding(4,4,4,4);palette.addView(choice,new LinearLayout.LayoutParams(0,-2,1));}Ui.add(box,palette);
    Button embedded=Ui.button(this,"Embedded styling: "+(prefs.getBoolean("embeddedstyle",true)?"On":"Off"),()->{});embedded.setOnClickListener(v->{boolean next=!prefs.getBoolean("embeddedstyle",true);prefs.edit().putBoolean("embeddedstyle",next).apply();embedded.setText("Embedded styling: "+(next?"On":"Off"));update.run();});Ui.add(box,embedded);Ui.add(box,Ui.text(this,"Embedded styling keeps the subtitle file's own appearance.",12,Ui.MUTED));Ui.sideDialog(this,"Subtitle preview",box);minus.requestFocus();
  }

  static String subtitleMime(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    if (n.endsWith(".srt")) return MimeTypes.APPLICATION_SUBRIP;
    if (n.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
    if (n.endsWith(".ass") || n.endsWith(".ssa")) return MimeTypes.TEXT_SSA;
    return null;
  }

  void importSubtitle(Uri uri, String name) {
    String mime = subtitleMime(name);
    if (mime == null) {
      Ui.message(this, "Subtitle format", "Choose .srt, .vtt, .ass or .ssa.");
      return;
    }
    new Thread(
            () -> {
              try {
                File target =
                    new File(
                        getCacheDir(),
                        "imported-subtitle." + name.substring(name.lastIndexOf('.') + 1));
                try (InputStream in = getContentResolver().openInputStream(uri);
                    OutputStream out = new FileOutputStream(target)) {
                  if (in == null) throw new IOException("Cannot read subtitle");
                  byte[] bytes = new byte[8192];
                  int count, total = 0;
                  while ((count = in.read(bytes)) != -1) {
                    total += count;
                    if (total > 4 * 1024 * 1024)
                      throw new IOException("Subtitle is larger than 4 MB");
                    out.write(bytes, 0, count);
                  }
                }
                ui.post(() -> applySubtitle(Uri.fromFile(target), mime));
              } catch (Exception e) {
                ui.post(() -> Ui.message(this, "Subtitle import", e.getMessage()));
              }
            },
            "Subtitle import")
        .start();
  }

  void applySubtitle(Uri uri,String mime){applySubtitle(uri,mime,false);}
  void applySubtitle(Uri uri, String mime,boolean automatic) {
    if(!automatic)prefs.edit().putString(movieKey("externalSubtitleUri"),uri.toString()).putString(movieKey("externalSubtitleMime"),mime).putBoolean(movieKey("externalSubtitleOff"),false).apply();
    subtitleUri = uri;
    subtitleMime = mime;
    prefs.edit().putBoolean("subtitleOff",false).apply();
    if (player != null) {
      player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).clearOverridesOfType(C.TRACK_TYPE_TEXT).build());
      long pos = player.getCurrentPosition();
      boolean playing = player.getPlayWhenReady();
      setMedia();
      player.seekTo(pos);
      player.prepare();
      player.setPlayWhenReady(playing);
    }
    Toast.makeText(
            this, "Subtitle imported. Open subtitle tracks to change/off.", Toast.LENGTH_LONG)
        .show();
  }

  void telegramSubtitles() {
    if (!tg.ready || movie().chatId == 0) {
      Ui.message(
          this,
          "Telegram subtitles",
          "Open a Telegram movie to search subtitle documents in that chat.");
      return;
    }
    status.setText("Finding subtitle files…");
    status.setVisibility(View.VISIBLE);
    long chat = movie().chatId;
    int requestGeneration = generation;
    TdApi.Object filter;
    try {
      filter = TelegramBridge.obj("SearchMessagesFilterDocument");
    } catch (Exception e) {
      Ui.message(this, "Subtitles", e.getMessage());
      return;
    }
    tg.send(
        "SearchChatMessages",
        result ->
            ui.post(
                () -> {
                  if (isFinishing() || requestGeneration != generation) return;
                  status.setVisibility(View.GONE);
                  ArrayList<TdApi.Object> docs = new ArrayList<>();
                  Object raw = TelegramBridge.get(result, "messages");
                  if (raw instanceof TdApi.Message[])
                    for (TdApi.Message message : (TdApi.Message[]) raw) {
                      Object content = TelegramBridge.get(message, "content");
                      Object doc = TelegramBridge.get(content, "document");
                      if (doc instanceof TdApi.Object
                          && subtitleMime(TelegramBridge.str(doc, "fileName")) != null)
                        docs.add((TdApi.Object) doc);
                    }
                  docs.sort(
                      (a, b) ->
                          Integer.compare(
                              similarity(TelegramBridge.str(b, "fileName"), movie().title),
                              similarity(TelegramBridge.str(a, "fileName"), movie().title)));
                  if (docs.isEmpty()) {
                    Ui.message(
                        this,
                        "Subtitles",
                        "No supported subtitle files in the latest document results. You can import"
                            + " from device storage.");
                    return;
                  }
                  String[] labels = new String[docs.size()];
                  for (int i = 0; i < docs.size(); i++)
                    labels[i] = TelegramBridge.str(docs.get(i), "fileName");
                  choose(
                      "Telegram subtitles • closest filename first",
                      labels,
                      n -> {
                        TdApi.Object doc = docs.get(n);
                        Object f = TelegramBridge.get(doc, "document");
                        int id = (int) TelegramBridge.num(f, "id");
                        long bytes = TelegramBridge.num(f, "size");
                        if (bytes > 4 * 1024 * 1024) {
                          Ui.message(this, "Subtitle", "Subtitle is larger than 4 MB.");
                          return;
                        }
                        String name = TelegramBridge.str(doc, "fileName");
                        status.setText("Loading subtitle…");
                        status.setVisibility(View.VISIBLE);
                        new Thread(
                                () -> {
                                  try {
                                    TdApi.Object downloaded =
                                        tg.waitRequest(
                                            "DownloadFile",
                                            "fileId",
                                            id,
                                            "priority",
                                            16,
                                            "offset",
                                            0L,
                                            "limit",
                                            0L,
                                            "synchronous",
                                            true);
                                    String path =
                                        TelegramBridge.str(
                                            TelegramBridge.get(downloaded, "local"), "path");
                                    if (path.isEmpty())
                                      throw new IOException("Subtitle is not ready; retry.");
                                    File subtitleCopy=new File(getCacheDir(),"telegram-subtitle."+name.substring(name.lastIndexOf('.')+1));
                                    try(InputStream in=new FileInputStream(path);OutputStream out=new FileOutputStream(subtitleCopy)) {
                                      byte[] bytesBuffer=new byte[8192];int count,total=0;
                                      while((count=in.read(bytesBuffer))!=-1) {
                                        total+=count;if(total>4*1024*1024) throw new IOException("Subtitle is larger than 4 MB");
                                        out.write(bytesBuffer,0,count);
                                      }
                                    }
                                    ui.post(
                                        () -> {
                                          if (requestGeneration != generation) return;
                                          status.setVisibility(View.GONE);
                                          applySubtitle(
                                              Uri.fromFile(subtitleCopy), subtitleMime(name));
                                        });
                                  } catch (Exception e) {
                                    ui.post(
                                        () -> {
                                          status.setVisibility(View.GONE);
                                          Ui.message(this, "Subtitle", e.getMessage());
                                        });
                                  }
                                },
                                "Telegram subtitle")
                            .start();
                      });
                }),
        "chatId",
        chat,
        "topicId",
        null,
        "query",
        "",
        "senderId",
        null,
        "fromMessageId",
        0L,
        "offset",
        0,
        "limit",
        100,
        "filter",
        filter);
  }

  static int similarity(String a, String b) {
    String x = a.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " "),
        y = b.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
    int score = 0;
    for (String word : y.split(" "))
      if (word.length() > 2 && x.contains(word)) score += word.length();
    return score;
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 12 && result == RESULT_OK && data != null) {
      Uri uri = data.getData();
      String name = "";
      try (android.database.Cursor c =
          getContentResolver()
              .query(
                  uri,
                  new String[] {android.provider.OpenableColumns.DISPLAY_NAME},
                  null,
                  null,
                  null)) {
        if (c != null && c.moveToFirst()) name = c.getString(0);
      }
      importSubtitle(uri, name);
    }
  }

  final Runnable ticker =
      new Runnable() {
        int ticks;long lastBytes,lastSampleAt,lastTickAt;

        public void run() {
          if (player == null) return;
          if(tvTools!=null)tvTools.tick();
          Format format=player.getVideoFormat(),audioFormat=player.getAudioFormat();
          long bitrate=(format!=null && format.bitrate>0 ? format.bitrate : 0)
              +(audioFormat!=null && audioFormat.bitrate>0 ? audioFormat.bitrate : 0);
          long duration=player.getDuration();
          if(movie().size>0 && duration>0 && duration!=C.TIME_UNSET)bitrate=movie().size*8000/duration;
          else if(bitrate==0 && movie().size>0 && movie().duration>0)bitrate=movie().size*8/movie().duration;
          if(bitrate>0)streamStats.mediaBits=bitrate;
          long positionDuration=duration>0&&duration!=C.TIME_UNSET?duration:movie().duration*1000L;
          streamStats.setPlaybackPosition(player.getCurrentPosition(),positionDuration,movie().size);
          session.updatePosition(player.getCurrentPosition());
          long now=SystemClock.elapsedRealtime();
          if(lastTickAt>0&&now-lastTickAt>2000)BetaLog.event("ui_tick_delay","ms="+(now-lastTickAt));lastTickAt=now;
          DecoderCounters videoCounters=player.getVideoDecoderCounters();
          if(videoCounters!=null)videoCounters.ensureUpdated();
          long rendered=videoCounters==null?-1:videoCounters.renderedOutputBufferCount;
          // STATE_BUFFERING is expected during seek/range fetch. Only call it a
          // renderer freeze once playback is actually ready and advancing.
          boolean activelyPlaying=foreground&&player.getPlaybackState()==Player.STATE_READY&&player.isPlaying()
              &&player.getPlaybackSuppressionReason()==Player.PLAYBACK_SUPPRESSION_REASON_NONE;
          if(playbackWatchdog.stalled(now,activelyPlaying,firstFrameSeen||(format!=null&&player.getTotalBufferedDuration()>=2000),
              player.getCurrentPosition(),rendered,player.getTotalBufferedDuration(),format!=null)){
            BetaLog.event("renderer_freeze","positionMs="+player.getCurrentPosition()+" bufferedMs="+player.getTotalBufferedDuration()+" rendered="+rendered+" asyncCodec="+codecAsync+" attempts="+retryCount);
            if(retryCount<PlaybackPolicy.MAX_RETRIES){
              retryCount++;resumePosition=player.getCurrentPosition();resumePlaying=true;
              // This fallback is session-local: a renderer watchdog observation
              // is not strong enough evidence to change the next app launch.
              if(codecAsync){codecAsync=false;BetaLog.event("codec_fallback","from=async to=sync reason=renderer_freeze sessionOnly=true");}
              release();start();return;
            }
            player.pause();Ui.message(PlayerActivity.this,"Playback paused","The decoder stopped producing output. Your position is saved; please send the beta log.");
            return;
          }
          if(readySince>0 && now-readySince>10000) retryCount=0;
          if(player.getPlaybackState()==Player.STATE_BUFFERING) {
            long bytes=streamStats.transferredBytes();
            ReadAheadStore readingCache=streamStats.cache;
            long consumed=readingCache==null?0:readingCache.readBytes();
            // Cached metadata parsing is progress even when network traffic is zero.
            // Never restart a progressing extractor merely for taking >30 seconds.
            if(bytes>bufferingBytes||consumed!=bufferingReadBytes){bufferingBytes=bytes;bufferingReadBytes=consumed;bufferingSince=now;}
            status.setVisibility(View.GONE);
            if(bufferingSince>0 && player.getPlayWhenReady() && now-bufferingSince>30000) {
              bufferingSince=now;
              BetaLog.event("buffer_recovery","reason=no_network_or_source_read_progress sourceReadBytes="+consumed+" downloadedBytes="+bytes);
              if(retryCount<PlaybackPolicy.MAX_RETRIES) scheduleRetry();
              else if(playbackErrorDialog==null || !playbackErrorDialog.isShowing()) {
                player.pause();
                playbackErrorDialog=Ui.dialog(PlayerActivity.this).setTitle("Connection is still slow")
                    .setMessage("Playback position has been kept. Retry when the connection is available.")
                    .setPositiveButton("Retry",(d,w) -> {retryCount=0;recoverPlayback(true);})
                    .setNegativeButton("Back",(d,w) -> finish()).show();
              }
            }
          }
          info.setVisibility(View.GONE);
          if (++ticks % 5 == 0){savePosition();
            if(BetaLog.active()){long total=streamStats.transferredBytes(),dt=lastSampleAt==0?0:now-lastSampleAt;
              ReadAheadStore cache=streamStats.cache;Object nativeLocal=TelegramBridge.get(tg.files.get(movie().fileId),"local");BetaLog.event("sample","positionMs="+player.getCurrentPosition()+" durationMs="+duration+" bufferedMs="+player.getTotalBufferedDuration()+" playWhenReady="+player.getPlayWhenReady()+" suppression="+player.getPlaybackSuppressionReason()+" state="+player.getPlaybackState()+" playing="+player.isPlaying()+" loading="+player.isLoading()+" downloadedBytes="+total+" fileBytesPerSec="+(dt>0?Math.max(0,total-lastBytes)*1000/dt:0)+" nativeBytes="+streamStats.nativeBytes+" nativeOffset="+TelegramBridge.num(nativeLocal,"downloadOffset")+" nativePrefix="+TelegramBridge.num(nativeLocal,"downloadedPrefixSize")+" downloadActive="+TelegramBridge.bool(nativeLocal,"isDownloadingActive")+" diskBytes="+streamStats.cachedBytes()+" aheadBytes="+streamStats.aheadBytes()+" readOffset="+(cache==null?-1:cache.position())+" sourceReadBytes="+(cache==null?0:cache.readBytes())+" diskReads="+(cache==null?0:cache.diskReadCount())+" requests="+streamStats.downloadRequests+" stalls="+streamStats.stalls+" copiedBytes="+streamStats.copiedBytes+" connection="+tg.connectionState);logPipeline();BetaLog.device();lastBytes=total;lastSampleAt=now;
            }else{lastBytes=0;lastSampleAt=0;}
          }
          ui.postDelayed(this, 1000);
        }
      };


  void diagnosticSnapshot(){
    if(!BetaLog.active()||player==null)return;
    BetaLog.event("player_snapshot","generation="+generation+" positionMs="+player.getCurrentPosition()+" durationMs="+player.getDuration()+" bufferedMs="+player.getTotalBufferedDuration()+" state="+player.getPlaybackState()+" playWhenReady="+player.getPlayWhenReady()+" volume="+player.getVolume()+" foreground="+foreground+" retryCount="+retryCount+" stereo="+stereo+" boostPercent="+boost+" boostGain="+(boost/100f)+" subtitleDelayMs="+subtitleDelayMs+" aspectChoice="+aspectChoice+" asyncCodec="+codecAsync+" videoEffects=disabled");
    BetaLog.format("current_video",player.getVideoFormat());BetaLog.format("current_audio",player.getAudioFormat());logPipeline();
  }

  void logPipeline(){
    if(!BetaLog.active()||player==null)return;
    if(!pipelineSnapshotPending){
      pipelineSnapshotPending=true;
      final Renderer[] renderers=new Renderer[player.getRendererCount()];
      for(int i=0;i<renderers.length;i++)renderers[i]=player.getRenderer(i);
      player.createMessage((type,payload)->{
        try{for(Renderer r:renderers){int state=r.getState();BetaLog.event("renderer_status","name="+r.getName()+" type="+r.getTrackType()+" state="+state+" ready="+(state!=Renderer.STATE_DISABLED&&r.isReady())+" ended="+(state!=Renderer.STATE_DISABLED&&r.isEnded())+" readingUs="+(state!=Renderer.STATE_DISABLED?r.getReadingPositionUs():-1));}}
        finally{pipelineSnapshotPending=false;}
      }).send();
    }
    DecoderCounters v=player.getVideoDecoderCounters(),a=player.getAudioDecoderCounters();
    if(v!=null)v.ensureUpdated();if(a!=null)a.ensureUpdated();
    BetaLog.event("pipeline","videoQueued="+(v==null?-1:v.queuedInputBufferCount)+" videoRendered="+(v==null?-1:v.renderedOutputBufferCount)+" videoDropped="+(v==null?-1:v.droppedBufferCount)+" videoDroppedToKeyframe="+(v==null?-1:v.droppedToKeyframeCount)+" audioQueued="+(a==null?-1:a.queuedInputBufferCount)+" audioRendered="+(a==null?-1:a.renderedOutputBufferCount)+" frameOffsetUs="+(lastFrameOffsetCount>0?lastFrameOffsetUs/lastFrameOffsetCount:0)+" sampleAllocated="+(activeLoadControl==null?0:activeLoadControl.getAllocator().getTotalBytesAllocated())+" surface="+(hwPlus?"TextureView":"SurfaceView")+" asyncCodec="+codecAsync+" firstFrame="+firstFrameSeen+" videoDecoder="+decoder+" audioDecoder="+audioDecoder+" boostPercent="+boost+" boostGain="+(boost/100f)+" stereo="+stereo+" playerVolume="+player.getVolume()+" speed="+player.getPlaybackParameters().speed);
  }

  void savePosition() {
    if (player == null || items.isEmpty()) return;
    long position = player.getCurrentPosition(), duration = player.getDuration();
    if (duration > 0 && duration - position < 10000) position = 0;
    new TvLibrary(this).remember(movie());
    SharedPreferences.Editor saved=prefs.edit().putLong(positionKey(),position);if(duration>0)saved.putLong("duration:"+movie().cacheIdentity(),duration);saved.apply();
  }

  void release() {
    BetaLog.event("player_release","positionMs="+(player==null?-1:player.getCurrentPosition()));
    ui.removeCallbacks(expireWarm);ui.removeCallbacks(hideControls);ui.removeCallbacks(hideSeekFeedback);
    if(bufferPulse!=null)bufferPulse.setVisibility(View.GONE);
    ui.removeCallbacks(ticker);
    ui.removeCallbacks(retryTask);
    if(playbackErrorDialog!=null) {playbackErrorDialog.dismiss();playbackErrorDialog=null;}
    pipelineSnapshotPending=false;
    if (player != null) {
      view.setPlayer(null);
      player.release();
      player = null;
    }
  }

  long warmDurationMs(){android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();am.getMemoryInfo(info);return info.lowMemory||info.availMem<512L*1024*1024?30000:120000;}

  @Override
  protected void onStop() {
    BetaLog.event("player_stop","warmRetained="+(player!=null&&!isFinishing()));
    if(Build.VERSION.SDK_INT>=26&&isInPictureInPictureMode()){super.onStop();return;}
    if(tvTools!=null)tvTools.hidden();
    foreground=false;bluetooth.stop();controlsTouchActive=false;ui.removeCallbacks(hideControls);ui.removeCallbacks(hideSeekFeedback);if(seekFeedback!=null)seekFeedback.setVisibility(View.GONE);
    if (player != null) {
      resumePosition = player.getCurrentPosition();
      resumePlaying = player.getPlayWhenReady();
      streamStats.setPlaybackPosition(resumePosition,player.getDuration()>0?player.getDuration():movie().duration*1000L,movie().size);
      savePosition();
      ui.removeCallbacks(ticker);ui.removeCallbacks(retryTask);
      bufferPulse.setVisibility(View.GONE);
      player.pause();player.setAudioAttributes(player.getAudioAttributes(),false);
      // Keep the existing surface binding during warm return. Release detaches it.
      playbackWatchdog.reset(SystemClock.elapsedRealtime(),resumePosition,-1);
      if(isFinishing())release();
      else {warm=new java.lang.ref.WeakReference<>(this);ui.postDelayed(expireWarm,warmDurationMs());}
    }
    session.remove(this);session.onHidden(this);
    stopped=player==null;generation++;
    super.onStop();
  }

  @Override
  protected void onStart() {
    BetaLog.event("player_return","warm="+(player!=null)+" resumeMs="+resumePosition);
    super.onStart();
    foreground=true;bluetooth.start();session.add(this);session.onVisible(this);changed();
    ui.removeCallbacks(expireWarm);
    if(player!=null) {
      streamStats.paused=false;
      if(streamStats.cache!=null)streamStats.cache.setPaused(false);
      view.setPlayer(player);player.setAudioAttributes(player.getAudioAttributes(),true);player.setPlayWhenReady(resumePlaying);
      bufferPulse.setVisibility(player.getPlaybackState()==Player.STATE_BUFFERING?View.VISIBLE:View.GONE);
      ui.removeCallbacks(ticker);ui.post(ticker);armControlsHide();
    } else if (stopped && !isFinishing()) start();
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);setIntent(intent);
    try {
      JSONArray array=new JSONArray(intent.getStringExtra("movies"));
      ArrayList<Movie> next=new ArrayList<>();for(int i=0;i<array.length();i++)next.add(Movie.from(array.getJSONObject(i)));
      if(next.isEmpty())return;
      int selected=Math.max(0,Math.min(next.size()-1,intent.getIntExtra("index",0)));
      boolean same=movie().cacheIdentity().equals(next.get(selected).cacheIdentity());
      boolean forceStart=intent.getBooleanExtra("startFromBeginning",false);
      boolean rebuild=!same||player==null||forceStart;
      if(rebuild)release();
      items.clear();items.addAll(next);index=selected;
      if(rebuild){streamStats=session.movie!=null&&session.movie.cacheIdentity().equals(movie().cacheIdentity())?session.stats:new StreamStats();subtitleUri=null;subtitleMime=null;appliedAutoSubtitle="";autoSubtitleBeforeStart();resumePosition=forceStart?0:movie().savedPosition(this);resumePlaying=true;restoreMovieSettings();start();}
    }catch(Exception e){Ui.message(this,"Video",e.getMessage());}
  }
  @Override public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if(!foreground&&(level==TRIM_MEMORY_RUNNING_LOW||level==TRIM_MEMORY_RUNNING_CRITICAL||level>=TRIM_MEMORY_BACKGROUND)) {
      release();stopped=true;
    }
  }

  @Override
  protected void onDestroy() {
    if(tvTools!=null)tvTools.close();
    bluetooth.stop();session.remove(this);session.onHidden(this);
    release();
    if(warm.get()==this)warm.clear();
    super.onDestroy();
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putLong("position",player==null ? resumePosition : player.getCurrentPosition());
    state.putBoolean("playing",player==null||!foreground ? resumePlaying : player.getPlayWhenReady());
    if(subtitleUri!=null) {state.putString("subtitle",subtitleUri.toString());state.putString("subtitleMime",subtitleMime);}
    savePosition();
  }

  long lastPointerLogAt;
  void logPointerEvent(android.view.MotionEvent event){
    int tool=event.getPointerCount()>0?event.getToolType(0):android.view.MotionEvent.TOOL_TYPE_UNKNOWN;
    boolean mouse=tool==android.view.MotionEvent.TOOL_TYPE_MOUSE||event.isFromSource(android.view.InputDevice.SOURCE_MOUSE);
    int action=event.getActionMasked();long now=SystemClock.elapsedRealtime();
    if(!mouse&&action!=android.view.MotionEvent.ACTION_DOWN&&action!=android.view.MotionEvent.ACTION_UP&&action!=android.view.MotionEvent.ACTION_CANCEL)return;
    if(mouse&&(action==android.view.MotionEvent.ACTION_HOVER_MOVE||action==android.view.MotionEvent.ACTION_MOVE)&&now-lastPointerLogAt<500)return;
    lastPointerLogAt=now;
    String type=mouse?"mouse":tool==android.view.MotionEvent.TOOL_TYPE_STYLUS?"stylus":"touch";
    String act=action==android.view.MotionEvent.ACTION_DOWN?"down":action==android.view.MotionEvent.ACTION_UP?"up":action==android.view.MotionEvent.ACTION_CANCEL?"cancel":action==android.view.MotionEvent.ACTION_HOVER_MOVE?"hover_move":action==android.view.MotionEvent.ACTION_HOVER_ENTER?"hover_enter":action==android.view.MotionEvent.ACTION_HOVER_EXIT?"hover_exit":action==android.view.MotionEvent.ACTION_SCROLL?"scroll":action==android.view.MotionEvent.ACTION_MOVE?"move":"other";
    if(mouse||action!=android.view.MotionEvent.ACTION_MOVE)BetaLog.event("pointer_input","type="+type+" action="+act+" source="+event.getSource()+" buttons="+event.getButtonState()+" playerState="+(player==null?-1:player.getPlaybackState())+" playing="+(player!=null&&player.isPlaying())+" positionMs="+(player==null?-1:player.getCurrentPosition())+" bufferedMs="+(player==null?-1:player.getTotalBufferedDuration()));
  }
  @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
    logPointerEvent(event);int action=event.getActionMasked();
    if(player!=null&&action==android.view.MotionEvent.ACTION_DOWN){controlsTouchActive=true;ui.removeCallbacks(hideControls);view.showController();}
    boolean handled=super.dispatchTouchEvent(event);
    if(player!=null&&(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL)){controlsTouchActive=false;armControlsHide();}
    return handled;
  }
  @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event){if(event.isFromSource(android.view.InputDevice.SOURCE_MOUSE))logPointerEvent(event);return super.dispatchGenericMotionEvent(event);}

  TextView focusLabel,seekFeedback;
  final Runnable hideSeekFeedback=()->{if(seekFeedback!=null)seekFeedback.setVisibility(View.GONE);};
  private int lastSeekKey,wakeKey,lastControlFocus=androidx.media3.ui.R.id.exo_play_pause;
  private long lastSeekTap;
  private boolean controlsTouchActive;
  private long controlsHideAt;
  final Runnable hideControls=new Runnable(){public void run(){
    if(view==null||!controlsVisible||controlsTouchActive)return;
    long remaining=controlsHideAt-SystemClock.uptimeMillis();
    if(remaining>0){ui.postDelayed(this,remaining);return;}
    BetaLog.event("controls_hide","reason=inactivity elapsedMs=5000");view.hideController();
  }};
  void showFocusLabel(View v,boolean focused){if(focusLabel==null)return;if(!focused||!controlsVisible){focusLabel.setVisibility(View.GONE);return;}CharSequence label=v.getContentDescription();if(v.getId()==androidx.media3.ui.R.id.exo_play_pause)label=player!=null&&player.getPlayWhenReady()?"Pause":"Play";focusLabel.setText(label==null?"Seek through video":label);focusLabel.setVisibility(View.VISIBLE);}
  void showSeekFeedback(long delta,long target){if(seekFeedback==null)return;long seconds=Math.abs(delta)/1000;seekFeedback.setText((delta>=0?"+":"−")+(seconds<60?seconds+"s":CinemaInfo.time(Math.abs(delta)))+"\n"+CinemaInfo.time(target));seekFeedback.setVisibility(View.VISIBLE);ui.removeCallbacks(hideSeekFeedback);ui.postDelayed(hideSeekFeedback,1000);}
  void armControlsHide(){ui.removeCallbacks(hideControls);if(view!=null&&controlsVisible&&!controlsTouchActive){controlsHideAt=SystemClock.uptimeMillis()+5000;ui.postDelayed(hideControls,5000);}}
  void styleTransport(View v,boolean focused){v.setBackground(Ui.focusUnderline(this,focused));v.setScaleX(focused?1.3f:1f);v.setScaleY(focused?1.3f:1f);if(v instanceof ImageButton)((ImageButton)v).setColorFilter(focused?Color.WHITE:0xffb8c5d5);}
  void restoreRemoteFocus(){View target=root.findViewById(lastControlFocus);if(target==null||!target.isFocusable())target=view.findViewById(androidx.media3.ui.R.id.exo_play_pause);if(target!=null)target.requestFocus();}
  void configureRemoteNavigation(){
    int[] transport={androidx.media3.ui.R.id.exo_rew,androidx.media3.ui.R.id.exo_play_pause,androidx.media3.ui.R.id.exo_ffwd};
    int seekId=androidx.media3.ui.R.id.exo_progress;
    for(int i=0;i<3;i++){
      View t=top.getChildAt(i+1),b=view.findViewById(transport[i]);t.setFocusable(true);b.setFocusable(true);
      t.setNextFocusLeftId(top.getChildAt(Math.max(1,i)).getId());t.setNextFocusRightId(top.getChildAt(Math.min(3,i+2)).getId());
      t.setNextFocusUpId(t.getId());t.setNextFocusDownId(b.getId());
      b.setNextFocusLeftId(transport[Math.max(0,i-1)]);b.setNextFocusRightId(transport[Math.min(2,i+1)]);
      b.setNextFocusUpId(t.getId());b.setNextFocusDownId(seekId);
    }
    View seek=view.findViewById(seekId);seek.setNextFocusUpId(androidx.media3.ui.R.id.exo_play_pause);seek.setNextFocusDownId(androidx.media3.ui.R.id.exo_play_pause);
    if(seek instanceof DefaultTimeBar)((DefaultTimeBar)seek).setKeyTimeIncrement(10000);
    seek.setOnFocusChangeListener((v,f)->{v.setBackground(Ui.focusUnderline(this,f));if(f)lastControlFocus=v.getId();showFocusLabel(v,f);armControlsHide();});
  }
  void showRemoteControls(){boolean hidden=!controlsVisible;view.showController();if(hidden)restoreRemoteFocus();armControlsHide();}
  @Override public boolean dispatchKeyEvent(KeyEvent event){
    if(player==null)return super.dispatchKeyEvent(event);
    if(tvTools!=null&&tvTools.key(event))return true;
    int k=event.getKeyCode();boolean arrow=k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT;
    if(event.getAction()==KeyEvent.ACTION_UP){armControlsHide();if(wakeKey==k){wakeKey=0;return true;}}
    if(event.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(event);
    boolean hidden=!controlsVisible;
    BetaLog.event("remote_key","keyCode="+k+" repeat="+event.getRepeatCount()+" controlsVisible="+!hidden+" focusId="+(getCurrentFocus()==null?0:getCurrentFocus().getId()));
    showRemoteControls();
    if(arrow&&event.getRepeatCount()==0&&lastSeekKey==k&&event.getEventTime()-lastSeekTap<=350){
      long target=Math.max(0,player.getCurrentPosition()+(k==KeyEvent.KEYCODE_DPAD_RIGHT?10000:-10000));long duration=player.getDuration();if(duration>0)target=Math.min(duration,target);player.seekTo(target);lastSeekKey=0;wakeKey=k;return true;
    }
    if(hidden&&arrow&&event.getRepeatCount()==0){lastSeekKey=k;lastSeekTap=event.getEventTime();}else if(!arrow)lastSeekKey=0;
    if(k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(player.getPlayWhenReady())player.pause();else player.play();return true;}
    if(k==KeyEvent.KEYCODE_MEDIA_PLAY){player.play();return true;}
    if(k==KeyEvent.KEYCODE_MEDIA_PAUSE){player.pause();return true;}
    if(k==KeyEvent.KEYCODE_MEDIA_NEXT||k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){if(event.getRepeatCount()==0)player.seekForward();return true;}
    if(k==KeyEvent.KEYCODE_MEDIA_PREVIOUS||k==KeyEvent.KEYCODE_MEDIA_REWIND){if(event.getRepeatCount()==0)player.seekBack();return true;}
    if(k==KeyEvent.KEYCODE_VOLUME_UP||k==KeyEvent.KEYCODE_VOLUME_DOWN||k==KeyEvent.KEYCODE_VOLUME_MUTE)return super.dispatchKeyEvent(event);
    if(k==KeyEvent.KEYCODE_MENU){tvTools.menu();wakeKey=k;return true;}
    if(hidden){wakeKey=k;return true;}
    View focused=root.findFocus();int next=View.NO_ID;
    if(focused!=null){
      if(k==KeyEvent.KEYCODE_DPAD_UP)next=focused.getNextFocusUpId();
      else if(k==KeyEvent.KEYCODE_DPAD_DOWN)next=focused.getNextFocusDownId();
      else if(focused.getId()!=androidx.media3.ui.R.id.exo_progress){if(k==KeyEvent.KEYCODE_DPAD_LEFT)next=focused.getNextFocusLeftId();else if(k==KeyEvent.KEYCODE_DPAD_RIGHT)next=focused.getNextFocusRightId();}
      if(next!=View.NO_ID){View target=root.findViewById(next);if(target!=null){target.requestFocus();wakeKey=k;return true;}}
      if((k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_NUMPAD_ENTER)&&(focused instanceof Button||focused instanceof ImageButton)){if(event.getRepeatCount()==0)focused.performClick();wakeKey=k;return true;}
    }
    if(k==KeyEvent.KEYCODE_BACK){savePosition();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));return true;}
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void onBackPressed() {
    if (view != null && view.isControllerFullyVisible()) {
      view.hideController();
      return;
    }
    savePosition();
    startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
  }
}
