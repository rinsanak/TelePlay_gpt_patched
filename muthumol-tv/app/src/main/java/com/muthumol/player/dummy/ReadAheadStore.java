package com.muthumol.player.dummy;

import java.io.*;
import java.util.*;
import java.util.function.*;

/** Download on a producer thread; playback reads completed segments on disk. */
final class ReadAheadStore implements Closeable {
  static final int BLOCK=1024*1024;
  static final int START_BLOCK=64*1024;
  static final long HEADER_BYTES=2L*BLOCK;
  static long blockStart(long p){return p<HEADER_BYTES?p/START_BLOCK*START_BLOCK:p/BLOCK*BLOCK;}
  int blockSize(long p){return (int)Math.min(p<HEADER_BYTES?START_BLOCK:BLOCK,length-p);}
  static final long KEEP_BEHIND_BYTES=32L*1024*1024;
  interface Loader {
    void load(long start,int length,File target,BooleanSupplier cancelled) throws IOException;
    void cancel();
    default void preempt(){cancel();}
    default void progressListener(Runnable listener){}
    default void finish(){}
    default void committed(long start,int length){}
  }
  final File directory;
  final long length;
  final LongSupplier targetBytes;
  final LongSupplier cacheBudget;
  final LongSupplier playbackPosition;
  final Loader loader;
  private final TreeMap<Long,File> blocks=new TreeMap<>();
  private final Thread worker;
  private volatile boolean closed;
  private boolean paused;
  private long windowFrom=-1,windowTo=-1;
  private final ArrayDeque<Long> warmBlocks=new ArrayDeque<>();
  private File activePartial;
  private RandomAccessFile reader;private long readerBlock=-1;
  // EBML indexes often request one byte at a time. Bound RAM to one 64 KiB
  // page instead of doing seek/stat/read syscalls for every extractor byte.
  private final byte[] readPage=new byte[START_BLOCK];
  private long pageStart=-1,readBytes,diskReadCount;
  private int pageLength;
  synchronized int readCached(long pos,byte[] bytes,int offset,int count)throws IOException {if(pos<0||pos>=length)return -1;if(count==0)return 0;long start=blockStart(pos);File file=blocks.get(start);if(file==null||!file.isFile())return -1;try(RandomAccessFile input=new RandomAccessFile(file,"r")){long at=pos-start;if(at>=input.length())return -1;input.seek(at);return input.read(bytes,offset,(int)Math.min(count,input.length()-at));}}
  synchronized long readBytes(){return readBytes;}
  synchronized long diskReadCount(){return diskReadCount;}
  private synchronized boolean missingDemand(long loadingOffset){
    long demand=requestedBlock;
    return demand>=0&&!blocks.containsKey(demand)&&demand!=loadingOffset
        &&demand!=loadingOffset+blockSize(loadingOffset);
  }
  private void closeReader(){if(reader!=null)try{reader.close();}catch(IOException ignored){}reader=null;readerBlock=-1;pageStart=-1;pageLength=0;}
  private long activeOffset=-1;
  synchronized void window(long from,long to){windowFrom=from;windowTo=to;pruneWindow();notifyAll();}
  synchronized void warmRange(long from,long to){for(long n=blockStart(Math.max(0,from));n<Math.min(length,to);n+=blockSize(n))if(!blocks.containsKey(n)&&!warmBlocks.contains(n))warmBlocks.add(n);notifyAll();}
  synchronized void positionHint(long p){readPosition=Math.max(0,Math.min(length,p));notifyAll();}
  private void pruneWindow(){if(windowFrom<0)return;Iterator<Map.Entry<Long,File>> it=blocks.entrySet().iterator();while(it.hasNext()){Map.Entry<Long,File> e=it.next();long p=e.getKey();if(p<HEADER_BYTES||p>=length-2L*BLOCK||p==requestedBlock)continue;if(p+blockSize(p)<windowFrom||p>windowTo){if(readerBlock==p)closeReader();e.getValue().delete();accessed.remove(p);it.remove();}}}

  private final Map<Long,Long> accessed=new HashMap<>();
  private final String partialSuffix="."+java.util.UUID.randomUUID()+".partial";
  synchronized void setPaused(boolean value){paused=value;notifyAll();}
  private final boolean preserveCompleteBlocks;
  private boolean preserveOnClose;
  private long readPosition;
  private volatile long requestedBlock=-1;
  private IOException failure;
  ReadAheadStore(File dir,long length,long start,LongSupplier targetBytes,Loader loader) throws IOException {
    this(dir,length,start,targetBytes,()->Math.max(2L*BLOCK,targetBytes.getAsLong()*2),()->-1L,loader,false);
  }
  ReadAheadStore(File dir,long length,long start,LongSupplier targetBytes,Loader loader,boolean preserveCompleteBlocks) throws IOException {
    this(dir,length,start,targetBytes,()->Math.max(2L*BLOCK,targetBytes.getAsLong()*2),()->-1L,loader,preserveCompleteBlocks);
  }
  ReadAheadStore(File dir,long length,long start,LongSupplier targetBytes,LongSupplier cacheBudget,LongSupplier playbackPosition,Loader loader,boolean preserveCompleteBlocks) throws IOException {
    this.directory=dir;this.length=length;this.readPosition=start;this.targetBytes=targetBytes;this.cacheBudget=cacheBudget;this.playbackPosition=playbackPosition;this.loader=loader;this.preserveCompleteBlocks=preserveCompleteBlocks;
    loader.progressListener(()->{synchronized(this){notifyAll();}});
    if(!dir.mkdirs() && !dir.isDirectory())throw new IOException("Cannot create video preload cache");
    if(preserveCompleteBlocks)loadSavedBlocks();
    worker=new Thread(this::produce,"Video read-ahead");worker.setDaemon(true);worker.start();
  }
  private void loadSavedBlocks() {
    File[] files=directory.listFiles();if(files==null)return;
    for(File f:files) {
      String name=f.getName();
      if(name.endsWith(".partial")){f.delete();continue;}
      if(!name.endsWith(".block"))continue;
      try {
        long offset=Long.parseLong(name.substring(0,name.length()-6));
        long expected=offset>=0&&offset<length?blockSize(offset):-1;
        if(offset!=blockStart(offset)||expected<0||f.length()!=expected)f.delete();
        else {blocks.put(offset,f);accessed.put(offset,f.lastModified());}
      }catch(NumberFormatException e){f.delete();}
    }
  }

  private void produce() {
    try {
      while(!closed) {
        long next,limit;
        synchronized(this) {
          if(paused){wait(250);continue;}
          next=blockStart(readPosition);
          limit=windowTo>=0?windowTo:Math.min(length,readPosition+Math.max(2L*BLOCK,targetBytes.getAsLong()));
          while(blocks.containsKey(next))next+=blockSize(next);
          while(!warmBlocks.isEmpty()&&blocks.containsKey(warmBlocks.peek()))warmBlocks.remove();
          if(requestedBlock>=0&&!blocks.containsKey(requestedBlock)){next=requestedBlock;limit=Math.max(limit,next+blockSize(next));}
          else if(!warmBlocks.isEmpty()){next=warmBlocks.remove();limit=Math.max(limit,next+blockSize(next));}
          long nextSize=next<length?blockSize(next):0;
          if(next<limit)makeRoom(next,nextSize);
          if(next>=limit || (cachedBytes()+nextSize>cacheBudget.getAsLong()&&next!=requestedBlock)) {wait(250);continue;}
        }
        final long loadingOffset=next;java.util.concurrent.atomic.AtomicBoolean yielded=new java.util.concurrent.atomic.AtomicBoolean();
        int size=blockSize(next);File partial=new File(directory,next+partialSuffix),complete=new File(directory,next+".block");
        synchronized(this){activePartial=partial;activeOffset=next;notifyAll();}
        IOException problem=null;
        for(int attempt=1;attempt<=3 && !closed;attempt++) {
          try {loader.load(next,size,partial,()->{if(missingDemand(loadingOffset))yielded.set(true);return closed||yielded.get();});problem=null;break;}
          catch(IOException e) {problem=e;break;}
        }
        if(closed)break;
        if(yielded.get()){BetaLog.event("cache_preempt","active="+loadingOffset+" demand="+requestedBlock);loader.preempt();synchronized(this){if(readerBlock==activeOffset)closeReader();partial.delete();activePartial=null;activeOffset=-1;notifyAll();}continue;}
        if(problem!=null)throw problem;
        if(partial.length()!=size)throw new IOException("Incomplete video preload segment");
        synchronized(this) {if(!partial.renameTo(complete))throw new IOException("Cannot commit video preload segment");blocks.put(next,complete);loader.committed(next,size);activePartial=null;activeOffset=-1;accessed.put(next,System.currentTimeMillis());pruneWindow();notifyAll();}
      }
    } catch(InterruptedException e) {Thread.currentThread().interrupt();}
      catch(IOException e) {BetaLog.event("cache_failure","type="+e.getClass().getSimpleName());synchronized(this){failure=e;notifyAll();}}
    finally {
      loader.finish();
      synchronized(this){closeReader();}
      File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(partialSuffix))f.delete();
      if(closed&&!preserveOnClose)cleanup();
    }
  }
  synchronized int read(long position,byte[] data,int offset,int count) throws IOException {
    if(count==0)return 0;if(position>=length)return -1;
    readPosition=position;long block=blockStart(position);
    boolean hit=blocks.containsKey(block);
    requestedBlock=hit?-1:block;if(!hit)notifyAll();
    long waitStarted=BetaLog.active()?android.os.SystemClock.elapsedRealtime():0;
    while(!closed && !blocks.containsKey(block) && !(activeOffset==block&&activePartial!=null&&activePartial.length()>position-block)) {
      if(failure!=null)throw failure;
      try {wait(150);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
    }
    if(closed)throw new IOException("Stream closed");
    File f=blocks.get(block);boolean complete=f!=null;if(f==null)f=activePartial;
    if(reader==null||readerBlock!=block){closeReader();reader=new RandomAccessFile(f,"r");readerBlock=block;}
    if(position<pageStart||position>=pageStart+pageLength){
      long local=position-block,available=complete?blockSize(block):reader.length();
      reader.seek(local);pageLength=reader.read(readPage,0,(int)Math.min(readPage.length,available-local));
      diskReadCount++;pageStart=position;
      long now=System.currentTimeMillis(),last=accessed.getOrDefault(block,0L);
      if(now-last>=30000){accessed.put(block,now);if(complete)f.setLastModified(now);}
      if(pageLength<=0)throw new IOException("Invalid cached segment");
    }
    int pageOffset=(int)(position-pageStart),n=Math.min(count,pageLength-pageOffset);
    System.arraycopy(readPage,pageOffset,data,offset,n);readBytes+=n;
    if(waitStarted>0)BetaLog.read(hit,android.os.SystemClock.elapsedRealtime()-waitStarted,position,n);
    readPosition=position+n;requestedBlock=-1;
    if(!hit||blockStart(readPosition)!=block)notifyAll();return n;
  }

  // Retain exact downloaded ranges, including extractor headers and tail indexes.
  // Evict only when space is required; never infer a byte offset from movie time.
  private void makeRoom(long wanted,long size) {
    while(cachedBytes()+size>cacheBudget.getAsLong() && blocks.size()>1) {
      Long victim=null;long oldest=Long.MAX_VALUE;
      for(Long offset:blocks.keySet()) {
        if(offset==requestedBlock||offset==wanted)continue;
        if(wanted!=requestedBlock && !(windowFrom>=0 ? offset+blockSize(offset)<windowFrom||offset>windowTo : offset+blockSize(offset)<readPosition))continue;
        if(offset<HEADER_BYTES||offset>=Math.max(HEADER_BYTES,length-2L*BLOCK))continue;
        long stamp=accessed.getOrDefault(offset,0L);
        if(stamp<oldest){oldest=stamp;victim=offset;}
      }
      if(victim==null && wanted==requestedBlock) {
        for(Long offset:blocks.keySet()) {
          if(offset==0||offset==requestedBlock||offset==wanted)continue;
          long stamp=accessed.getOrDefault(offset,0L);
          if(stamp<oldest){oldest=stamp;victim=offset;}
        }
      }
      if(victim==null)break;
      if(readerBlock==victim)closeReader();blocks.remove(victim).delete();accessed.remove(victim);
    }
  }
  synchronized long aheadBytes() {
    long next=blockStart(readPosition),total=0;
    while(blocks.containsKey(next)) {total+=blocks.get(next).length();next+=blockSize(next);}
    return Math.max(0,total-(readPosition-blockStart(readPosition)));
  }
  synchronized long cachedBytes() {long n=0;for(File f:blocks.values())n+=f.length();return n;}
  synchronized boolean fullyCached(){
    long at=0;while(at<length){File f=blocks.get(at);int size=blockSize(at);if(f==null||f.length()!=size)return false;at+=size;}return length>0;
  }
  synchronized boolean failed(){return failure!=null;}
  synchronized long position(){return readPosition;}
  public void close() {close(false);}
  void closePreservingCompletedBlocks() {close(preserveCompleteBlocks);}
  private void close(boolean preserve) {
    synchronized(this) {if(closed)return;preserveOnClose=preserve;closed=true;notifyAll();}
    loader.cancel();worker.interrupt();
    if(Thread.currentThread()!=worker)try{worker.join(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    if(!worker.isAlive()) {
      File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(partialSuffix))f.delete();
      if(!preserve)cleanup();
    }
  }
  private synchronized void cleanup(){File[] all=directory.listFiles();if(all!=null)for(File f:all)f.delete();directory.delete();blocks.clear();}
}
