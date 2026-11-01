package com.muthumol.player.dummy;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

public class DownloadAndCacheTest {
  @Test(timeout=5000) public void byteAtATimeLargeMovieIndexUsesBoundedDiskReads() throws Exception {
    File dir=Files.createTempDirectory("large-movie-index").toFile();
    long length=3520073314L,block=ReadAheadStore.blockStart(length-500000);
    for(long at=block;at<length;at+=ReadAheadStore.BLOCK)
      new PatternLoader().load(at,(int)Math.min(ReadAheadStore.BLOCK,length-at),new File(dir,at+".block"),()->false);
    ReadAheadStore store=new ReadAheadStore(dir,length,length,()->0,new PatternLoader(),true);
    try {
      store.setPaused(true);byte[] one=new byte[1];long start=length-400000;
      for(long p=start;p<length;p++){assertEquals(1,store.read(p,one,0,1));assertEquals((byte)(p%251),one[0]);}
      assertEquals(400000,store.readBytes());assertTrue("one disk read per page",store.diskReadCount()<=8);
      // Backward seeks and a page boundary must return the original bytes.
      assertEquals(1,store.read(start+123,one,0,1));assertEquals((byte)((start+123)%251),one[0]);
      assertEquals(-1,store.read(length,one,0,1));
    }finally{store.close();}
  }
  @Test(timeout=5000) public void cachedIndexReadsDoNotPreemptUsefulBackgroundDownload()throws Exception {
    File dir=Files.createTempDirectory("cached-demand").toFile();long length=8L*ReadAheadStore.BLOCK;
    new PatternLoader().load(0,ReadAheadStore.START_BLOCK,new File(dir,"0.block"),()->false);
    java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),finish=new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean interrupted=new java.util.concurrent.atomic.AtomicBoolean();
    ReadAheadStore.Loader loader=new ReadAheadStore.Loader(){
      public void load(long at,int size,File file,BooleanSupplier stop)throws IOException{
        started.countDown();try{while(!finish.await(1,java.util.concurrent.TimeUnit.MILLISECONDS)){if(stop.getAsBoolean()){interrupted.set(true);return;}}}catch(InterruptedException e){throw new IOException(e);}
        new PatternLoader().load(at,size,file,stop);
      }
      public void cancel(){finish.countDown();}
    };
    ReadAheadStore store=new ReadAheadStore(dir,length,4L*ReadAheadStore.BLOCK,()->2L*ReadAheadStore.BLOCK,loader,true);
    try{assertTrue(started.await(1,java.util.concurrent.TimeUnit.SECONDS));byte[] b=new byte[1];
      for(int i=0;i<50000;i++){store.read(i,b,0,1);assertEquals((byte)(i%251),b[0]);}
      assertFalse(interrupted.get());assertFalse(store.failed());
    }finally{finish.countDown();store.close();}
  }
  @Test public void speedMeasuresDownloadedBytesAndFallsToZeroWhenIdle() {
    NetworkMeter m=new NetworkMeter();m.start(1000);m.add(1000000,1500);m.add(1000000,2000);
    assertEquals(16000000,m.bitsPerSecond(2000));assertEquals(2000000,m.totalBytes());
    assertEquals(0,m.bitsPerSecond(5000));
    // Cached reads never add anything to this meter.
    assertEquals(2000000,m.totalBytes());
  }
  @Test public void preloadTargetUsesVideoBitrateAndStorageBudget() {
    assertEquals(225000000,PlaybackPolicy.preloadBytes(6000000,512L*1024*1024));
    assertEquals(64L*1024*1024,PlaybackPolicy.preloadBytes(50000000,64L*1024*1024));
  }
  @Test public void stalledTelegramRangeRetriesBeforeFailingInsteadOfWaitingAMinute() {
    assertEquals(15000,PlaybackPolicy.RANGE_STALL_TIMEOUT_MS);
    assertEquals(2,PlaybackPolicy.RANGE_STALL_RETRIES);
  }
  @Test public void playbackReadsCorrectBytesAcrossCacheEviction() throws Exception {
    File dir=Files.createTempDirectory("read-ahead-test").toFile();
    ReadAheadStore store=new ReadAheadStore(dir,5L*ReadAheadStore.BLOCK,333,()->2L*ReadAheadStore.BLOCK,new PatternLoader());
    try {
      long position=333;byte[] b=new byte[8192];
      while(position<4L*ReadAheadStore.BLOCK) {
        int n=store.read(position,b,0,b.length);assertTrue(n>0);
        for(int i=0;i<n;i++)assertEquals((byte)((position+i)%251),b[i]);
        position+=n;assertTrue(store.cachedBytes()<=4L*ReadAheadStore.BLOCK);
      }
    }finally{store.close();}
  }
  @Test public void seekStartsAtRequestedRegionAndRespectsEof() throws Exception {
    long length=3L*ReadAheadStore.BLOCK+37,start=3L*ReadAheadStore.BLOCK+20;
    ReadAheadStore store=new ReadAheadStore(Files.createTempDirectory("seek-cache-test").toFile(),length,start,()->2L*ReadAheadStore.BLOCK,new PatternLoader());
    try {byte[] b=new byte[256];assertEquals(17,store.read(start,b,0,256));assertEquals(-1,store.read(length,b,0,256));}
    finally{store.close();}
  }
  @Test public void completedDiskSegmentsSurviveCloseAndAreReusedOnReturn() throws Exception {
    File root=Files.createTempDirectory("persistent-read-ahead").toFile();File dir=MediaCache.directory(root,"tg://file/91/sample");
    CopyOnWriteArrayList<Long> firstLoads=new CopyOnWriteArrayList<>();
    ReadAheadStore.Loader firstLoader=new ReadAheadStore.Loader() {
      public void load(long start,int length,File target,BooleanSupplier stop)throws IOException {firstLoads.add(start);new PatternLoader().load(start,length,target,stop);}
      public void cancel(){}
    };
    ReadAheadStore first=new ReadAheadStore(dir,4L*ReadAheadStore.BLOCK,333,()->2L*ReadAheadStore.BLOCK,firstLoader,true);
    byte[] data=new byte[64];assertEquals(64,first.read(333,data,0,64));assertEquals((byte)(333%251),data[0]);awaitComplete(dir,0);first.closePreservingCompletedBlocks();
    assertTrue(new File(dir,"0.block").isFile());
    CopyOnWriteArrayList<Long> secondLoads=new CopyOnWriteArrayList<>();
    ReadAheadStore.Loader secondLoader=new ReadAheadStore.Loader() {
      public void load(long start,int length,File target,BooleanSupplier stop)throws IOException {secondLoads.add(start);new PatternLoader().load(start,length,target,stop);}
      public void cancel(){}
    };
    ReadAheadStore second=new ReadAheadStore(dir,4L*ReadAheadStore.BLOCK,333,()->2L*ReadAheadStore.BLOCK,secondLoader,true);
    try {assertEquals(64,second.read(333,data,0,64));assertEquals((byte)(333%251),data[0]);assertFalse(secondLoads.contains(0L));}
    finally {second.closePreservingCompletedBlocks();MediaCache.delete(root);}
  }
  @Test public void recentSeekWindowSurvivesCloseWithinDiskBudget() throws Exception {
    File root=Files.createTempDirectory("back-seek-window").toFile();File dir=MediaCache.directory(root,"same movie");
    long length=64L*ReadAheadStore.BLOCK,target=2L*ReadAheadStore.BLOCK;
    java.util.concurrent.atomic.AtomicLong playbackPosition=new java.util.concurrent.atomic.AtomicLong(0);
    ReadAheadStore store=new ReadAheadStore(dir,length,0,()->target,()->ReadAheadStore.KEEP_BEHIND_BYTES+8L*target,playbackPosition::get,new PatternLoader(),true);
    try {
      byte[] data=new byte[32768];long position=0;
      while(position<48L*ReadAheadStore.BLOCK) {
        int n=store.read(position,data,0,data.length);assertTrue(n>0);position+=n;
        playbackPosition.set(position-8L*ReadAheadStore.BLOCK);
      }
      assertTrue(store.cachedBytes()<=ReadAheadStore.KEEP_BEHIND_BYTES+8L*target);
    }finally {store.closePreservingCompletedBlocks();}
    long resumeByte=40L*ReadAheadStore.BLOCK+333;
    assertTrue(new File(dir,"0.block").exists());
    // Header survives while the exact recent seek range is retained.
    java.util.concurrent.atomic.AtomicInteger networkLoads=new java.util.concurrent.atomic.AtomicInteger();
    ReadAheadStore.Loader loader=new ReadAheadStore.Loader(){
      public void load(long start,int size,File targetFile,BooleanSupplier stop)throws IOException{networkLoads.incrementAndGet();new PatternLoader().load(start,size,targetFile,stop);}
      public void cancel(){}
    };
    playbackPosition.set(resumeByte);
    ReadAheadStore reopened=new ReadAheadStore(dir,length,resumeByte,()->target,()->ReadAheadStore.KEEP_BEHIND_BYTES+8L*target,playbackPosition::get,loader,true);
    try {byte[] part=new byte[32];assertEquals(32,reopened.read(resumeByte,part,0,32));assertEquals((byte)(resumeByte%251),part[0]);assertEquals(0,networkLoads.get());}
    finally {reopened.closePreservingCompletedBlocks();MediaCache.delete(root);}
  }
  @Test public void newMovieKeepsOnlyItsCacheAndExplicitClearRemovesIt() throws Exception {
    File root=Files.createTempDirectory("video-cache-clear").toFile();
    File keep=MediaCache.directory(root,"current movie"),old=MediaCache.directory(root,"previous movie");
    assertTrue(keep.mkdirs());assertTrue(old.mkdirs());
    Files.write(new File(keep,"0.block").toPath(),new byte[]{1,2});Files.write(new File(old,"0.block").toPath(),new byte[]{3});
    MediaCache.activate(MediaCache.key("current movie"));MediaCache.keepOnly(root,MediaCache.key("current movie"));
    assertEquals(2,MediaCache.bytes(root));assertFalse(old.exists());assertTrue(keep.exists());
    MediaCache.clear(root);assertEquals(0,MediaCache.bytes(root));assertFalse(keep.exists());
    MediaCache.delete(root);
  }
  @Test public void extractorHeaderAndTailSurviveDistantSeekWithWrongTimeEstimate() throws Exception {
    File dir=Files.createTempDirectory("extractor-reopen").toFile();
    long length=64L*ReadAheadStore.BLOCK;
    ReadAheadStore first=new ReadAheadStore(dir,length,0,()->2L*ReadAheadStore.BLOCK,()->48L*ReadAheadStore.BLOCK,()->60L*ReadAheadStore.BLOCK,new PatternLoader(),true);
    byte[] data=new byte[64];
    for(long at=0;at<ReadAheadStore.HEADER_BYTES;at+=ReadAheadStore.START_BLOCK)first.read(at,data,0,64);
    awaitComplete(dir,ReadAheadStore.HEADER_BYTES-ReadAheadStore.START_BLOCK);first.closePreservingCompletedBlocks();
    ReadAheadStore tail=new ReadAheadStore(dir,length,length-128,()->2L*ReadAheadStore.BLOCK,new PatternLoader(),true);
    tail.read(length-128,data,0,64);awaitComplete(dir,ReadAheadStore.blockStart(length-128));tail.closePreservingCompletedBlocks();
    ReadAheadStore middle=new ReadAheadStore(dir,length,40L*ReadAheadStore.BLOCK,()->2L*ReadAheadStore.BLOCK,new PatternLoader(),true);
    middle.read(40L*ReadAheadStore.BLOCK,data,0,64);awaitComplete(dir,40L*ReadAheadStore.BLOCK);middle.closePreservingCompletedBlocks();
    assertTrue(new File(dir,"0.block").exists());
    java.util.concurrent.atomic.AtomicInteger network=new java.util.concurrent.atomic.AtomicInteger();
    ReadAheadStore.Loader loader=new ReadAheadStore.Loader(){
      public void load(long at,int size,File file,BooleanSupplier stop)throws IOException {network.incrementAndGet();throw new IOException("Network unavailable");}
      public void cancel(){}
    };
    ReadAheadStore reopened=new ReadAheadStore(dir,length,0,()->0,()->48L*ReadAheadStore.BLOCK,()->60L*ReadAheadStore.BLOCK,loader,true);
    try {reopened.setPaused(true);assertEquals(64,reopened.read(0,data,0,64));assertEquals(64,reopened.read(length-128,data,0,64));assertEquals(64,reopened.read(40L*ReadAheadStore.BLOCK,data,0,64));assertEquals(0,network.get());}
    finally {reopened.closePreservingCompletedBlocks();MediaCache.delete(dir);}
  }
  @Test public void initialReadRequestsSmallBlockBeforeBulkPreload() throws Exception {
    File dir=Files.createTempDirectory("fast-start").toFile();
    java.util.concurrent.atomic.AtomicInteger firstSize=new java.util.concurrent.atomic.AtomicInteger();
    ReadAheadStore.Loader loader=new ReadAheadStore.Loader(){
      public void load(long at,int size,File file,BooleanSupplier stop)throws IOException {firstSize.compareAndSet(0,size);new PatternLoader().load(at,size,file,stop);}
      public void cancel(){}
    };
    ReadAheadStore store=new ReadAheadStore(dir,8L*ReadAheadStore.BLOCK,0,()->2L*ReadAheadStore.BLOCK,loader);
    try {byte[] part=new byte[32];assertEquals(32,store.read(0,part,0,32));assertEquals(64*1024,firstSize.get());}
    finally {store.close();}
  }

  static void awaitComplete(File dir,long offset)throws Exception {long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(!new File(dir,offset+".block").isFile()&&System.nanoTime()<end)Thread.sleep(5);assertTrue(new File(dir,offset+".block").isFile());}
  @Test public void firstBytesAreReadableBeforeNetworkBlockCompletes() throws Exception {
    java.util.concurrent.CountDownLatch finish=new java.util.concurrent.CountDownLatch(1);
    File dir=Files.createTempDirectory("progressive-start").toFile();
    ReadAheadStore.Loader loader=new ReadAheadStore.Loader(){
      public void load(long start,int size,File file,BooleanSupplier stop)throws IOException {
        try(OutputStream out=new FileOutputStream(file)){out.write(new byte[]{42,43});out.flush();
          try{if(!finish.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Timed out waiting for reader");}catch(InterruptedException e){throw new IOException(e);}
          out.write(new byte[size-2]);}
      }
      public void cancel(){finish.countDown();}
    };
    ReadAheadStore store=new ReadAheadStore(dir,ReadAheadStore.START_BLOCK,0,()->0,loader,true);
    try {byte[] bytes=new byte[8];assertEquals(2,store.read(0,bytes,0,8));assertEquals(42,bytes[0]);assertEquals(1,finish.getCount());
      finish.countDown();awaitComplete(dir,0);assertEquals(8,store.read(2,bytes,0,8));
    }finally{finish.countDown();store.close();MediaCache.delete(dir);}
  }
  @Test public void rollingWindowPrunesOutsideRangesAndKeepsHeaderAndTail() throws Exception {
    File dir=Files.createTempDirectory("indexed-window").toFile();long length=30L*ReadAheadStore.BLOCK;
    for(long offset:new long[]{0,5L*ReadAheadStore.BLOCK,12L*ReadAheadStore.BLOCK,20L*ReadAheadStore.BLOCK,29L*ReadAheadStore.BLOCK}){
      int size=offset==0?ReadAheadStore.START_BLOCK:ReadAheadStore.BLOCK;try(java.io.RandomAccessFile f=new java.io.RandomAccessFile(new File(dir,offset+".block"),"rw")){f.setLength(size);}
    }
    ReadAheadStore store=new ReadAheadStore(dir,length,12L*ReadAheadStore.BLOCK,()->0,new PatternLoader(),true);
    try {store.setPaused(true);store.window(10L*ReadAheadStore.BLOCK,15L*ReadAheadStore.BLOCK);
      assertTrue(new File(dir,"0.block").exists());assertTrue(new File(dir,(12L*ReadAheadStore.BLOCK)+".block").exists());
      assertTrue(new File(dir,(29L*ReadAheadStore.BLOCK)+".block").exists());assertFalse(new File(dir,(5L*ReadAheadStore.BLOCK)+".block").exists());assertFalse(new File(dir,(20L*ReadAheadStore.BLOCK)+".block").exists());
    }finally{store.close();MediaCache.delete(dir);}
  }

  @Test(timeout=4000) public void demandedHeaderPreemptsUnrelatedBackgroundRange() throws Exception {
    File dir=Files.createTempDirectory("startup-priority").toFile();java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean first=new java.util.concurrent.atomic.AtomicBoolean(true);
    ReadAheadStore.Loader loader=new ReadAheadStore.Loader(){
      public void load(long at,int length,File target,BooleanSupplier stop)throws IOException {
        if(first.getAndSet(false)){started.countDown();while(!stop.getAsBoolean()){try{Thread.sleep(5);}catch(InterruptedException e){throw new IOException(e);}}throw new IOException("Preempted");}
        new PatternLoader().load(at,length,target,stop);
      }
      public void cancel(){}
    };
    ReadAheadStore store=new ReadAheadStore(dir,8L*ReadAheadStore.BLOCK,4L*ReadAheadStore.BLOCK,()->2L*ReadAheadStore.BLOCK,loader);
    try {assertTrue(started.await(1,java.util.concurrent.TimeUnit.SECONDS));byte[] bytes=new byte[16];assertEquals(16,store.read(0,bytes,0,16));assertEquals((byte)1,bytes[1]);assertFalse(store.failed());}
    finally {store.close();}
  }
  @Test(timeout=4000) public void nextSequentialBlockDoesNotDiscardCurrentDownload()throws Exception {
    File dir=Files.createTempDirectory("sequential-no-cancel").toFile();
    java.util.concurrent.CountDownLatch firstStarted=new java.util.concurrent.CountDownLatch(1),finishFirst=new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger preempts=new java.util.concurrent.atomic.AtomicInteger();
    ReadAheadStore.Loader loader=new ReadAheadStore.Loader(){
      public void load(long at,int length,File target,BooleanSupplier stop)throws IOException{
        if(at==0){firstStarted.countDown();try{while(!finishFirst.await(5,java.util.concurrent.TimeUnit.MILLISECONDS))if(stop.getAsBoolean())throw new IOException("Unexpected preemption");}catch(InterruptedException e){throw new IOException(e);}}
        new PatternLoader().load(at,length,target,stop);
      }
      public void cancel(){finishFirst.countDown();}
      public void preempt(){preempts.incrementAndGet();}
    };
    ReadAheadStore store=new ReadAheadStore(dir,3L*ReadAheadStore.START_BLOCK,0,()->0,loader);
    java.util.concurrent.ExecutorService reader=java.util.concurrent.Executors.newSingleThreadExecutor();
    try{assertTrue(firstStarted.await(1,java.util.concurrent.TimeUnit.SECONDS));java.util.concurrent.Future<Integer> read=reader.submit(()->store.read(ReadAheadStore.START_BLOCK,new byte[8],0,8));
      long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);while(store.position()!=ReadAheadStore.START_BLOCK&&System.nanoTime()<end)Thread.sleep(5);
      assertEquals(ReadAheadStore.START_BLOCK,store.position());Thread.sleep(40);assertEquals(0,preempts.get());finishFirst.countDown();assertEquals(8,(int)read.get(1,java.util.concurrent.TimeUnit.SECONDS));assertFalse(store.failed());
    }finally{finishFirst.countDown();store.close();reader.shutdownNow();}
  }
  static class PatternLoader implements ReadAheadStore.Loader {
    public void load(long start,int length,File file,BooleanSupplier stop) throws IOException {
      try(OutputStream out=new FileOutputStream(file)) {byte[] b=new byte[8192];int done=0;while(done<length){int n=Math.min(b.length,length-done);for(int i=0;i<n;i++)b[i]=(byte)((start+done+i)%251);out.write(b,0,n);done+=n;}}
    }
    public void cancel(){}
  }
}
