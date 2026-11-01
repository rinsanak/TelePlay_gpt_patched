package com.muthumol.player.dummy;

import android.app.*;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.drinkless.tdlib.TdApi;

/** Opt-in playback diagnostics. Allowlisted numeric events, never raw TDLib/logcat dumps. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class BetaLog {
  static final int MAX_BYTES=1024*1024,QUEUE_LIMIT=256;
  static final AtomicLong hits=new AtomicLong(),misses=new AtomicLong(),waitMs=new AtomicLong(),readBytes=new AtomicLong();
  static final AtomicLong underruns=new AtomicLong(),droppedVideoFrames=new AtomicLong(),playerErrors=new AtomicLong(),pointerEvents=new AtomicLong(),boostClippedSamples=new AtomicLong();
  static volatile boolean collecting,finishing;
  static volatile long lastCpuPercent=-1;
  static volatile String failure="";
  static Context context;static File logFile;static long began,lastCpuAt,lastCpuMs;
  static ArrayBlockingQueue<String> errors;
  static final AtomicInteger errorCount=new AtomicInteger();
  static ArrayBlockingQueue<String> queue;static Thread writer;
  static volatile boolean crashHandlerInstalled;
  static final AtomicInteger dropped=new AtomicInteger();
  static synchronized void init(Context c){if(context==null){context=c.getApplicationContext();logFile=new File(context.getFilesDir(),"beta-stream-log.txt");}}
  static boolean enabled(){return BuildConfig.BETA_DIAGNOSTICS;}
  static boolean active(){return enabled()&&collecting;}
  static String state(){return collecting?"Collecting":finishing?"Saving log":failure.isEmpty()?"Stopped":failure;}
  static synchronized void start(Context c){
    init(c);if(!enabled()||collecting||finishing||(writer!=null&&writer.isAlive()))return;
    queue=new ArrayBlockingQueue<>(QUEUE_LIMIT);errors=new ArrayBlockingQueue<>(8);errorCount.set(0);dropped.set(0);hits.set(0);misses.set(0);waitMs.set(0);readBytes.set(0);underruns.set(0);droppedVideoFrames.set(0);playerErrors.set(0);pointerEvents.set(0);boostClippedSamples.set(0);
    began=SystemClock.elapsedRealtime();lastCpuAt=lastCpuMs=0;failure="";collecting=true;
    final ArrayBlockingQueue<String> pending=queue;
    writer=new Thread(()->{
      try(OutputStream out=new BufferedOutputStream(new FileOutputStream(logFile),8192)){
        int bytes=0;long flushed=SystemClock.elapsedRealtime();
        while(collecting||!pending.isEmpty()||!errors.isEmpty()){
          String line=errors.poll();boolean critical=line!=null;if(line==null)line=pending.poll(200,TimeUnit.MILLISECONDS);
          if(line!=null){byte[] data=line.getBytes(StandardCharsets.UTF_8);if(bytes+data.length>MAX_BYTES-512 || (!critical&&bytes+data.length>MAX_BYTES-65536)){dropped.incrementAndGet();}else{out.write(data);bytes+=data.length;}}
          if(SystemClock.elapsedRealtime()-flushed>=1000){out.flush();flushed=SystemClock.elapsedRealtime();}
        }
        out.write(("summary droppedEvents="+dropped.get()+" cacheHitReads="+hits.get()+" waitedReads="+misses.get()+" readWaitMs="+waitMs.get()+" readBytes="+readBytes.get()+" audioUnderrunEvents="+underruns.get()+" droppedVideoFrames="+droppedVideoFrames.get()+" playerErrors="+playerErrors.get()+" pointerEvents="+pointerEvents.get()+" boostClippedSamples="+boostClippedSamples.get()+"\n").getBytes(StandardCharsets.UTF_8));
      }catch(Exception e){failure="Log could not be saved";collecting=false;}
    },"Beta log writer");writer.setDaemon(true);writer.start();
    installCrashHandler();
    event("beta_start","version="+BuildConfig.VERSION_NAME+" utcMs="+System.currentTimeMillis()+" android="+Build.VERSION.SDK_INT+" model="+Build.MODEL.replace('\n',' ') +" abi="+Build.SUPPORTED_ABIS[0]+" cpus="+Runtime.getRuntime().availableProcessors()+" maxHeap="+Runtime.getRuntime().maxMemory());
    event("privacy","No credentials, chat text, account IDs, SSID, IP addresses, media URLs, audio samples, or pointer coordinates; exception messages redacted");
    event("build","versionCode="+BuildConfig.VERSION_CODE+" media3=1.4.1 loggingSchema=3 videoEffects=disabled");
    android.content.SharedPreferences settings=c.getSharedPreferences("player",0);
    event("saved_settings","stereo="+settings.getBoolean("stereo",true)+" boost="+settings.getInt("boost",100)+" asyncCodec="+settings.getBoolean("codecAsyncFallbackV2",Build.VERSION.SDK_INT<=25));
    device();audioCapabilities();
    ChannelSession session=ChannelSession.get(c);StreamStats stats=session.stats;event("initial_cache","movieBytes="+(session.movie==null?0:session.movie.size)+" savedPositionMs="+(session.movie==null?0:session.movie.savedPosition(c))+" indexReady="+(session.index!=null&&session.index.durationUs>0)+" diskBytes="+stats.cachedBytes()+" aheadBytes="+stats.aheadBytes()+" requests="+stats.downloadRequests+" paused="+stats.paused+" cacheFailed="+(session.store!=null&&session.store.failed()));
    TelegramBridge tg=TelegramBridge.getInstance(c);event("telegram_state","ready="+tg.ready+" connection="+tg.connectionState);
    if(c instanceof PlayerActivity)((PlayerActivity)c).diagnosticSnapshot();
    tg.send("GetOption",v->{if(v instanceof TdApi.OptionValueString)event("tdlib_version",((TdApi.OptionValueString)v).value);},"name","version");
  }
  static synchronized void installCrashHandler(){
    if(crashHandlerInstalled)return;
    final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
      if(active()){
        event("uncaught_exception","thread="+thread.getName()+" type="+error.getClass().getName());
        exception("uncaught",error);collecting=false;
        try{Thread w=writer;if(w!=null)w.join(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
      }
      if(previous!=null)previous.uncaughtException(thread,error);
      else {android.util.Log.e("MuthumolPlayer","Uncaught application exception",error);android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
    });
    crashHandlerInstalled=true;
  }
  static void event(String name,String value){
    if(!active())return;
    if("audio_underrun".equals(name))underruns.incrementAndGet();
    else if("player_error".equals(name))playerErrors.incrementAndGet();
    else if("pointer_input".equals(name))pointerEvents.incrementAndGet();
    else if("dropped_frames".equals(name))droppedVideoFrames.addAndGet(numberField(value,"count="));
    else if("boost_audio_stats".equals(name))boostClippedSamples.addAndGet(numberField(value,"clippedSamples="));
    String line=(SystemClock.elapsedRealtime()-began)+"ms "+name+" "+value+"\n";
    if(!queue.offer(line))dropped.incrementAndGet();
  }
  static long numberField(String text,String key){try{int at=text.indexOf(key);if(at<0)return 0;at+=key.length();int end=at;while(end<text.length()&&Character.isDigit(text.charAt(end)))end++;return Long.parseLong(text.substring(at,end));}catch(Exception ignored){return 0;}}
  static void exception(String stage,Throwable error){
    if(!active()||error==null)return;
    String line=(SystemClock.elapsedRealtime()-began)+"ms exception stage="+stage+" id="+errorCount.incrementAndGet()+" thread="+Thread.currentThread().getName()+"\n"+DiagnosticText.trace(error)+"exception_end\n";
    if(!errors.offer(line)){errors.poll();if(!errors.offer(line))dropped.incrementAndGet();}
  }
  static void format(String stage,androidx.media3.common.Format f){
    if(f==null)return;event(stage,"mime="+f.sampleMimeType+" codecs="+f.codecs+" width="+f.width+" height="+f.height+" fps="+f.frameRate+" channels="+f.channelCount+" rate="+f.sampleRate+" pcmEncoding="+f.pcmEncoding+" bitrate="+f.bitrate);
  }
  static void audioCapabilities(){
    if(!active()||context==null)return;
    try{android.media.AudioManager manager=(android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
      if(manager!=null)for(android.media.AudioDeviceInfo d:manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS))
        event("audio_capabilities","type="+d.getType()+" rates="+java.util.Arrays.toString(d.getSampleRates())+" channels="+java.util.Arrays.toString(d.getChannelCounts())+" encodings="+java.util.Arrays.toString(d.getEncodings()));
    }catch(Exception e){exception("audio_capabilities",e);}
  }
  static void read(boolean hit,long wait,long position,int bytes){
    if(!active())return;if(hit)hits.incrementAndGet();else misses.incrementAndGet();waitMs.addAndGet(wait);if(bytes>0)readBytes.addAndGet(bytes);
    if(wait>=500)event("read_wait","offset="+position+" ms="+wait+" bytes="+bytes);
  }
  static void device(){
    if(!active()||context==null)return;
    long cpuNow=android.os.Process.getElapsedCpuTime(),wall=SystemClock.elapsedRealtime();
    long cpuPercent=lastCpuAt>0&&wall>lastCpuAt?Math.max(0,cpuNow-lastCpuMs)*100/(wall-lastCpuAt):-1;lastCpuAt=wall;lastCpuMs=cpuNow;lastCpuPercent=cpuPercent;
    Runtime r=Runtime.getRuntime();ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();if(am!=null)am.getMemoryInfo(mi);
    ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkCapabilities cap=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
    String wifi="";try{android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);android.net.wifi.WifiInfo wi=wm==null?null:wm.getConnectionInfo();if(wi!=null)wifi=" wifiRssiDbm="+wi.getRssi()+" wifiLinkMbps="+wi.getLinkSpeed()+" wifiFrequencyMhz="+wi.getFrequency();}catch(SecurityException ignored){}
    String outputs="";try{android.media.AudioManager audio=(android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE);if(audio!=null){StringBuilder types=new StringBuilder();for(android.media.AudioDeviceInfo device:audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)){if(types.length()>0)types.append(',');types.append(device.getType());}int music=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC),max=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);outputs=" audioOutputTypes="+types+" audioMode="+audio.getMode()+" musicActive="+audio.isMusicActive()+" musicVolume="+music+" musicMax="+max+" musicMuted="+audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC);}}catch(SecurityException ignored){}
    event("device","processCpuPercent="+cpuPercent+" heapUsed="+(r.totalMemory()-r.freeMemory())+" nativeHeap="+Debug.getNativeHeapAllocatedSize()+" heapTotal="+r.totalMemory()+" systemAvailable="+mi.availMem+" lowMemory="+mi.lowMemory+" memoryClass="+(am==null?0:am.getMemoryClass())+" diskFree="+new StatFs(context.getFilesDir().getAbsolutePath()).getAvailableBytes()+wifi+outputs+" network="+(cap==null?"none":cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"wifi":cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"cellular":"other"));
  }
  static void stop(Activity a,boolean send){
    init(a);synchronized(BetaLog.class){if(finishing){Toast.makeText(a,"Log is being saved",Toast.LENGTH_SHORT).show();return;}event("beta_stop","user requested");collecting=false;finishing=true;}
    Thread t=new Thread(()->{
      try{Thread w=writer;if(w!=null)w.join(3000);if(w!=null&&w.isAlive()){failure="Log is still saving; try Stop again";}}catch(InterruptedException e){Thread.currentThread().interrupt();}
      File upload=null;
      if(send&&logFile.isFile()&&failure.isEmpty())try{File folder=new File(context.getCacheDir(),"beta-exports");folder.mkdirs();File[] old=folder.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>24L*60*60*1000)f.delete();upload=new File(folder,"beta-stream-"+began+".txt");try(InputStream in=new FileInputStream(logFile);OutputStream out=new FileOutputStream(upload)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}}catch(IOException e){failure="Log saved; could not prepare Telegram copy";}
      final File sendFile=upload;finishing=false;
      a.runOnUiThread(()->{
        if(!logFile.isFile()||!failure.isEmpty()){Ui.message(a,"Playback logs",failure.isEmpty()?"Start log collection first":failure);return;}
        if(send)sendSaved(a,sendFile);else Toast.makeText(a,"Log saved",Toast.LENGTH_SHORT).show();
      });
    },"Finish beta log");t.setDaemon(true);t.start();
  }
  static void sendSaved(Activity a,File upload){
    TelegramBridge tg=TelegramBridge.getInstance(a);
    if(!tg.ready){Ui.message(a,"Log saved","Telegram is unavailable. Reconnect Telegram, then press Log Stop again.");return;}
    Toast.makeText(a,"Sending log to Telegram Saved Messages…",Toast.LENGTH_LONG).show();
    tg.send("GetMe",me->{
      if(me instanceof TdApi.Error){sendFailed(a);return;}
      tg.send("CreatePrivateChat",chat->{
        if(chat instanceof TdApi.Error){sendFailed(a);return;}
        try{
          TdApi.Object input=document(upload);
          tg.send("SendMessage",result->a.runOnUiThread(()->{
            if(result instanceof TdApi.Error)sendFailed(a);
            else Toast.makeText(a,"Log queued in Saved Messages.",Toast.LENGTH_LONG).show();
          }),"chatId",TelegramBridge.num(chat,"id"),"inputMessageContent",input);
        }catch(Exception e){sendFailed(a);}
      },"userId",TelegramBridge.num(me,"id"),"force",false);
    });
  }
  static TdApi.Object document(File file)throws Exception{return TelegramBridge.obj("InputMessageDocument","document",TelegramBridge.obj("InputDocument","document",TelegramBridge.obj("InputFileLocal","path",file.getAbsolutePath()),"disableContentTypeDetection",false),"caption",TelegramBridge.obj("FormattedText","text","Muthumol Player "+BuildConfig.VERSION_NAME+" diagnostic log","entities",new TdApi.TextEntity[0]));}
  static void sendFailed(Activity a){a.runOnUiThread(()->Ui.message(a,"Log saved","Telegram could not send the log. Reconnect Telegram, then press Log Stop again."));}
}
