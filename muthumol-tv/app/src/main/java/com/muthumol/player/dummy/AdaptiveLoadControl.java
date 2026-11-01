package com.muthumol.player.dummy;

import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class AdaptiveLoadControl extends DefaultLoadControl {
  private final StreamStats stats;
  private final int memoryLimit;
  private boolean loading;
  AdaptiveLoadControl(StreamStats stats,int memoryLimit) {
    super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE), 15000, 30000, 1500, 4000,
        memoryLimit, false, 0, false);
    this.stats = stats;
    this.memoryLimit=memoryLimit;
  }
  @Override public boolean shouldContinueLoading(Parameters p) {
    long buffered = (long)(p.bufferedDurationUs / Math.max(.1f, p.playbackSpeed));
    // Five minutes ahead lives on disk; keep only a short compressed RAM queue.
    long target = 30000000L;
    if (getAllocator().getTotalBytesAllocated() >= memoryLimit && buffered >= 1000000) loading = false;
    else if (buffered < target / 2) loading = true;
    else if (buffered >= target) loading = false;
    return loading;
  }
  @Override public boolean shouldStartPlayback(Parameters p) {
    long buffered = (long)(p.bufferedDurationUs / Math.max(.1f, p.playbackSpeed));
    long required = p.rebuffering ? 4000000L : 1500000;
    return buffered >= required || (buffered >= 1000000 && getAllocator().getTotalBytesAllocated() >= memoryLimit);
  }
}
