package com.muthumol.player.dummy;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class PlaybackLaunchTest {
  @Test public void playbackLaunchDoesNotWaitForBackgroundProgressSync(){
    List<String> order=new ArrayList<>();
    WatchSync.launchImmediately(()->order.add("sync-started"),()->order.add("playback-launched"));
    assertEquals(Arrays.asList("sync-started","playback-launched"),order);
  }
}
