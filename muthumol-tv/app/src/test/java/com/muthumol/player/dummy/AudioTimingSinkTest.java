package com.muthumol.player.dummy;
import java.nio.*;import java.lang.reflect.*;
import androidx.media3.exoplayer.audio.AudioSink;
import org.junit.Test;import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=25)
public class AudioTimingSinkTest {
 @Test public void diagnosticsKeepPcmTimestampAndClockUnchanged()throws Exception{
  ByteBuffer data=ByteBuffer.allocateDirect(4);long[] timestamp={0};
  AudioSink delegate=(AudioSink)Proxy.newProxyInstance(AudioSink.class.getClassLoader(),new Class[]{AudioSink.class},(p,m,args)->{if(m.getName().equals("handleBuffer")){assertSame(data,args[0]);timestamp[0]=(Long)args[1];return true;}if(m.getName().equals("getCurrentPositionUs"))return 34000000L;return null;});
  AudioSink wrapped=AudioTimingSink.wrap(delegate);assertTrue(wrapped.handleBuffer(data,34000000L,1));assertEquals(34000000L,timestamp[0]);assertEquals(34000000L,wrapped.getCurrentPositionUs(false));wrapped.flush();assertTrue(wrapped.handleBuffer(data,0,1));assertEquals(0,timestamp[0]);
 }
 @Test public void diagnosticsPreserveOriginalExceptions(){
  IllegalStateException failure=new IllegalStateException("test");AudioSink delegate=(AudioSink)Proxy.newProxyInstance(AudioSink.class.getClassLoader(),new Class[]{AudioSink.class},(p,m,args)->{throw failure;});assertSame(failure,assertThrows(IllegalStateException.class,()->AudioTimingSink.wrap(delegate).play()));
 }
}
