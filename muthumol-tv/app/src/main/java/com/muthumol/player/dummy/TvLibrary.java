package com.muthumol.player.dummy;
import android.content.*;import java.util.*;import org.json.*;
/** Local collections contain media references, never account credentials. */
final class TvLibrary {
 final Context context;final SharedPreferences p;TvLibrary(Context c){context=c.getApplicationContext();p=c.getSharedPreferences("tv-library",0);}
 String key(Movie m){return MediaCache.key(m.cacheIdentity());}
 String markKey(Movie m,String group){return group.equals("watched")&&MediaTitle.parse(m.title).episodic()?"episode-watched:"+MediaTitle.parse(m.title).year+":"+SeriesCatalog.episodeKey(m):group+":"+key(m);}
 boolean marked(Movie m,String group){return p.getBoolean(markKey(m,group),p.getBoolean(group+":"+key(m),false));}
 void toggle(Movie m,String group){remember(m);p.edit().putBoolean(markKey(m,group),!marked(m,group)).apply();if(group.equals("watched"))WatchSync.get(context).record(m,m.savedPosition(context),m.duration*1000L,marked(m,group));}
 void remember(Movie m){p.edit().putString("movie:"+key(m),m.json().toString()).putLong("seen:"+key(m),System.currentTimeMillis()).apply();}
 List<Movie> saved(){List<Movie> all=new ArrayList<>();for(Map.Entry<String,?> e:p.getAll().entrySet())if(e.getKey().startsWith("movie:")&&e.getValue() instanceof String)try{all.add(Movie.from(new JSONObject((String)e.getValue())));}catch(Exception ignored){}all.sort((a,b)->Long.compare(p.getLong("seen:"+key(b),0),p.getLong("seen:"+key(a),0)));return all;}
 List<Movie> list(Context c,List<Movie> input,String filter,String query,int sort){LinkedHashMap<String,Movie> map=new LinkedHashMap<>();for(Movie m:input)map.put(m.cacheIdentity(),m);if(!filter.equals("All"))for(Movie m:saved())map.putIfAbsent(m.cacheIdentity(),m);List<Movie> result=new ArrayList<>();String q=MediaTitle.normalize(query);
  for(Movie m:map.values()){MediaTitle t=MediaTitle.parse(m.title);boolean pass=filter.equals("All")||filter.equals("Movies")&&!t.episodic()||filter.equals("Series")&&t.episodic()||filter.equals("Continue")&&m.savedPosition(c)>0||filter.equals("Watch later")&&marked(m,"later")||filter.equals("Watched")&&marked(m,"watched")||filter.equals("Favourites")&&marked(m,"star");if(pass&&matches(m.title,q))result.add(m);}
  if(sort==0&&!q.isEmpty())result.sort(Comparator.comparingInt(m->LibrarySearch.rank(m.title,q)));else if(sort==1)result.sort(Comparator.comparing(m->MediaTitle.parse(m.title).display()));else if(sort==2)result.sort(Comparator.comparingLong(m->m.size));else if(sort==3)result.sort((a,b)->Long.compare(b.size,a.size));return result;
 }
 static boolean matches(String title,String query){return LibrarySearch.rank(title,query)!=Integer.MAX_VALUE;}
 static String seriesKey(Movie m){MediaTitle t=MediaTitle.parse(m.title);return MediaTitle.normalize(t.name)+":"+t.year;}
 static List<Movie> homeEntries(List<Movie> input){LinkedHashMap<String,Movie> entries=new LinkedHashMap<>();for(Movie m:input){String key=MediaTitle.parse(m.title).episodic()?"series:"+seriesKey(m):"movie:"+m.cacheIdentity();entries.putIfAbsent(key,m);}return new ArrayList<>(entries.values());}
 static SortedMap<Integer,List<Movie>> seasons(List<Movie> input,Movie selected){SortedMap<Integer,List<Movie>> result=new TreeMap<>();for(Movie m:episodes(input,selected))result.computeIfAbsent(MediaTitle.parse(m.title).season,k->new ArrayList<>()).add(m);return result;}
 static List<Movie> episodes(List<Movie> movies,Movie current){return SeriesCatalog.episodes(movies,current);}
 static Movie neighbour(List<Movie> movies,Movie current,int direction){List<Movie> list=episodes(movies,current);for(int i=0;i<list.size();i++)if(SeriesCatalog.episodeKey(list.get(i)).equals(SeriesCatalog.episodeKey(current))){int n=i+direction;return n>=0&&n<list.size()?list.get(n):null;}return null;}
}
