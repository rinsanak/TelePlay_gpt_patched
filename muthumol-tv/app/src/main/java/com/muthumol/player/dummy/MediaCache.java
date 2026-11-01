package com.muthumol.player.dummy;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

/** Bounded, disk-only read-ahead segments for the last selected movie. */
final class MediaCache {
  private static String activeKey;
  static synchronized void activate(String key){activeKey=key;}
  private static final String PREFIX="read-ahead-";
  private MediaCache(){}
  static String key(String mediaUri) {
    try {
      byte[] hash=MessageDigest.getInstance("SHA-256").digest(mediaUri.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder out=new StringBuilder(hash.length*2);
      for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));
      return out.toString();
    } catch(Exception e) {throw new IllegalStateException("SHA-256 unavailable",e);}
  }
  static File root(Context c){File root=new File(c.getFilesDir(),"video-cache");root.mkdirs();return root;}
  static void migrate(Context c,Movie movie){File old=directory(c.getCacheDir(),movie.legacyUri()),dest=directory(c,movie.cacheIdentity());if(old.isDirectory()&&!dest.exists())old.renameTo(dest);try{clear(c.getCacheDir());}catch(IOException ignored){}}
  static File directory(Context c,String mediaUri) {return directory(root(c),mediaUri);}
  static File directory(File root,String mediaUri) {return new File(root,PREFIX+key(mediaUri));}
  static void keepOnly(Context c,String mediaUri) {keepOnly(root(c),key(mediaUri));}
  static synchronized void keepOnly(File root,String keepKey) {
    if(activeKey!=null&&!activeKey.equals(keepKey))return;
    File[] dirs=root.listFiles(f->f.isDirectory()&&f.getName().startsWith(PREFIX));
    if(dirs==null)return;
    for(File dir:dirs)if(!dir.getName().equals(PREFIX+keepKey))delete(dir);
  }
  static void clear(Context c) throws IOException {clear(root(c));clear(c.getCacheDir());}
  static void clear(File root) throws IOException {
    File[] dirs=root.listFiles(f->f.isDirectory()&&f.getName().startsWith(PREFIX));
    if(dirs==null)return;
    for(File dir:dirs)if(!delete(dir))throw new IOException("Could not clear video cache");
  }
  static long bytes(File root) {
    File[] dirs=root.listFiles(f->f.isDirectory()&&f.getName().startsWith(PREFIX));long total=0;
    if(dirs!=null)for(File dir:dirs){File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile())total+=f.length();}
    return total;
  }
  static boolean delete(File file) {
    if(file.isDirectory()){File[] files=file.listFiles();if(files!=null)for(File child:files)if(!delete(child))return false;}
    return !file.exists()||file.delete();
  }
}
