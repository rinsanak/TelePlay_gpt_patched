package com.muthumol.player.dummy;

/** Bounded decisions shared by buffering, stream requests and recovery. */
final class PlaybackPolicy {
  static final long CACHE_LIMIT = 512L * 1024 * 1024;
  static final int MEMORY_LIMIT = 96 * 1024 * 1024;
  static final int PRELOAD_SECONDS=300;
  static final int BACK_SECONDS=120;
  static final int MAX_RETRIES = 3;
  static final long RANGE_STALL_TIMEOUT_MS=15000;
  static final int RANGE_STALL_RETRIES=2;
  static boolean defaultAsyncCodec(int sdk){return sdk<=25||sdk>=31;}
  static int memoryBudget(int memoryClass,long available,boolean low) {
    long heap=Math.max(8L*1024*1024,(long)memoryClass*1024*1024/3);
    long free=available>0?Math.max(8L*1024*1024,available/8):heap;
    return (int)Math.min(low?16L*1024*1024:MEMORY_LIMIT,Math.min(heap,free));
  }
  static int targetSeconds(long throughputBits, long mediaBits, int stalls) {
    return PRELOAD_SECONDS;
  }
  static long preloadBytes(long mediaBits,long diskBudget) {
    long rate=mediaBits>0 ? mediaBits : 6000000;
    return Math.max(2L*1024*1024,Math.min(diskBudget,rate/8*PRELOAD_SECONDS));
  }
  static long retryDelayMs(int attempt) { return 1000L << Math.max(0, Math.min(3, attempt - 1)); }
  static long requestBytes(long throughputBits) {
    return Math.max(2L * 1024 * 1024, Math.min(8L * 1024 * 1024, throughputBits / 8 * 3));
  }
  static float[] stereoMatrix(int channels) {
    if (channels < 1 || channels > 8) throw new IllegalArgumentException("Unsupported channel count");
    float[] m = new float[channels * 2];
    if (channels == 1) { m[0] = m[1] = 1; return m; }
    m[0] = 1; m[3] = 1;
    if (channels == 3 || channels >= 5) { m[4] = m[5] = .7071f; }
    if (channels == 4) { m[4] = .7071f; m[7] = .7071f; }
    if (channels == 5) { m[6] = .7071f; m[9] = .7071f; }
    if (channels >= 6) { m[6] = m[7] = .25f; m[8] = .7071f; m[11] = .7071f; }
    if (channels == 7) { m[12] = m[13] = .5f; }
    if (channels == 8) { m[12] = .7071f; m[15] = .7071f; }
    // RMS normalization preserves more dialog/front-channel level than the old
    // sum-of-amplitudes normalization while keeping correlated multichannel
    // mixes from gaining level just from the downmix.
    float leftPower = 0, rightPower = 0;
    for (int i = 0; i < channels; i++) { leftPower += m[i*2]*m[i*2]; rightPower += m[i*2+1]*m[i*2+1]; }
    float scale = (float)Math.sqrt(Math.max(1, Math.max(leftPower, rightPower)));
    for (int i = 0; i < m.length; i++) m[i] /= scale;
    return m;
  }
}
