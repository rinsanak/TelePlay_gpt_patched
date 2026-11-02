package com.muthumol.player.dummy;

import android.content.*;
import java.io.*;

final class MovieData {
  private MovieData(){}
  static String json(java.util.List<Movie> movies){org.json.JSONArray array=new org.json.JSONArray();for(Movie m:movies)array.put(m.json());return array.toString();}
  static int clearPreferences(Context context)throws IOException {
    SharedPreferences player=context.getSharedPreferences("player",0);
    SharedPreferences.Editor edit=player.edit();int removed=0;
    for(String key:player.getAll().keySet())if(isMovieKey(key)){edit.remove(key);removed++;}
    if(!edit.commit())throw new IOException("Could not reset movie positions/settings");
    if(!context.getSharedPreferences("channel",0).edit().remove("movie").remove("recentMovies").remove("subtitleId")
      .remove("subtitlePath").remove("subtitleType").remove("subtitleName").commit())
      throw new IOException("Could not reset movie details");
    return removed;
  }
  static boolean isMovieKey(String key){
    return key.startsWith("logical-pos:")||key.startsWith("logical-duration:")||key.startsWith("duration:")||key.startsWith("pos:")||key.startsWith("aspect:")||key.startsWith("subdelay:")
      ||key.startsWith("externalSubtitleUri:")||key.startsWith("externalSubtitleMime:")
      ||key.startsWith("externalSubtitleOff:");
  }
  static void clearFiles(Context c)throws IOException {
    MediaCache.clear(c);
    if(!MediaCache.delete(new File(c.getFilesDir(),"channel-subtitles")))throw new IOException("Could not clear movie subtitles");
    File[] files=c.getCacheDir().listFiles();
    if(files!=null)for(File f:files)if(f.getName().startsWith("imported-subtitle.")||f.getName().startsWith("telegram-subtitle."))
      if(!MediaCache.delete(f))throw new IOException("Could not clear imported subtitle");
  }
}
