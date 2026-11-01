package com.muthumol.player.dummy;
import java.nio.*;
import org.junit.Test;
import static org.junit.Assert.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
public class BoostProcessorTest {
 @Test public void sharedEmptyBufferDoesNotCrashAtAnyBoost()throws Exception {
  BoostProcessor p=new BoostProcessor();p.configure(new AudioFormat(48000,2,C.ENCODING_PCM_16BIT));p.flush();
  for(int level:new int[]{100,300,100,200,300}){p.percent=level;p.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER);assertFalse(p.getOutput().hasRemaining());}
 }
 @Test public void emptyPollPreservesPendingAudioAndLiveBoostWorks()throws Exception {
  BoostProcessor p=new BoostProcessor();p.configure(new AudioFormat(48000,2,C.ENCODING_PCM_16BIT));p.flush();
  for(int level:new int[]{100,300,100,300}){
   p.percent=level;p.flush();ByteBuffer in=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());in.putShort((short)1000).putShort((short)-1000).flip();
   p.queueInput(in);p.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER);
   ByteBuffer out=p.getOutput().order(ByteOrder.nativeOrder());assertEquals(4,out.remaining());assertEquals(level*10,out.getShort());assertEquals(-level*10,out.getShort());assertFalse(in.hasRemaining());
  }
 }
 @Test public void threeHundredPercentSixChannelAudioAmplifiesAndClipsSafely()throws Exception {
  BoostProcessor p=new BoostProcessor();p.percent=300;p.configure(new AudioFormat(48000,6,C.ENCODING_PCM_16BIT));p.flush();
  ByteBuffer in=ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());for(int i=0;i<6;i++)in.putShort((short)(i==0?32767:1000));in.flip();p.queueInput(in);
  ByteBuffer out=p.getOutput().order(ByteOrder.nativeOrder());assertEquals(12,out.remaining());assertEquals(Short.MAX_VALUE,out.getShort());for(int i=1;i<6;i++)assertEquals(3000,out.getShort());
 }
 @Test public void boostMatchesLinearGainAndVlcStyleClippingForAllLayouts()throws Exception {
  for(int channels:new int[]{1,2,6,8})for(int level:new int[]{100,200,263,300}){
   int count=channels*5000;short[] input=new short[count],expected=new short[count];java.util.Random random=new java.util.Random(42);
   for(int i=0;i<count;i++)input[i]=(short)random.nextInt();
   float gain=level/100f;
   for(int i=0;i<count;i++)expected[i]=level==100?input[i]:(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(input[i]*gain)));
   BoostProcessor p=new BoostProcessor();p.percent=level;p.configure(new AudioFormat(48000,channels,C.ENCODING_PCM_16BIT));p.flush();
   ByteBuffer in=ByteBuffer.allocateDirect(count*2+4).order(ByteOrder.nativeOrder());in.position(4);in.asShortBuffer().put(input);in.limit(count*2+4);
   p.queueInput(in);assertEquals(in.limit(),in.position());ByteBuffer out=p.getOutput().order(ByteOrder.nativeOrder());short[] actual=new short[count];out.asShortBuffer().get(actual);assertArrayEquals(expected,actual);
  }
 }
 short[] process(int percent,short... samples)throws Exception {BoostProcessor p=new BoostProcessor();p.percent=percent;p.configure(new AudioFormat(48000,2,C.ENCODING_PCM_16BIT));p.flush();ByteBuffer in=ByteBuffer.allocateDirect(samples.length*2).order(ByteOrder.nativeOrder());for(short sample:samples)in.putShort(sample);in.flip();p.queueInput(in);ByteBuffer out=p.getOutput().order(ByteOrder.nativeOrder());short[] result=new short[out.remaining()/2];for(int i=0;i<result.length;i++)result[i]=out.getShort();return result;}
 @Test public void normalVolumeIsBitExact()throws Exception {short[] input={0,32767,-32768,1234,-6543,0};assertArrayEquals(input,process(100,input));}
 @Test public void quietAudioGetsThreeTimesGain()throws Exception {assertArrayEquals(new short[]{3000,-3000,6000,-6000},process(300,(short)1000,(short)-1000,(short)2000,(short)-2000));}
 @Test public void fullScalePeaksClipWithoutWrapping()throws Exception {short[] out=process(300,(short)32767,(short)-32768,(short)30000,(short)-30000);assertArrayEquals(new short[]{32767,-32768,32767,-32768},out);}
}
