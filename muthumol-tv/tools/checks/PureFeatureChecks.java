package com.muthumol.player.dummy;
import java.io.*;import java.util.*;import java.util.zip.*;import org.json.*;
/** Executes production parsing/filtering code without Android services or network. */
public final class PureFeatureChecks {
 static int count;static void check(boolean value,String label){count++;if(!value)throw new AssertionError(label);}
 static Movie movie(String title,int seconds,long mb){Movie m=new Movie();m.title=title;m.duration=seconds;m.size=mb*1048576;m.identity=title;return m;}
 static byte[] zip(String... names)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){for(String name:names){z.putNextEntry(new ZipEntry(name));z.write("1\n00:00:01,000 --> 00:00:02,000\nHello\n".getBytes("UTF-8"));z.closeEntry();}}return bytes.toByteArray();}
 static byte[] box(String type,byte[] data)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeInt(data.length+8);d.write(type.getBytes("US-ASCII"));d.write(data);return b.toByteArray();}
 static byte[] ebml(byte[] id,byte[] data)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(id);if(data.length>=127)throw new IllegalArgumentException();b.write(128|data.length);b.write(data);return b.toByteArray();}
 static byte[] concat(byte[]... parts)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] part:parts)b.write(part);return b.toByteArray();}
 static List<ChapterReader.Chapter> chapters(byte[] bytes)throws Exception{return ChapterReader.read(new ChapterReader.Source(){public long size(){return bytes.length;}public byte[] read(long at,int length)throws IOException{if(at<0||at+length>bytes.length)throw new IOException();return Arrays.copyOfRange(bytes,(int)at,(int)at+length);}});}
 public static void main(String[] args)throws Exception{
 check(!LibraryContent.visible(movie("Telegram video",22,4)),"short generic clip hidden");
 check(!LibraryContent.visible(movie("LOST S01E01",30,2)),"short episode clip hidden");
 check(LibraryContent.visible(movie("LOST S01E01",2500,40)),"known episode duration overrides size");
 check(!LibraryContent.visible(movie("Movie 2024 Trailer",0,1000)),"unknown-duration trailer hidden");
 check(LibraryContent.visible(movie("The Big Short 2015",7200,400)),"title Short retained");
 check(LibraryContent.visible(movie("Trailer Park Boys S01E01",1400,200)),"show title Trailer retained");
 Movie film=movie("Dune 2021 1080p WEB-DL Malayalam",7200,900),other=movie("Dune 2021 720p BluRay English",7200,500);
 check(VideoCopies.same(film,other),"movie versions grouped");
 check(!VideoCopies.same(film,movie("Dune 1984",7200,900)),"remakes separate");
 check(!VideoCopies.same(movie("Dune",0,900),movie("Dune unknown copy",0,900)),"unknown-year films need manual grouping");
 Movie episode=movie("LOST S01E02 1080p Malayalam",2400,500);
 check(AutoSubtitles.score(episode,"LOST.S01E02.Malayalam.srt","")>0,"exact subtitle episode");
 check(AutoSubtitles.score(episode,"LOST.S01E03.srt","LOST S01E02")<0,"caption cannot override wrong episode");
 check(AutoSubtitles.score(film,"Dune.1984.Malayalam.srt","")<0,"subtitle remake rejected");
 check(AutoSubtitles.score(film,"Dune.2021.WEB-DL.Malayalam.srt","")>AutoSubtitles.score(film,"Dune.2021.BluRay.English.srt",""),"language and release ranking");
 AutoSubtitles.Packed packed=AutoSubtitles.prepare(zip("S01E01.srt","S01E02.srt","OtherShow.S01E02.srt"),episode,"LOST Season 1.zip");
 check(packed!=null&&packed.name.equals("S01E02.srt"),"verified season pack chooses exact episode");
 ByteArrayOutputStream retryZip=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(retryZip)){z.putNextEntry(new ZipEntry("LOST.S01E02.Malayalam.srt"));z.write("<html>bad response</html>".getBytes("UTF-8"));z.closeEntry();z.putNextEntry(new ZipEntry("LOST.S01E02.English.srt"));z.write("1\n00:00:01,000 --> 00:00:02,000\nGood".getBytes("UTF-8"));z.closeEntry();}
 AutoSubtitles.Packed fallback=AutoSubtitles.prepare(retryZip.toByteArray(),episode,"LOST Season 1.zip");check(fallback!=null&&fallback.name.contains("English"),"invalid highest-ranked inner subtitle tries next readable entry");
 check(AutoSubtitles.prepare(zip("S01E02.srt"),episode,"OtherShow Season 1.zip")==null,"anonymous episode cannot match wrong pack");
 check(AutoSubtitles.prepare("<html>--></html>".getBytes("UTF-8"),episode,"LOST.S01E02.srt")==null,"HTML download rejected");
 check(!AutoSubtitles.readable(new byte[0]),"empty subtitles rejected");
 List<Movie> candidates=Arrays.asList(movie("LOST S01E03 1080p English",2400,500),movie("LOST S01E03 720p Malayalam",2400,300));
 check(SeriesCatalog.prefer(candidates,episode)==candidates.get(1),"next episode language before resolution");
 check(SeriesCatalog.episodes(Arrays.asList(episode,movie("LOST S01E02 720p English",2400,300),candidates.get(1)),episode).size()==2,"episode duplicates collapsed");
 check(Arrays.equals(StreamHandoff.range("bytes=10-19",100),new long[]{10,19}),"explicit HTTP range");
 check(Arrays.equals(StreamHandoff.range("bytes=-10",100),new long[]{90,99}),"suffix HTTP range");
 check(Arrays.equals(StreamHandoff.range("bytes=90-999",100),new long[]{90,99}),"range bounded to file");
 for(String invalid:new String[]{"bytes=100-","bytes=20-10","bytes=-0","bytes=1-2,4-5","bad"}){boolean rejected=false;try{StreamHandoff.range(invalid,100);}catch(IOException e){rejected=true;}check(rejected,"invalid range rejected "+invalid);}
 List<SkipSegments.Segment> segments=SkipSegments.parse(new JSONObject("{\"intro\":[{\"start_ms\":1000,\"end_ms\":10000},{\"start_ms\":8000,\"end_ms\":7000}],\"credits\":[{\"start_ms\":50000,\"end_ms\":null}]}"));
 check(segments.size()==2,"invalid skip intervals rejected");check(SkipSegments.active(segments,3000,60000)!=null,"intro active");check(SkipSegments.active(segments,59000,60000)==null,"skip button hidden at end");
 ByteArrayOutputStream payload=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(payload);d.writeLong(0);d.writeByte(2);d.writeLong(10000L*1000);d.writeByte(5);d.writeBytes("Intro");d.writeLong(10000L*12000);d.writeByte(4);d.writeBytes("Main");
 List<ChapterReader.Chapter> ch=chapters(box("moov",box("udta",box("chpl",payload.toByteArray()))));
 check(ch.size()==2&&ch.get(1).time==12000&&ch.get(0).title.equals("Intro"),"MP4 chapters and time units");
 check(chapters(new byte[]{0,0,0,1,109,111,111,118}).isEmpty(),"truncated MP4 extended header bounded");
 ByteArrayOutputStream time=new ByteArrayOutputStream();new DataOutputStream(time).writeLong(12000000000L);
 byte[] atom=ebml(new byte[]{(byte)0xb6},concat(ebml(new byte[]{(byte)0x91},time.toByteArray()),ebml(new byte[]{(byte)0x80},ebml(new byte[]{(byte)0x85},"Main".getBytes("UTF-8")))));
 byte[] mkv=concat(new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3,(byte)0x80,0x18,0x53,(byte)0x80,0x67,(byte)0xff},ebml(new byte[]{0x10,0x43,(byte)0xa7,0x70},ebml(new byte[]{0x45,(byte)0xb9},atom)));
 List<ChapterReader.Chapter> mk=chapters(mkv);check(mk.size()==1&&mk.get(0).time==12000&&mk.get(0).title.equals("Main"),"MKV chapters and nanosecond conversion");
 boolean malformed=false;try{ChapterReader.element(new byte[]{0},0,1);}catch(IOException e){malformed=true;}check(malformed,"invalid EBML rejected");
 JSONObject reply=new JSONObject("{\"subtitles\":[{\"lang\":\"mal\",\"url\":\"https://malayalamsubtitles.org/right.srt\",\"name\":\"LOST S01E02\"},{\"lang\":\"mal\",\"url\":\"https://malayalamsubtitles.org/wrong.srt\",\"name\":\"LOST S01E03\"},{\"lang\":\"mal\",\"url\":\"https://evil.example/test.srt\"}]}");
 check(MsoneSubtitles.hits(episode,reply).size()==1,"MSone wrong episode and untrusted host rejected");
 System.out.println("PASS: "+count+" production-code checks (no device, native codec, provider or UI verification)");
 }
}
