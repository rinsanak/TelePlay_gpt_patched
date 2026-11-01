package com.muthumol.player.dummy;

/** Network windows are independent of the much smaller on-disk blocks. */
final class DownloadWindow {
  long from=-1,to=-1,persistedTo=-1;
  boolean contains(long position){return from>=0&&position>=from&&position<to;}
  void start(long position,long length){from=position;to=position+length;persistedTo=position;}
  void copied(long position,int count){if(position<=persistedTo&&position+count>persistedTo)persistedTo=position+count;}
  boolean consumed(){return to>=0&&persistedTo>=to;}
  void reset(){from=to=persistedTo=-1;}
  static long bytes(long fileRemaining,long diskBudget){
    return Math.min(fileRemaining,Math.max(1024*1024,Math.min(16L*1024*1024,diskBudget/8)));
  }
}
