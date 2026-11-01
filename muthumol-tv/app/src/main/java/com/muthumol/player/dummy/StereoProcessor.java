package com.muthumol.player.dummy;
import java.nio.ByteBuffer;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;

/** PCM downmix with bulk buffer access, avoiding per-sample direct-buffer calls. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class StereoProcessor extends BaseAudioProcessor {
  private float[] matrix;
  private short[] source=new short[0],mixed=new short[0];
  @Override protected AudioFormat onConfigure(AudioFormat f)throws UnhandledAudioFormatException {
    if(f.encoding!=C.ENCODING_PCM_16BIT||f.channelCount<1||f.channelCount>8)throw new UnhandledAudioFormatException(f);
    if(f.channelCount==2)return AudioFormat.NOT_SET; // Bit-exact bypass, no identity mix.
    matrix=PlaybackPolicy.stereoMatrix(f.channelCount);
    return new AudioFormat(f.sampleRate,2,f.encoding);
  }
  @Override public void queueInput(ByteBuffer input){
    if(!input.hasRemaining())return;
    int channels=inputAudioFormat.channelCount,frames=input.remaining()/(channels*2),count=frames*channels;
    if(source.length<count)source=new short[count];if(mixed.length<frames*2)mixed=new short[frames*2];
    input.asShortBuffer().get(source,0,count);
    for(int f=0;f<frames;f++){
      float left=0,right=0;int base=f*channels;
      for(int c=0;c<channels;c++){short sample=source[base+c];left+=sample*matrix[c*2];right+=sample*matrix[c*2+1];}
      // Match Media3's PCM16 mixer: clamp, then truncate rather than round.
      mixed[f*2]=(short)Math.max(-32768f,Math.min(32767f,left));
      mixed[f*2+1]=(short)Math.max(-32768f,Math.min(32767f,right));
    }
    ByteBuffer out=replaceOutputBuffer(frames*4);out.asShortBuffer().put(mixed,0,frames*2);out.position(frames*4);out.flip();
    input.position(input.position()+count*2);
  }
}
