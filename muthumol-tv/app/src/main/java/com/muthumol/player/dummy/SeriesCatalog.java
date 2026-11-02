package com.muthumol.player.dummy;
import java.util.*;
import java.util.regex.*;
/** One logical episode with all of its playable copies retained. */
final class SeriesCatalog {
 static boolean sameShow(Movie a,Movie b){MediaTitle x=MediaTitle.parse(a.title),y=MediaTitle.parse(b.title);return x.episodic()&&y.episodic()&&MediaTitle.normalize(x.name).equals(MediaTitle.normalize(y.name))&&(x.year==0||y.year==0||x.year==y.year);}
 static String episodeKey(Movie m){MediaTitle t=MediaTitle.parse(m.title);return MediaTitle.normalize(t.name)+":"+t.season+":"+t.episode;}
 static List<Movie> copies(List<Movie> input,Movie selected){List<Movie> result=new ArrayList<>();Set<String> seen=new HashSet<>();MediaTitle s=MediaTitle.parse(selected.title);for(Movie m:input){MediaTitle t=MediaTitle.parse(m.title);if(sameShow(m,selected)&&s.season==t.season&&s.episode==t.episode&&seen.add(m.cacheIdentity()))result.add(m);}return result;}
 static String quality(Movie m){Matcher match=Pattern.compile("(?i)(2160|1080|720|576|480|360)p|\\b(4k)\\b").matcher(m.title);if(match.find())return match.group(2)!=null?"2160p":match.group(1)+"p";return m.height>0?m.height+"p":"Original";}
 static Movie prefer(List<Movie> copies,Movie previous){Movie best=copies.get(0);int score=Integer.MIN_VALUE;for(Movie m:copies){int rank=quality(m).equals(quality(previous))?100:0;String low=m.title.toLowerCase(Locale.ROOT),old=previous.title.toLowerCase(Locale.ROOT);for(String tag:new String[]{"malayalam","mal","tamil","telugu","hindi","english","eng","hevc","x265","x264"})if(old.contains(tag)&&low.contains(tag))rank+=5;if(m.cacheIdentity().equals(previous.cacheIdentity()))rank+=1000;if(rank>score){best=m;score=rank;}}return best;}
 static List<Movie> episodes(List<Movie> input,Movie selected){SortedMap<String,List<Movie>> groups=new TreeMap<>();for(Movie m:input)if(sameShow(m,selected)){MediaTitle t=MediaTitle.parse(m.title);String k=String.format(Locale.ROOT,"%06d:%06d",t.season,t.episode);groups.computeIfAbsent(k,v->new ArrayList<>()).add(m);}List<Movie> result=new ArrayList<>();for(List<Movie> group:groups.values())result.add(prefer(group,selected));return result;}
 private SeriesCatalog(){}
}
