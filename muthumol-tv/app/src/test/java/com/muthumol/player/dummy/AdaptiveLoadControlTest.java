package com.muthumol.player.dummy;
import org.junit.Test;
import static org.junit.Assert.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.analytics.PlayerId;
public class AdaptiveLoadControlTest {
 LoadControl.Parameters p(long us,boolean retry){return new LoadControl.Parameters(PlayerId.UNSET,Timeline.EMPTY,null,0,us,1,true,retry,C.TIME_UNSET);}
 @Test public void diskPreloadDoesNotRequireMinutesOfCompressedRam(){
  AdaptiveLoadControl load=new AdaptiveLoadControl(new StreamStats(),24*1024*1024);
  assertTrue(load.shouldContinueLoading(p(10000000,false)));assertFalse(load.shouldContinueLoading(p(30000000,false)));
  assertFalse(load.shouldContinueLoading(p(20000000,false)));assertTrue(load.shouldContinueLoading(p(14000000,false)));
  assertTrue(load.shouldStartPlayback(p(1500000,false)));assertFalse(load.shouldStartPlayback(p(1499999,false)));
  assertFalse(load.shouldStartPlayback(p(3999999,true)));assertTrue(load.shouldStartPlayback(p(4000000,true)));
 }
 @Test public void sampleAllocatorCapStopsLoading(){
  AdaptiveLoadControl load=new AdaptiveLoadControl(new StreamStats(),2*C.DEFAULT_BUFFER_SEGMENT_SIZE);
  load.getAllocator().allocate();load.getAllocator().allocate();
  assertFalse(load.shouldContinueLoading(p(2000000,false)));
 }
}
