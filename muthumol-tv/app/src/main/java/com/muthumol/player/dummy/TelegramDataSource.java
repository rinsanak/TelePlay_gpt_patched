package com.muthumol.player.dummy;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;
import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.drinkless.tdlib.TdApi;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class TelegramDataSource extends BaseDataSource {
  final Context context;
  final TelegramBridge tg;
  final StreamStats stats;
  Uri uri;
  int id;
  long position,end;
  volatile boolean closed;
  ReadAheadStore store;
  TelegramDataSource(Context c,StreamStats stats) {super(false);context=c;this.stats=stats;tg=TelegramBridge.getInstance(c);}
  static long budget(Context c) throws IOException {
    long free=new StatFs(MediaCache.root(c).getAbsolutePath()).getAvailableBytes();
    if(free<32L*1024*1024)throw new IOException("Not enough free space for video preload. Clear cache or free storage.");
    return Math.min(PlaybackPolicy.CACHE_LIMIT,Math.max(2L*ReadAheadStore.BLOCK,(free-24L*1024*1024)/2));
  }
  @Override public long open(DataSpec spec) throws IOException {
    transferInitializing(spec);closed=false;uri=spec.uri;
    try{id=Integer.parseInt(uri.getPathSegments().get(0));}catch(Exception e){throw new IOException("Invalid Telegram video URI",e);}
    ChannelSession session=ChannelSession.get(context);
    store=session.acquire(id);
    long size=store.length;position=spec.position;
    BetaLog.event("source_open","offset="+position+" length="+spec.length+" fileBytes="+size);
    if(position>size)throw new IOException("Seek outside file");
    end=spec.length==C.LENGTH_UNSET?size:Math.min(size,position+spec.length);
    store.positionHint(position);
    store.setPaused(stats.paused);stats.cache=store;transferStarted(spec);return end-position;
  }
  @Override public int read(byte[] b,int offset,int count) throws IOException {
    if(count==0)return 0;if(closed)throw new IOException("Stream closed");if(position>=end)return C.RESULT_END_OF_INPUT;
    int n=store.read(position,b,offset,(int)Math.min(count,end-position));if(n>0){position+=n;bytesTransferred(n);}return n;
  }
  @Override public Uri getUri(){return uri;}
  @Override public void close(){if(closed)return;closed=true;store=null;if(uri!=null){uri=null;transferEnded();}}


  static final class Factory implements DataSource.Factory {
    final Context c;final StreamStats stats;
    Factory(Context c,StreamStats stats){this.c=c;this.stats=stats;}
    public DataSource createDataSource(){return new Route(c,stats);}
  }
  static final class Route implements DataSource {
    final Context c;final StreamStats stats;DataSource active;final List<TransferListener> listeners=new ArrayList<>();
    Route(Context c,StreamStats stats){this.c=c;this.stats=stats;}
    public void addTransferListener(TransferListener l){listeners.add(l);if(active!=null)active.addTransferListener(l);}
    public long open(DataSpec s) throws IOException {
      String scheme=s.uri.getScheme();
      if("tg".equals(scheme))active=new TelegramDataSource(c,stats);
      else if("https".equals(scheme)||"http".equals(scheme))active=new HttpReadAhead(c,stats);
      else active=new DefaultDataSource.Factory(c).createDataSource();
      for(TransferListener l:listeners)active.addTransferListener(l);return active.open(s);
    }
    public int read(byte[] b,int o,int n) throws IOException{return active.read(b,o,n);}
    public Uri getUri(){return active==null?null:active.getUri();}
    public Map<String,List<String>> getResponseHeaders(){return active==null?Collections.emptyMap():active.getResponseHeaders();}
    public void close() throws IOException{if(active!=null){active.close();active=null;}}
  }

  static final class HttpReadAhead extends BaseDataSource {
    final Context c;final StreamStats stats;DataSource fallback;ReadAheadStore store;Uri uri;long position,end;
    volatile DataSource downloading;boolean started;
    HttpReadAhead(Context c,StreamStats stats){super(false);this.c=c;this.stats=stats;}
    DefaultHttpDataSource source(){DefaultHttpDataSource d=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(30000).createDataSource();d.addTransferListener(stats);return d;}
    public long open(DataSpec spec) throws IOException {
      transferInitializing(spec);uri=spec.uri;position=spec.position;
      DefaultHttpDataSource probe=source();long remaining;
      try{remaining=probe.open(spec);}catch(IOException e){probe.close();throw e;}
      boolean rangeSupported=probe.getResponseCode()==206;
      for(Map.Entry<String,List<String>> h:probe.getResponseHeaders().entrySet())if("Accept-Ranges".equalsIgnoreCase(h.getKey()))
        for(String value:h.getValue())if(value.toLowerCase(Locale.ROOT).contains("bytes"))rangeSupported=true;
      long total=-1;
      for(Map.Entry<String,List<String>> h:probe.getResponseHeaders().entrySet())if("Content-Range".equalsIgnoreCase(h.getKey()) && !h.getValue().isEmpty()) {
        String value=h.getValue().get(0);try{total=Long.parseLong(value.substring(value.lastIndexOf('/')+1));}catch(Exception ignored){}
      }
      if(total<0 && spec.length==C.LENGTH_UNSET && remaining>=0)total=position+remaining;
      if(total<0 || !rangeSupported) {fallback=probe;started=true;transferStarted(spec);return remaining;}
      probe.close();end=spec.length==C.LENGTH_UNSET?total:Math.min(total,position+spec.length);
      stats.diskBudget=budget(c);
      store=new ReadAheadStore(MediaCache.directory(c,uri.toString()),total,position,stats::preloadTargetBytes,stats::cacheBudgetBytes,stats::playbackBytePosition,new ReadAheadStore.Loader(){
        Runnable progress=()->{};
        public void progressListener(Runnable listener){progress=listener;}
        public void load(long start,int length,File target,BooleanSupplier stop) throws IOException {
          DefaultHttpDataSource network=source();downloading=network;
          try(OutputStream out=new FileOutputStream(target)) {
            network.open(spec.buildUpon().setPosition(start).setLength(length).build());byte[] data=new byte[65536];int left=length;
            while(left>0 && !stop.getAsBoolean()) {int n=network.read(data,0,Math.min(left,data.length));if(n<0)throw new EOFException("Incomplete HTTP range");out.write(data,0,n);left-=n;progress.run();}
            if(left>0)throw new IOException("Stream closed");
          }finally{network.close();downloading=null;}
        }
        public void cancel(){DataSource d=downloading;if(d!=null)try{d.close();}catch(IOException ignored){}}
      },true);
      store.setPaused(stats.paused);stats.cache=store;started=true;transferStarted(spec);return end-position;
    }
    public int read(byte[] b,int o,int n) throws IOException {
      if(n==0)return 0;if(fallback!=null){int count=fallback.read(b,o,n);if(count>0)bytesTransferred(count);return count;}
      if(position>=end)return C.RESULT_END_OF_INPUT;
      int count=store.read(position,b,o,(int)Math.min(n,end-position));if(count>0){position+=count;bytesTransferred(count);}return count;
    }
    public Uri getUri(){return uri;}
    public void close() throws IOException {
      if(fallback!=null){fallback.close();fallback=null;}if(store!=null){store.closePreservingCompletedBlocks();if(stats.cache==store)stats.cache=null;store=null;}
      if(started){started=false;transferEnded();}uri=null;
    }
  }
}
