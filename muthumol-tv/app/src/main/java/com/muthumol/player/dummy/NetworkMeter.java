package com.muthumol.player.dummy;

/** Rolling media download payload rate. Disk reads are deliberately excluded. */
final class NetworkMeter {
  private final long[] times=new long[40],bytes=new long[40];
  private int cursor;
  private long started=-1,total;
  synchronized void reset(){java.util.Arrays.fill(times,0);java.util.Arrays.fill(bytes,0);cursor=0;started=-1;total=0;}
  synchronized void start(long now){if(started<0)started=now;}
  synchronized void add(long count,long now) {
    if(count<=0)return;
    if(started<0)started=now;
    int slot=cursor%times.length;
    if(times[slot]/100==now/100)bytes[slot]+=count;
    else {cursor=(cursor+1)%times.length;times[cursor]=now;bytes[cursor]=count;}
    total+=count;
  }
  synchronized long bitsPerSecond(long now) {
    if(started<0)return 0;
    long count=0;
    for(int i=0;i<times.length;i++)if(bytes[i]>0 && now-times[i]<2000 && now>=times[i])count+=bytes[i];
    return count*8000/Math.max(100,Math.min(2000,now-started));
  }
  synchronized long totalBytes(){return total;}
}
