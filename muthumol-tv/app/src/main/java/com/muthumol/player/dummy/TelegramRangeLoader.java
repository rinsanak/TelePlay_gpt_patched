package com.muthumol.player.dummy;
import java.io.*;
import java.util.function.BooleanSupplier;
import android.os.SystemClock;
import android.os.StatFs;
import org.drinkless.tdlib.TdApi;

/** One stable TDLib network window, consumed progressively into persistent disk blocks. */
final class TelegramRangeLoader implements ReadAheadStore.Loader {
  final TelegramBridge tg;final int id;final long size;final StreamStats stats;
  final DownloadWindow window=new DownloadWindow();
  final byte[] data=new byte[65536];
  Runnable progress=()->{};
  RandomAccessFile input;String inputPath="";
  int epoch=-1;boolean initialized;
  long requestAt,lastPrefixQuery,queriedAvailable;
  TelegramRangeLoader(TelegramBridge tg,int id,long size,StreamStats stats){this.tg=tg;this.id=id;this.size=size;this.stats=stats;}
  public void progressListener(Runnable listener){progress=listener;}
  public void committed(long start,int length){window.copied(start,length);}
  // A reader seek will select a new window if needed; do not cancel useful in-flight bytes here.
  public void preempt(){BetaLog.event("range_preempt","file="+id);}
  long available(Object file,long position){
    Object local=TelegramBridge.get(file,"local");
    if(TelegramBridge.bool(local,"isDownloadingCompleted"))return size-position;
    long from=TelegramBridge.num(local,"downloadOffset"),prefix=TelegramBridge.num(local,"downloadedPrefixSize");
    return position>=from&&position<from+prefix?from+prefix-position:0;
  }
  void request(long position){
    long bytes=DownloadWindow.bytes(size-position,stats.diskBudget);
    window.start(position,bytes);requestAt=SystemClock.elapsedRealtime();
    tg.download(id,position,bytes);stats.downloadRequests++;
  }
  public void load(long start,int length,File target,BooleanSupplier stop)throws IOException {
    long began=SystemClock.elapsedRealtime(),lastProgress=began;int copied=0,stallRetries=0;
    boolean repairedMissingLocalFile=false;
    TdApi.Object file=tg.files.get(id);
    if(!initialized||file==null){file=tg.waitRequest("GetFile","fileId",id);tg.publishFile(id,file);if(!initialized){stats.beginFile(id,TelegramBridge.num(TelegramBridge.get(file,"local"),"downloadedSize"));initialized=true;}}
    // Query old disjoint cached ranges once, rather than on every 30 ms read attempt.
    queriedAvailable=available(file,start);lastPrefixQuery=began;
    if(queriedAvailable<length&&!window.contains(start)){TdApi.Object prefix=tg.waitRequest("GetFileDownloadedPrefixSize","fileId",id,"offset",start);queriedAvailable=Math.max(queriedAvailable,TelegramBridge.num(prefix,"size"));}
    try(OutputStream out=new FileOutputStream(target)){
      while(copied<length&&!stop.getAsBoolean()){
        if(stats.paused){closeInput();window.reset();while(stats.paused&&!stop.getAsBoolean())tg.awaitFileChange(tg.revision(),250);if(stop.getAsBoolean())break;lastProgress=SystemClock.elapsedRealtime();}
        if(epoch!=stats.transferEpoch){epoch=stats.transferEpoch;window.reset();}
        int beforeCopy=copied;
        long revision=tg.revision();file=tg.files.get(id);Object local=TelegramBridge.get(file,"local");
        long ready=Math.min(length,Math.max(queriedAvailable,copied+available(file,start+copied)));
        if(ready>copied){
          String path=TelegramBridge.str(local,"path");
          // TDLib can retain a non-empty local path and downloaded-prefix count
          // after Android has removed the underlying cache file. Those stale
          // values make the player retry the same ENOENT forever. Reset TDLib's
          // local record once, then request this range again from Telegram.
          if(path.isEmpty()||!new File(path).isFile()){
            if(repairedMissingLocalFile)throw new FileNotFoundException("Telegram local media file is still missing after refresh");
            BetaLog.event("telegram_local_file_missing","offset="+(start+copied)+" pathPresent="+(!path.isEmpty())+" downloadedBytes="+TelegramBridge.num(local,"downloadedSize"));
            repairedMissingLocalFile=true;
            closeInput();
            tg.cancel(id);
            tg.waitRequest("DeleteFile","fileId",id);
            file=tg.waitRequest("GetFile","fileId",id);tg.publishFile(id,file);
            local=TelegramBridge.get(file,"local");
            stats.beginFile(id,TelegramBridge.num(local,"downloadedSize"));
            queriedAvailable=0;window.reset();
            request(start+copied);
            lastProgress=SystemClock.elapsedRealtime();lastPrefixQuery=lastProgress;
            continue;
          }
          if(!path.isEmpty()){
            if(input==null||!path.equals(inputPath)){closeInput();input=new RandomAccessFile(path,"r");inputPath=path;}
            input.seek(start+copied);
            while(copied<ready&&!stop.getAsBoolean()){
              int n=input.read(data,0,(int)Math.min(data.length,ready-copied));if(n<=0)break;
              out.write(data,0,n);copied+=n;lastProgress=SystemClock.elapsedRealtime();stats.copiedBytes+=n;progress.run();
            }
          }
        }
        if(copied==length)break;
        // UpdateFile may advance while bytes are being copied. Consume those
        // bytes before issuing a replacement request at the resumed offset.
        if(copied>beforeCopy&&available(tg.files.get(id),start+copied)>0)continue;
        long now=SystemClock.elapsedRealtime(),wanted=start+copied;
        boolean active=TelegramBridge.bool(local,"isDownloadingActive");
        if(!window.contains(wanted)||(!active&&now-requestAt>2000)){
          reclaimConsumedWindow(wanted,file);
          request(wanted);
        }
        // UpdateFile covers the active contiguous prefix. A slow fallback handles old disjoint ranges.
        if(now-lastPrefixQuery>=1500){
          TdApi.Object prefix=tg.waitRequest("GetFileDownloadedPrefixSize","fileId",id,"offset",wanted);
          queriedAvailable=Math.max(queriedAvailable,copied+TelegramBridge.num(prefix,"size"));lastPrefixQuery=SystemClock.elapsedRealtime();
        }
        if(now-lastProgress>PlaybackPolicy.RANGE_STALL_TIMEOUT_MS){
          if(stallRetries++>=PlaybackPolicy.RANGE_STALL_RETRIES){BetaLog.event("range_stalled","file="+id+" offset="+wanted+" retries="+stallRetries);throw new IOException("Telegram transfer stalled after retry");}
          BetaLog.event("range_stall_retry","file="+id+" offset="+wanted+" attempt="+stallRetries);
          tg.cancel(id);window.reset();queriedAvailable=copied;request(wanted);lastPrefixQuery=now;lastProgress=now;
        }
        stats.nativeBytes=TelegramBridge.num(local,"downloadedSize");
        tg.awaitFileChange(revision,150);
      }
    }
    if(copied!=length)throw new IOException("Stream closed");
    if(BetaLog.active()&&SystemClock.elapsedRealtime()-began>1000)BetaLog.event("range_copy","offset="+start+" bytes="+length+" waitMs="+(SystemClock.elapsedRealtime()-began));
  }
  void reclaimConsumedWindow(long wanted,Object file)throws IOException{
    Object local=TelegramBridge.get(file,"local");long nativeBytes=TelegramBridge.num(local,"downloadedSize");
    // TDLib cannot delete individual ranges. Only recycle at a fully consumed, inactive
    // network-window boundary, or on actual low storage; never every 32 MB block.
    long free=new StatFs(tg.context.getCacheDir().getAbsolutePath()).getAvailableBytes();
    boolean exhausted=window.consumed()&&wanted>=window.to&&!TelegramBridge.bool(local,"isDownloadingActive");
    if((exhausted&&nativeBytes>=stats.diskBudget)||(free<24L*1024*1024&&nativeBytes>0)){
      BetaLog.event("native_reclaim","bytes="+nativeBytes+" free="+free+" exhausted="+exhausted);
      closeInput();tg.cancel(id);tg.waitRequest("DeleteFile","fileId",id);
      TdApi.Object fresh=tg.waitRequest("GetFile","fileId",id);tg.publishFile(id,fresh);
      stats.beginFile(id,TelegramBridge.num(TelegramBridge.get(fresh,"local"),"downloadedSize"));
      queriedAvailable=0;window.reset();
    }
  }
  void closeInput(){if(input!=null)try{input.close();}catch(IOException ignored){}input=null;inputPath="";}
  static void sleep(long ms)throws IOException{try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
  public void cancel(){tg.cancel(id);synchronized(tg.fileSignal){tg.fileSignal.notifyAll();}}
  public void finish(){closeInput();}
}
