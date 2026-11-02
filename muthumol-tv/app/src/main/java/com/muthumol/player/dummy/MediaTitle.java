package com.muthumol.player.dummy;
import java.util.*;import java.util.regex.*;
/** Filename-only public lookup key; never sends captions, chat ids or Telegram URLs. */
final class MediaTitle {
 final String name,episodeName;final int year,season,episode;
 private MediaTitle(String n,int y,int s,int e,String title){name=n;year=y;season=s;episode=e;episodeName=title;}
 static final Pattern EP=Pattern.compile("(?i)\\b(?:s(\\d{1,2})\\s*e(\\d{1,3})(?:\\s*(?:-|–|,|&)??\\s*e\\d{1,3})?|(\\d{1,2})x(\\d{1,3})(?:\\s*(?:-|–|,|&)??\\s*\\d{1,3})?|season\\s*(\\d{1,2})\\s*(?:episode|ep|e)\\s*(\\d{1,3}))\\b");
 static final Pattern YEAR=Pattern.compile("\\b((?:19|20)\\d{2})\\b");
 static final Pattern TAG=Pattern.compile("(?i)\\b(?:2160p|1080p|720p|480p|4k|uhd|hq|hdrip|web[ -]?dl|webrip|bluray|brrip|dvdrip|hdtv|x26[45]|h[ .]?26[45]|hevc|avc|aac\\d*|ddp?\\+?|eac3|ac3|dts|esub|multi|dual|malayalam|tamil|telugu|hindi|kannada|english|org|proper|repack)\\b");
 static MediaTitle parse(String raw){String s=raw.replaceFirst("(?i)\\.(mkv|mp4|avi|mov|m4v|webm|ts)$","").replaceAll("\\[[^]]*]", " ").replaceAll("[._]"," ").replaceAll("\\s+"," ").trim();Matcher y=YEAR.matcher(s);int year=y.find()?Integer.parseInt(y.group(1)):0;Matcher ep=EP.matcher(s);int season=0,episode=0;String end="",name=s;
  if(ep.find()){String a=ep.group(1)!=null?ep.group(1):ep.group(3)!=null?ep.group(3):ep.group(5),b=ep.group(2)!=null?ep.group(2):ep.group(4)!=null?ep.group(4):ep.group(6);season=Integer.parseInt(a);episode=Integer.parseInt(b);name=s.substring(0,ep.start());end=clean(s.substring(ep.end()));}
  name=clean(name);if(name.isEmpty())name=raw.substring(0,Math.min(80,raw.length()));return new MediaTitle(name,year,season,episode,end);
 }
 static String clean(String raw){Matcher y=YEAR.matcher(raw),tag=TAG.matcher(raw);int cut=raw.length();if(y.find())cut=Math.min(cut,y.start());if(tag.find())cut=Math.min(cut,tag.start());return raw.substring(0,cut).replaceAll("[(){}]"," ").replaceAll("^[\\s\\-–|:]+|[\\s\\-–|:]+$","").replaceAll("\\s+"," ").trim();}
 boolean episodic(){return episode>0;}
 String display(){return name+(episodic()?String.format(Locale.US," · S%02d E%02d",season,episode):year>0?" ("+year+")":"");}
 String key(){return normalize(name)+":"+year+":"+season+":"+episode;}
 static String normalize(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
 static double similarity(String a,String b){a=normalize(a);b=normalize(b);if(a.equals(b))return 1;Set<String> aa=new HashSet<>(Arrays.asList(a.split(" +"))),bb=new HashSet<>(Arrays.asList(b.split(" +")));if(aa.isEmpty()||bb.isEmpty())return 0;Set<String> hit=new HashSet<>(aa);hit.retainAll(bb);return 2.0*hit.size()/(aa.size()+bb.size());}
}
