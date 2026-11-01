package com.muthumol.player.dummy;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackPolicyTest {
  @Test public void bufferingRespondsToSlowLinksAndRepeatedStalls() {
    assertEquals(300,PlaybackPolicy.targetSeconds(0,0,0));
    assertEquals(300,PlaybackPolicy.targetSeconds(10000000,2000000,0));
    assertEquals(300,PlaybackPolicy.targetSeconds(1000000,2000000,0));
    assertEquals(300,PlaybackPolicy.targetSeconds(10000000,2000000,2));
  }
  @Test public void requestWindowsAndRecoveryAreBounded() {
    assertEquals(2L*1024*1024,PlaybackPolicy.requestBytes(0));
    assertEquals(8L*1024*1024,PlaybackPolicy.requestBytes(1000000000));
    assertEquals(1000,PlaybackPolicy.retryDelayMs(1));
    assertEquals(2000,PlaybackPolicy.retryDelayMs(2));
    assertEquals(4000,PlaybackPolicy.retryDelayMs(3));
    assertTrue(PlaybackPolicy.requestBytes(1000000000)<PlaybackPolicy.CACHE_LIMIT);
  }
  @Test public void stereoMixUsesRmsNormalizationAndKeepsDialogue() {
    for(int channels=1;channels<=8;channels++) {
      float[] m=PlaybackPolicy.stereoMatrix(channels);
      float l=0,r=0;
      for(int i=0;i<channels;i++) {l+=m[i*2]*m[i*2];r+=m[i*2+1]*m[i*2+1];}
      assertTrue(l<=1.0001f);assertTrue(r<=1.0001f);
      if(channels==3 || channels>=5) {assertTrue(m[4]>0);assertTrue(m[5]>0);}
      assertTrue(m[0]>0);assertTrue(m[channels==1 ? 1 : 3]>0);
    }
    assertArrayEquals(new float[]{1,0,0,1},PlaybackPolicy.stereoMatrix(2),0);
  }
  @Test public void fiveOneRmsMixPreservesMoreFrontAndCenterLevel() {
    float[] m=PlaybackPolicy.stereoMatrix(6);
    assertEquals(.6963f,m[0],.001f);assertEquals(.4925f,m[4],.001f);
    assertEquals(.6963f,m[3],.001f);assertEquals(.4925f,m[11],.001f);
  }
  @Test public void ramBudgetLeavesSystemAndRadioHeadroom() {
    assertEquals(16*1024*1024,PlaybackPolicy.memoryBudget(256,512L*1024*1024,true));
    assertEquals(32*1024*1024,PlaybackPolicy.memoryBudget(256,256L*1024*1024,false));
    assertEquals(PlaybackPolicy.MEMORY_LIMIT,PlaybackPolicy.memoryBudget(1024,4L*1024*1024*1024,false));
  }

 @org.junit.Test public void asyncPolicyUsesApi31DefaultAndRetainsProjectorMode(){
  org.junit.Assert.assertTrue(PlaybackPolicy.defaultAsyncCodec(25));
  org.junit.Assert.assertFalse(PlaybackPolicy.defaultAsyncCodec(28));
  org.junit.Assert.assertTrue(PlaybackPolicy.defaultAsyncCodec(31));
  org.junit.Assert.assertTrue(PlaybackPolicy.defaultAsyncCodec(35));
 }
}
