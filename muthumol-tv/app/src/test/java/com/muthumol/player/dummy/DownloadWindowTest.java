package com.muthumol.player.dummy;
import org.junit.Test;
import static org.junit.Assert.*;
public class DownloadWindowTest {
  @Test public void smallDiskBlocksReuseOneNetworkWindow(){
    DownloadWindow w=new DownloadWindow();long bytes=DownloadWindow.bytes(1L<<30,256L<<20);w.start(0,bytes);
    for(long offset=0;offset<bytes;offset+=ReadAheadStore.START_BLOCK)assertTrue(w.contains(offset));
    assertFalse(w.contains(bytes));assertFalse(w.contains(-1));
    w.start(20L<<20,bytes);assertFalse(w.contains(0));assertTrue(w.contains(20L<<20));
    w.reset();assertFalse(w.contains(20L<<20));
  }
  @Test public void seekPastWindowCannotPretendUncopiedBytesArePersisted(){
    DownloadWindow w=new DownloadWindow();w.start(0,1024);w.copied(0,256);w.copied(800,224);assertFalse(w.consumed());w.copied(256,768);assertTrue(w.consumed());
  }
  @Test public void windowIsBoundedByFileAndStorage(){
    assertEquals(1024,DownloadWindow.bytes(1024,256L<<20));
    assertEquals(16L<<20,DownloadWindow.bytes(1L<<30,256L<<20));
    assertEquals(1L<<20,DownloadWindow.bytes(1L<<30,8L<<20));
  }
}
