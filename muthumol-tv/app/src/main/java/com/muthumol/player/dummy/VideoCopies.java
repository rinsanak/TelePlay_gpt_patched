package com.muthumol.player.dummy;
import java.util.*;
/** Stable logical identity; native/file IDs never determine movie title grouping. */
final class VideoCopies {
 static String key(Movie m){if(!m.groupOverride.isEmpty())return "manual:"+m.groupOverride;MediaTitle t=MediaTitle.parse(m.title);String title=MediaTitle.normalize(t.name);if(t.episodic())return "episode:"+title+":"+t.year+":"+t.season+":"+t.episode;if(t.year>0)return "film:"+title+":"+t.year;return "file:"+m.cacheIdentity();}
 static boolean same(Movie a,Movie b){return key(a).equals(key(b))||MediaTitle.parse(a.title).episodic()&&SeriesCatalog.sameShow(a,b)&&SeriesCatalog.episodeKey(a).equals(SeriesCatalog.episodeKey(b));}
 static List<Movie> copies(List<Movie> source,Movie selected){List<Movie> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(Movie m:source)if(same(m,selected)&&seen.add(m.cacheIdentity()))out.add(m);return out;}
 static List<Movie> available(List<Movie> source,Movie selected){List<Movie> base=copies(source,selected);if(base.isEmpty())base=java.util.Collections.singletonList(selected);List<Movie> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(Movie m:base){if(seen.add(m.cacheIdentity()))out.add(m);for(int i=0;i<m.alternatives.length();i++){org.json.JSONObject value=m.alternatives.optJSONObject(i);if(value!=null){Movie copy=Movie.from(value);if(seen.add(copy.cacheIdentity()))out.add(copy);}}}return out;}
 static long position(android.content.Context c,Movie m){android.content.SharedPreferences p=c.getSharedPreferences("player",0);String logical="logical-pos:"+key(m);return p.contains(logical)?p.getLong(logical,0):m.ownSavedPosition(c);}
 static void save(android.content.Context c,Movie m,long position,long duration){c.getSharedPreferences("player",0).edit().putLong("logical-pos:"+key(m),Math.max(0,position)).putLong("logical-duration:"+key(m),Math.max(0,duration)).apply();}
}
