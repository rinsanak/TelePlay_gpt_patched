package androidx.media3.decoder.ffmpeg;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.decoder.*;
import java.nio.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Optional native/JNI smoke tests; run with a matching host verification library and fixtures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=25)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class AudioNativeSmokeTest {
  @Before public void fixtureAvailable(){Assume.assumeTrue(System.getProperty("muthumol.audioFixtures")!=null);}
  @Test public void commonAudioFormatsHaveSoftwareDecoders() {
    assertTrue(FfmpegLibrary.isAvailable());
    for(String mime:new String[]{MimeTypes.AUDIO_AAC,MimeTypes.AUDIO_AC3,MimeTypes.AUDIO_E_AC3,
      MimeTypes.AUDIO_DTS,MimeTypes.AUDIO_TRUEHD,MimeTypes.AUDIO_MPEG,MimeTypes.AUDIO_FLAC,
      MimeTypes.AUDIO_VORBIS,MimeTypes.AUDIO_OPUS,MimeTypes.AUDIO_ALAC,MimeTypes.AUDIO_AMR_NB,MimeTypes.AUDIO_AMR_WB})
      assertTrue(mime,FfmpegLibrary.supportsFormat(mime));
  }
  @Test public void eac3SixChannelsProduceAudiblePcm() throws Exception {decode("eac3",MimeTypes.AUDIO_E_AC3,6);}
  @Test public void aacProducesAudiblePcm() throws Exception {decode("aac",MimeTypes.AUDIO_AAC,2);}
  @Test public void mp3ProducesAudiblePcm() throws Exception {decode("mp3",MimeTypes.AUDIO_MPEG,2);}
  void decode(String ext,String mime,int channels) throws Exception {
    Format.Builder format=new Format.Builder().setSampleMimeType(mime).setSampleRate(48000).setChannelCount(channels);
    if(MimeTypes.AUDIO_AAC.equals(mime))format.setInitializationData(java.util.Collections.singletonList(new byte[]{0x11,(byte)0x90}));
    FfmpegAudioDecoder decoder=new FfmpegAudioDecoder(format.build(),16,16,65536,false);
    try {
      for(int i=0;i<6;i++) {
        byte[] packet=Files.readAllBytes(Paths.get(System.getProperty("muthumol.audioFixtures"),ext+"-"+i+".packet"));
        DecoderInputBuffer in=decoder.dequeueInputBuffer();assertNotNull(in);in.ensureSpaceForWrite(packet.length);in.data.put(packet);in.flip();in.timeUs=i*32000L;decoder.queueInputBuffer(in);
      }
      boolean audible=false;long deadline=System.nanoTime()+5000000000L;
      while(!audible && System.nanoTime()<deadline) {
        SimpleDecoderOutputBuffer out=decoder.dequeueOutputBuffer();
        if(out==null){Thread.sleep(10);continue;}
        ShortBuffer pcm=out.data.order(ByteOrder.nativeOrder()).asShortBuffer();while(pcm.hasRemaining())if(Math.abs((int)pcm.get())>8)audible=true;
        out.release();
      }
      assertTrue("Decoded PCM must contain non-zero audio",audible);assertEquals(channels,decoder.getChannelCount());assertEquals(48000,decoder.getSampleRate());
    } finally {decoder.release();}
  }
}
