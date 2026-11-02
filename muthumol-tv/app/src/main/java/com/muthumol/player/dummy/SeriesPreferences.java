package com.muthumol.player.dummy;
import android.content.*;
final class SeriesPreferences {
 static String key(Movie m,String setting){MediaTitle t=MediaTitle.parse(m.title);return "series:"+MediaTitle.normalize(t.name)+":"+t.year+":"+setting;}
 static void restore(Context c,Movie m){if(!MediaTitle.parse(m.title).episodic())return;SharedPreferences s=c.getSharedPreferences("series-preferences",0),p=c.getSharedPreferences("player",0);SharedPreferences.Editor e=p.edit();for(String k:new String[]{"audioLanguage","subtitleLanguage"})if(s.contains(key(m,k)))e.putString(k,s.getString(key(m,k),""));if(s.contains(key(m,"tvSpeed")))e.putFloat("tvSpeed",s.getFloat(key(m,"tvSpeed"),1));e.apply();}
 static void save(Context c,Movie m){if(m==null||!MediaTitle.parse(m.title).episodic())return;SharedPreferences s=c.getSharedPreferences("series-preferences",0),p=c.getSharedPreferences("player",0);SharedPreferences.Editor e=s.edit();for(String k:new String[]{"audioLanguage","subtitleLanguage"})e.putString(key(m,k),p.getString(k,""));e.putFloat(key(m,"tvSpeed"),p.getFloat("tvSpeed",1));e.apply();}
}
