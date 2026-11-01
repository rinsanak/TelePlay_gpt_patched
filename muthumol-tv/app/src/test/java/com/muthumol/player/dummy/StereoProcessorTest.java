package com.muthumol.player.dummy;
import java.nio.*;
import org.junit.Test;
import static org.junit.Assert.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.*;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=25)
public class StereoProcessorTest {
 @Test public void stereoIsInactiveAndBitExactBypass()throws Exception{
  StereoProcessor p=new StereoProcessor();assertEquals(AudioFormat.NOT_SET,p.configure(new AudioFormat(48000,2,C.ENCODING_PCM_16BIT)));p.flush();assertFalse(p.isActive());
 }
 @Test public void bulkDownmixMatchesMedia3AcrossEveryLayout()throws Exception{
  for(int channels:new int[]{1,3,4,5,6,7,8}){
   short[] samples=new short[channels*4096];java.util.Random rng=new java.util.Random(9);for(int i=0;i<samples.length;i++)samples[i]=(short)rng.nextInt();
   ByteBuffer source=ByteBuffer.allocateDirect(samples.length*2).order(ByteOrder.nativeOrder());source.asShortBuffer().put(samples);
   ChannelMixingAudioProcessor reference=new ChannelMixingAudioProcessor();reference.putChannelMixingMatrix(new ChannelMixingMatrix(channels,2,PlaybackPolicy.stereoMatrix(channels)));
   StereoProcessor bulk=new StereoProcessor();AudioFormat format=new AudioFormat(48000,channels,C.ENCODING_PCM_16BIT);
   reference.configure(format);reference.flush();bulk.configure(format);bulk.flush();reference.queueInput(source.duplicate().order(ByteOrder.nativeOrder()));bulk.queueInput(source);
   assertFalse(source.hasRemaining());ByteBuffer a=reference.getOutput(),b=bulk.getOutput();assertEquals(a.remaining(),b.remaining());while(a.hasRemaining())assertEquals(a.getShort(),b.getShort());
  }
 }
}
