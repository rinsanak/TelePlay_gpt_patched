package com.muthumol.player.dummy;
import java.io.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.drinkless.tdlib.TdApi;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class TelegramStreamingTest {
  @Before public void storage(){org.robolectric.shadows.ShadowStatFs.registerStats(RuntimeEnvironment.getApplication().getCacheDir().getAbsolutePath(),1024*1024,512*1024,512*1024);}
  static class FakeBridge extends TelegramBridge {
    final TdApi.File file=new TdApi.File();int requests,deletes,cancels;
    FakeBridge(File source){super(RuntimeEnvironment.getApplication(),false);file.id=7;file.size=64L<<20;file.local=new TdApi.LocalFile(source.getAbsolutePath(),true,true,false,false,0,0,0);files.put(7,file);}
    @Override void download(int id,long offset,long limit){requests++;file.local.downloadOffset=offset;file.local.downloadedPrefixSize=limit;file.local.downloadedSize+=limit;file.local.isDownloadingActive=false;publishFile(id,file);}
    @Override TdApi.Object waitRequest(String method,Object... args)throws IOException{
      if(method.equals("GetFile"))return file;
      if(method.equals("DeleteFile")){deletes++;file.local.downloadedSize=0;file.local.downloadedPrefixSize=0;return new TdApi.Ok();}
      if(method.equals("GetFileDownloadedPrefixSize")){long offset=0;for(int i=0;i<args.length;i+=2)if(args[i].equals("offset"))offset=((Number)args[i+1]).longValue();return new TdApi.FileDownloadedPrefixSize(offset>=file.local.downloadOffset&&offset<file.local.downloadOffset+file.local.downloadedPrefixSize?file.local.downloadOffset+file.local.downloadedPrefixSize-offset:0);}
      throw new IOException(method);
    }
    @Override void cancel(int id){cancels++;}
  }
  @Test(timeout=5000) public void missingNativeCacheIsDownloadedAgain()throws Exception {
    File dir=Files.createTempDirectory("missing-native-cache").toFile(),source=new File(dir,"missing.bin"),target=new File(dir,"block");
    byte[] expected=new byte[65536];for(int i=0;i<expected.length;i++)expected[i]=(byte)(i%251);
    FakeBridge tg=new FakeBridge(source){
      @Override void download(int id,long offset,long limit){try{Files.write(source.toPath(),expected);}catch(IOException e){throw new RuntimeException(e);}super.download(id,offset,limit);}
    };
    tg.file.local.downloadedPrefixSize=65536;tg.file.local.downloadedSize=65536;
    StreamStats stats=new StreamStats();TelegramRangeLoader loader=new TelegramRangeLoader(tg,7,64L<<20,stats);
    try{loader.load(0,65536,target,()->false);assertArrayEquals(expected,Files.readAllBytes(target.toPath()));assertEquals(1,tg.deletes);assertEquals(1,tg.requests);}
    finally{loader.finish();MediaCache.delete(dir);}
  }
  @Test(timeout=5000) public void missingNativeCacheRecoveryIsBounded()throws Exception {
    File dir=Files.createTempDirectory("missing-native-bounded").toFile(),source=new File(dir,"missing.bin"),target=new File(dir,"block");
    FakeBridge tg=new FakeBridge(source);tg.file.local.downloadedPrefixSize=65536;
    TelegramRangeLoader loader=new TelegramRangeLoader(tg,7,64L<<20,new StreamStats());
    try{assertThrows(FileNotFoundException.class,()->loader.load(0,65536,target,()->false));assertEquals(1,tg.deletes);assertEquals(1,tg.requests);}
    finally{loader.finish();MediaCache.delete(dir);}
  }
  @Test(timeout=5000) public void resumedNativePrefixStartsAfterAlreadyCopiedBytes()throws Exception{
    File dir=Files.createTempDirectory("prefix-resume").toFile(),source=new File(dir,"source.bin"),target=new File(dir,"block");
    try(RandomAccessFile f=new RandomAccessFile(source,"rw")){f.setLength(64L<<20);for(int i=0;i<65536;i++)f.write(i%251);}
    FakeBridge tg=new FakeBridge(source);tg.file.local.downloadedPrefixSize=32768;
    StreamStats stats=new StreamStats();stats.diskBudget=128L<<20;
    TelegramRangeLoader loader=new TelegramRangeLoader(tg,7,64L<<20,stats);
    loader.progressListener(()->{
      // TDLib now exposes the new range rather than the already consumed prefix.
      tg.file.local.downloadOffset=32768;tg.file.local.downloadedPrefixSize=32768;
    });
    try{loader.load(0,65536,target,()->false);byte[] actual=Files.readAllBytes(target.toPath());assertEquals(65536,actual.length);
      for(int i=0;i<actual.length;i++)assertEquals((byte)(i%251),actual[i]);assertEquals(0,tg.requests);
    }finally{loader.finish();MediaCache.delete(dir);}
  }
  @Test public void sequentialBlocksShareWindowsAndNeverDeleteAt32Mb()throws Exception{
    File dir=Files.createTempDirectory("telegram-windows").toFile(),source=new File(dir,"source.bin"),target=new File(dir,"block");
    try(RandomAccessFile f=new RandomAccessFile(source,"rw")){f.setLength(64L<<20);f.write(new byte[]{41,42,43});}
    FakeBridge tg=new FakeBridge(source);StreamStats stats=new StreamStats();stats.diskBudget=128L<<20;
    TelegramRangeLoader loader=new TelegramRangeLoader(tg,7,64L<<20,stats);
    try{
      for(long offset=0;offset<2L<<20;offset+=ReadAheadStore.START_BLOCK){loader.load(offset,ReadAheadStore.START_BLOCK,target,()->false);loader.committed(offset,ReadAheadStore.START_BLOCK);}
      assertEquals(1,tg.requests);assertEquals(0,tg.cancels);
      for(long offset=2L<<20;offset<35L<<20;offset+=ReadAheadStore.BLOCK){loader.load(offset,ReadAheadStore.BLOCK,target,()->false);loader.committed(offset,ReadAheadStore.BLOCK);}
      assertEquals(3,tg.requests);assertEquals(0,tg.deletes);assertEquals(0,tg.cancels);
      loader.load(50L<<20,ReadAheadStore.BLOCK,target,()->false);assertEquals(4,tg.requests);
    }finally{loader.finish();MediaCache.delete(dir);}
  }
  @Test public void cacheReclamationOccursOnlyAfterConsumedInactiveWindow()throws Exception{
    File dir=Files.createTempDirectory("telegram-budget").toFile(),source=new File(dir,"source.bin"),target=new File(dir,"block");try(RandomAccessFile f=new RandomAccessFile(source,"rw")){f.setLength(64L<<20);}
    FakeBridge tg=new FakeBridge(source);StreamStats stats=new StreamStats();stats.diskBudget=8L<<20;
    TelegramRangeLoader loader=new TelegramRangeLoader(tg,7,64L<<20,stats);
    try{for(long offset=0;offset<9L<<20;offset+=ReadAheadStore.BLOCK){loader.load(offset,ReadAheadStore.BLOCK,target,()->false);loader.committed(offset,ReadAheadStore.BLOCK);}assertEquals(1,tg.deletes);}
    finally{loader.finish();MediaCache.delete(dir);}
  }
}
