package com.muthumol.player.dummy;

import android.os.SystemClock;
import androidx.media3.datasource.*;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class StreamStats implements TransferListener {
  volatile boolean paused;
  volatile int transferEpoch;
  volatile long mediaBits, diskBudget=PlaybackPolicy.CACHE_LIMIT, playbackBytePosition=-1;
  volatile int stalls,downloadRequests;
  volatile long copiedBytes,nativeBytes;
  volatile ReadAheadStore cache;
  volatile String networkState="Waiting for download";
  final NetworkMeter meter=new NetworkMeter();
  final Map<Integer,Long> downloaded=new HashMap<>();
  synchronized void resetMovie(){downloaded.clear();meter.reset();nativeBytes=copiedBytes=mediaBits=0;stalls=downloadRequests=0;playbackBytePosition=-1;cache=null;networkState="Movie data cleared";}
  synchronized void beginFile(int id,long existing) {downloaded.put(id,existing);nativeBytes=existing;meter.start(SystemClock.elapsedRealtime());}
  synchronized void fileProgress(int id,long size,boolean active) {
    Long old=downloaded.get(id);if(old==null)return;nativeBytes=Math.max(old,size);
    if(size>old) {meter.add(size-old,SystemClock.elapsedRealtime());downloaded.put(id,size);}
    networkState=active ? "Downloading" : "Download idle";
  }
  synchronized void endFile(int id){downloaded.remove(id);}
  boolean hasNetworkSample(){return meter.totalBytes()>0;}
  long throughputBits(){return meter.bitsPerSecond(SystemClock.elapsedRealtime());}
  long transferredBytes(){return meter.totalBytes();}
  long preloadTargetBytes(){return PlaybackPolicy.preloadBytes(mediaBits,diskBudget);}
  long cacheBudgetBytes(){return diskBudget;}
  long playbackBytePosition(){return playbackBytePosition;}
  void setPlaybackPosition(long positionMs,long durationMs,long bytes) {
    if(positionMs>=0&&durationMs>0&&bytes>0)playbackBytePosition=(long)(Math.min(1.0,positionMs/(double)durationMs)*bytes);
  }
  long aheadBytes(){ReadAheadStore c=cache;return c==null?0:c.aheadBytes();}
  long cachedBytes(){ReadAheadStore c=cache;return c==null?0:c.cachedBytes();}
  int targetSeconds(){return PlaybackPolicy.PRELOAD_SECONDS;}
  public void onTransferInitializing(DataSource s,DataSpec d,boolean network){}
  public void onTransferStart(DataSource s,DataSpec d,boolean network){if(network){meter.start(SystemClock.elapsedRealtime());networkState="Downloading";}}
  public void onBytesTransferred(DataSource s,DataSpec d,boolean network,int bytes){if(network)meter.add(bytes,SystemClock.elapsedRealtime());}
  public void onTransferEnd(DataSource s,DataSpec d,boolean network){if(network)networkState="Prefetch ready / waiting";}
}
