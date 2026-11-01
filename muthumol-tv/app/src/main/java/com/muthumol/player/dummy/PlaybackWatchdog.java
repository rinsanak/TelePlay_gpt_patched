package com.muthumol.player.dummy;

/** Playback progress is independent of background cache/network progress. */
final class PlaybackWatchdog {
  static final long FREEZE_MS=8000;
  private long position=-1,frames=-1,progressAt,frameAt;
  private boolean armed;
  boolean stalled(long now,boolean active,boolean firstFrame,long current,long rendered,long bufferedMs,boolean video) {
    if(!active||!firstFrame){reset(now,current,rendered);return false;}
    if(!armed){reset(now,current,rendered);armed=true;return false;}
    if(current!=position||rendered!=frames)progressAt=now;
    if(rendered!=frames)frameAt=now;
    position=current;frames=rendered;
    // Unknown counters must not cause a video-only false alarm.
    return bufferedMs>=2000&&(now-progressAt>=FREEZE_MS
        ||(video&&rendered>=0&&now-frameAt>=FREEZE_MS));
  }
  void reset(long now,long current,long rendered){position=current;frames=rendered;progressAt=frameAt=now;armed=false;}
}
