package com.muthumol.player.dummy;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.drinkless.tdlib.TdApi;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class BetaDiagnosticsTest {
  @Test public void documentUsesMatchingTdlibSchema()throws Exception{
    TdApi.InputMessageDocument input=(TdApi.InputMessageDocument)BetaLog.document(new File("/tmp/beta-stream-test.txt"));
    assertTrue(input.document.document instanceof TdApi.InputFileLocal);
    assertEquals("/tmp/beta-stream-test.txt",((TdApi.InputFileLocal)input.document.document).path);
    assertTrue(input.caption.text.contains("diagnostic log"));
  }
  @Test public void loggingIsOptInBoundedAndStopsWriting()throws Exception{
    Context c=RuntimeEnvironment.getApplication();assertFalse(BetaLog.active());BetaLog.init(c);BetaLog.start(c);
    BetaLog.exception("fixture_error",new IllegalArgumentException("decoder failure",new IOException("source failure")));
    for(int i=0;i<10000;i++)BetaLog.event("fixture","numeric="+i+" padding="+new String(new char[100]).replace('\0','x'));
    BetaLog.collecting=false;BetaLog.writer.join(3000);assertFalse(BetaLog.writer.isAlive());assertFalse(BetaLog.active());
    assertTrue(BetaLog.logFile.length()<=BetaLog.MAX_BYTES);long bytes=BetaLog.logFile.length();BetaLog.event("fixture","after stop");assertEquals(bytes,BetaLog.logFile.length());
    String text=new String(java.nio.file.Files.readAllBytes(BetaLog.logFile.toPath()),StandardCharsets.UTF_8);assertTrue(text.contains("summary"));assertTrue(text.contains("fixture_error"));assertTrue(text.contains("Caused by: java.io.IOException"));assertTrue(text.contains("BetaDiagnosticsTest.java:"));
  }
}
