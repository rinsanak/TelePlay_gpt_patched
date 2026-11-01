package com.muthumol.player.dummy;
import java.nio.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class BoostProcessor extends BaseAudioProcessor {
  volatile int percent=100;
  private short[] samples=new short[0];
  private long statsStartMs,statFrames,statClipped,statProcessNs,maxBlockNs;
  protected AudioFormat onConfigure(AudioFormat f)throws UnhandledAudioFormatException{if(f.encoding!=C.ENCODING_PCM_16BIT)throw new UnhandledAudioFormatException(f);return f;}
  public void queueInput(ByteBuffer input){
    // Ignore Media3's shared empty drain poll; replaceOutputBuffer(0) can
    // return the same buffer and cause ByteBuffer.put(self) to fail on API25.
    if(!input.hasRemaining())return;
    final long started=System.nanoTime();
    final int size=input.remaining(),channels=inputAudioFormat.channelCount,frameBytes=channels*2;
    final int level=Math.max(100,Math.min(300,percent));
    ByteBuffer out=replaceOutputBuffer(size);
    long clipped=0;
    if(level==100){
      out.put(input);
    }else{
      // VLC-style linear software amplification. Peaks above full scale clip,
      // as they do with VLC's software volume; stats below quantify clipping.
      // One bulk native-memory transfer each way; avoid a Java ByteBuffer
      // bounds/order dispatch for every PCM sample on low-end ARMv7 TVs.
      final int count=size/2;
      if(samples.length<count)samples=new short[count];
      input.asShortBuffer().get(samples,0,count);
      final float gain=level/100f;
      for(int i=0;i<count;i++){
        float amplified=samples[i]*gain;
        if(amplified>32767f){samples[i]=32767;clipped++;}
        else if(amplified< -32768f){samples[i]=-32768;clipped++;}
        else samples[i]=(short)Math.round(amplified);
      }
      out.asShortBuffer().put(samples,0,count);
      input.position(input.position()+count*2);out.position(count*2);
    }
    out.flip();
    recordStats(level,channels,size/frameBytes,clipped,System.nanoTime()-started);
  }
  private void recordStats(int level,int channels,long frames,long clipped,long elapsedNs){
    if(!BetaLog.active())return;
    long now=android.os.SystemClock.elapsedRealtime();
    if(statsStartMs==0)statsStartMs=now;
    statFrames+=frames;statClipped+=clipped;statProcessNs+=elapsedNs;maxBlockNs=Math.max(maxBlockNs,elapsedNs);
    long span=now-statsStartMs;
    if(span>=2000){
      long clipPermille=statFrames>0?1000L*statClipped/(statFrames*channels):0;
      BetaLog.event("boost_audio_stats","percent="+level+" linearGain="+(level/100f)+" channels="+channels+" sampleRate="+inputAudioFormat.sampleRate+" frames="+statFrames+" clippedSamples="+statClipped+" clippedPermille="+clipPermille+" processingMs="+(statProcessNs/1000000)+" maxBlockMs="+(maxBlockNs/1000000)+" windowMs="+span);
      statsStartMs=now;statFrames=statClipped=statProcessNs=maxBlockNs=0;
    }
  }
  protected void onFlush(){statsStartMs=statFrames=statClipped=statProcessNs=maxBlockNs=0;}
}
