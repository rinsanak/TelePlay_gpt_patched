package com.muthumol.player.dummy;
import java.nio.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
/** Dialogue processing in one PCM pass, before quantisation: centre-aware stereo,
 * gentle presence EQ, slow volume levelling and a linked-channel soft peak limit. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvAudioProcessor extends BaseAudioProcessor {
 volatile int mode=1,percent=100; volatile boolean fold=true,normalize=true;
 private float[] low=new float[0],high=new float[0],frame=new float[0],mixed=new float[0];private double energy=.016,level=1,envelope,gain=1;private int outputs,inputs;private boolean floating;
 protected AudioFormat onConfigure(AudioFormat f)throws UnhandledAudioFormatException {
  if(f.channelCount<1||f.channelCount>32||(f.encoding!=C.ENCODING_PCM_16BIT&&f.encoding!=C.ENCODING_PCM_FLOAT))throw new UnhandledAudioFormatException(f);
  inputs=f.channelCount;outputs=fold&&inputs>2?2:inputs;floating=f.encoding==C.ENCODING_PCM_FLOAT;
  low=new float[outputs];high=new float[outputs];frame=new float[inputs];mixed=new float[outputs];return new AudioFormat(f.sampleRate,outputs,C.ENCODING_PCM_16BIT);
 }
 public void queueInput(ByteBuffer input){if(!input.hasRemaining())return;input.order(ByteOrder.nativeOrder());int frames=input.remaining()/(inputs*(floating?4:2));ByteBuffer out=replaceOutputBuffer(frames*outputs*2).order(ByteOrder.nativeOrder());
  double loStep=1-Math.exp(-2*Math.PI*220/inputAudioFormat.sampleRate),hiStep=1-Math.exp(-2*Math.PI*3800/inputAudioFormat.sampleRate),attack=1-Math.exp(-1.0/(.003*inputAudioFormat.sampleRate)),release=1-Math.exp(-1.0/(.18*inputAudioFormat.sampleRate));
  double measure=1-Math.exp(-1.0/(2*inputAudioFormat.sampleRate)),rise=1-Math.exp(-1.0/(2*inputAudioFormat.sampleRate)),fall=1-Math.exp(-1.0/(.25*inputAudioFormat.sampleRate));
  int selected=mode;double target=Math.max(1,Math.min(3,percent/100.0));
  for(int f=0;f<frames;f++){for(int i=0;i<inputs;i++)frame[i]=floating?finite(input.getFloat()):input.getShort()/32768f;
   java.util.Arrays.fill(mixed,0);if(outputs==inputs)System.arraycopy(frame,0,mixed,0,outputs);else {mixed[0]=frame[0];mixed[1]=frame[1];
    if(inputs==3||inputs>=5){mixed[0]+=frame[2]*(selected==2?1.3f:1.05f);mixed[1]+=frame[2]*(selected==2?1.3f:1.05f);}
    if(inputs==4){mixed[0]+=.5f*frame[2];mixed[1]+=.5f*frame[3];}
    else if(inputs==5){mixed[0]+=.5f*frame[3];mixed[1]+=.5f*frame[4];}
    else if(inputs>=6){mixed[0]+=.12f*frame[3]+.45f*frame[4];mixed[1]+=.12f*frame[3]+.45f*frame[5];for(int i=6;i<inputs;i++)mixed[i%2]+=.35f*frame[i];}
   }
   double square=0;for(float v:mixed)square+=v*v;square/=outputs;if(square>1e-6)energy+=(square-energy)*measure;
   double wanted=Math.max(1,Math.min(3,.12/Math.sqrt(Math.max(energy,1e-9))));if(normalize)level+=(wanted-level)*(wanted>level?rise:fall);else level=1;gain+=(target-gain)*.001;
   double peak=0;for(int i=0;i<outputs;i++){float x=mixed[i];low[i]+=loStep*(x-low[i]);high[i]+=hiStep*(x-high[i]);double voice=selected==2?.32:selected==3?.16:.20;double y=(x+voice*(high[i]-low[i])-.10*low[i])*level*gain;mixed[i]=(float)y;peak=Math.max(peak,Math.abs(y));}
   envelope+=(peak-envelope)*(peak>envelope?attack:release);double threshold=selected==3?.20:selected==2?.40:.56,ratio=selected==3?6:3;double compression=envelope>threshold?Math.pow(threshold/envelope,1-1/ratio):1;
   for(float value:mixed){double y=value*compression;/* linked compressor plus memoryless soft ceiling catches even the first peak */double a=Math.abs(y);if(a>.85)y=Math.copySign(.85+.14*Math.tanh((a-.85)/.14),y);out.putShort((short)Math.round(Math.max(-.99,Math.min(.99,y))*32767));}
  }out.flip();
 }
 static float finite(float v){return Float.isFinite(v)?Math.max(-4,Math.min(4,v)):0;}
 protected void onReset(){energy=.016;level=gain=1;envelope=0;low=high=new float[0];}
}
