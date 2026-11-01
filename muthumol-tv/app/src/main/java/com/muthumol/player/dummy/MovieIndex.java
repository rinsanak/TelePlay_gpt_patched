package com.muthumol.player.dummy;
import java.io.*;
import org.json.*;
import androidx.media3.extractor.SeekMap;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class MovieIndex {
  final File file;final long length;
  volatile long durationUs;
  volatile long[] times=new long[0],positions=new long[0];
  MovieIndex(File directory,long length){file=new File(directory,"seek-index.json");this.length=length;try{JSONObject j=new JSONObject(new String(SmallFileIO.read(file,2*1024*1024),java.nio.charset.StandardCharsets.UTF_8));if(j.getLong("length")!=length)return;durationUs=j.getLong("duration");JSONArray a=j.getJSONArray("points");long[] t=new long[a.length()],p=new long[a.length()];for(int i=0;i<a.length();i++){t[i]=a.getJSONArray(i).getLong(0);p[i]=a.getJSONArray(i).getLong(1);}times=t;positions=p;}catch(Exception ignored){}}
  synchronized void capture(SeekMap map){long duration=map.getDurationUs();if(duration>0)durationUs=duration;if(!map.isSeekable()||duration<=0)return;try{int count=(int)Math.min(20000,duration/5000000L+2);long[] t=new long[count],p=new long[count];JSONArray a=new JSONArray();for(int i=0;i<count;i++){long at=Math.min(duration,i*5000000L);t[i]=at;p[i]=Math.max(0,Math.min(length,map.getSeekPoints(at).first.position));a.put(new JSONArray().put(t[i]).put(p[i]));}JSONObject j=new JSONObject().put("length",length).put("duration",duration).put("points",a);File tmp=new File(file.getParentFile(),"seek-index.tmp");SmallFileIO.write(tmp,j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));if(tmp.renameTo(file)){times=t;positions=p;}}catch(Exception ignored){}}
  long byteAt(long us,boolean after){long[] t=times,p=positions;if(t.length==0||p.length!=t.length)return -1;int n=java.util.Arrays.binarySearch(t,us);if(n<0)n=after?-n-1:-n-2;n=Math.max(0,Math.min(p.length-1,n));return us>=durationUs?length:p[n];}
}
