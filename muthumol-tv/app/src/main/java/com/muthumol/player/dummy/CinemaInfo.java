package com.muthumol.player.dummy;
import java.util.Locale;
/** Pure presentation helpers; no player or network work. */
final class CinemaInfo {
  static long clamp(long position,long duration){return Math.max(0,duration>0?Math.min(position,duration):position);}
  static int progress(long position,long duration){return duration<=0?0:(int)Math.min(1000,clamp(position,duration)*1000/duration);}
  static String time(long ms){long seconds=Math.max(0,ms)/1000;return MainActivity.duration(seconds);}
  static String resume(long position,long duration){position=clamp(position,duration);if(position==0)return "Start your movie";return "Continue at "+time(position)+(duration>0?"  ·  "+(long)Math.ceil(Math.max(0,duration-position)/60000d)+" min left":"  ·  Saved position");}
  static String state(boolean hasMovie,boolean failed,boolean complete,long cached,boolean prepared,boolean downloading){if(!hasMovie)return "Choose a movie";if(failed)return "Retry playback";if(complete)return "Offline ready";if(cached>0)return (downloading?"Downloading":prepared?"Ready to stream":"Partially cached")+"  ·  "+MainActivity.size(cached)+" cached";return downloading?"Downloading":prepared?"Ready to stream":"Getting ready";}
  static boolean error(String note){if(note==null)return false;String n=note.toLowerCase(Locale.ROOT);return n.contains("failed")||n.contains("error")||n.contains("will retry");}
}
