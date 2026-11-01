package com.muthumol.player.dummy;
import androidx.media3.exoplayer.audio.AudioSink;
import java.lang.reflect.*;
/** Numeric-only first-buffer/clock diagnostics; never modifies timestamps or PCM. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class AudioTimingSink implements InvocationHandler {
  final AudioSink sink;boolean first=true;long lastPosition=Long.MIN_VALUE,lastAt;
  private AudioTimingSink(AudioSink sink){this.sink=sink;}
  static AudioSink wrap(AudioSink sink){return (AudioSink)Proxy.newProxyInstance(AudioSink.class.getClassLoader(),new Class[]{AudioSink.class},new AudioTimingSink(sink));}
  public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
    Object result;try{result=method.invoke(sink,args);}catch(InvocationTargetException e){throw e.getCause();}
    String name=method.getName();
    if(name.equals("flush")||name.equals("reset")){first=true;lastPosition=Long.MIN_VALUE;}
    if(name.equals("handleBuffer")&&Boolean.TRUE.equals(result)&&first){first=false;BetaLog.event("audio_first_pcm","timestampUs="+args[1]);}
    if(name.equals("getCurrentPositionUs")&&result instanceof Long){
      long position=(Long)result,now=android.os.SystemClock.elapsedRealtime();
      if(position!=Long.MIN_VALUE){if(lastPosition!=Long.MIN_VALUE&&Math.abs(position-lastPosition)>Math.max(2000000L,(now-lastAt)*1000+1000000L))BetaLog.event("audio_clock_jump","fromUs="+lastPosition+" toUs="+position+" elapsedMs="+(now-lastAt));lastPosition=position;lastAt=now;}
    }
    return result;
  }
}
