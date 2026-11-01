package com.muthumol.player.dummy;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackWatchdogTest {
 @Test public void bufferedFreezeIsDetectedDespiteBackgroundDownloads(){
  PlaybackWatchdog w=new PlaybackWatchdog();assertFalse(w.stalled(0,true,true,1000,10,93000,true));
  assertFalse(w.stalled(7999,true,true,1000,10,93000,true));assertTrue(w.stalled(8000,true,true,1000,10,93000,true));
 }
 @Test public void audioClockAdvancingCannotHideFrozenVideo(){
  PlaybackWatchdog w=new PlaybackWatchdog();w.stalled(0,true,true,1000,10,10000,true);
  assertTrue(w.stalled(8000,true,true,9000,10,10000,true));
 }
 @Test public void pauseSeekAndUncachedBufferingAreNotRendererFreezes(){
  PlaybackWatchdog w=new PlaybackWatchdog();w.stalled(0,true,true,1000,10,10000,true);
  assertFalse(w.stalled(20000,false,true,1000,10,10000,true));assertFalse(w.stalled(30000,true,true,1000,10,10000,true));
  assertFalse(w.stalled(35000,true,true,500000,11,10000,true));assertFalse(w.stalled(40000,true,true,505000,12,10000,true));
  assertFalse(w.stalled(50000,true,true,505000,12,0,true));
 }
 @Test public void metadataParsingAndUnknownVideoCountersDoNotFalseTrigger(){
  PlaybackWatchdog w=new PlaybackWatchdog();assertFalse(w.stalled(60000,true,false,0,-1,10000,false));
  w.stalled(70000,true,true,1000,-1,10000,true);assertFalse(w.stalled(80000,true,true,11000,-1,10000,true));
 }
}
