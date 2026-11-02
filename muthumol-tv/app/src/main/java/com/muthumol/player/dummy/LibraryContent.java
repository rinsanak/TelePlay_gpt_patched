package com.muthumol.player.dummy;
import android.content.*;import android.widget.*;import java.util.*;import java.util.regex.*;
/** Presentation filtering, including old saved entries; never deletes source media. */
final class LibraryContent {
 static final long MB=1048576L;
 static boolean visible(Movie m){return visible(null,m);}
 static boolean visible(Context c,Movie m){SharedPreferences p=c==null?null:c.getSharedPreferences("content-filter",0);boolean episode=MediaTitle.parse(m.title).episodic();int seconds=m.duration;
  if(seconds<=0&&c!=null)seconds=(int)(c.getSharedPreferences("player",0).getLong("duration:"+m.cacheIdentity(),0)/1000);
  int minSeconds=p==null?(episode?180:1200):p.getInt(episode?"episodeSeconds":"filmSeconds",episode?180:1200);
  long minimum=(p==null?(episode?50:400):p.getInt(episode?"episodeMB":"filmMB",episode?50:400))*MB;
  if(seconds>0)return seconds>=minSeconds;
  String name=m.title.replaceAll("[._]"," ");Matcher ep=MediaTitle.EP.matcher(name),year=MediaTitle.YEAR.matcher(name);int end=ep.find()?ep.end():year.find()?year.end():-1;
  if(end>=0&&Pattern.compile("(?i)\\b(trailer|teaser|sample|promo|shorts?|reels?|status|featurette)\\b").matcher(name.substring(end)).find())return false;
  if(m.size>0)return m.size>=minimum;
  String title=MediaTitle.normalize(m.title);return !title.isEmpty()&&!title.equals("telegram video")&&!title.equals("video");
 }
 static void settings(MainActivity a){SharedPreferences p=a.getSharedPreferences("content-filter",0);Ui.menu(a,"Movie / episode filters",new String[]{"Movie minimum duration · "+p.getInt("filmSeconds",1200)/60+" min","Episode minimum duration · "+p.getInt("episodeSeconds",180)/60+" min","Movie minimum size when duration unknown · "+p.getInt("filmMB",400)+" MB","Episode minimum size when duration unknown · "+p.getInt("episodeMB",50)+" MB","Review hidden videos"},n->{if(n==4){review(a);return;}String key=new String[]{"filmSeconds","episodeSeconds","filmMB","episodeMB"}[n];EditText input=Ui.input(a,n<2?"Minimum minutes (0–240)":"Minimum MB (0–10000)",false);input.setInputType(2);input.setText(""+(p.getInt(key,new int[]{1200,180,400,50}[n])/(n<2?60:1)));Ui.dialog(a).setTitle("Library limit").setView(input).setPositiveButton("Save",(d,w)->{try{int v=Integer.parseInt(input.getText().toString());if(v<0||v>(n<2?240:10000))throw new IllegalArgumentException();p.edit().putInt(key,v*(n<2?60:1)).apply();a.shelfKey=null;a.extraShelfKey="";a.changed();}catch(Exception e){Ui.message(a,"Limit","Enter a number in the shown range.");}}).setNegativeButton("Cancel",null).show();});}
 static void review(MainActivity a){List<Movie> hidden=new ArrayList<>();for(Movie m:a.libraryMovies())if(!visible(a,m))hidden.add(m);if(hidden.isEmpty()){Ui.message(a,"Hidden videos","No videos are hidden in the loaded pages.");return;}String[] labels=new String[hidden.size()];for(int i=0;i<labels.length;i++)labels[i]=hidden.get(i).title+" · "+MainActivity.size(hidden.get(i).size);Ui.menu(a,"Hidden in loaded pages · "+hidden.size(),labels,n->a.movieOptions(hidden.get(n)));}
}
