package com.muthumol.player.dummy;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=25)
public class ChannelScanTest {
  @Test public void selectsNewestMovieRegardlessOfHistoryArrayOrderAndOnlyItsLaterSubtitle(){
    TdApi.Message old=movie(10,1,"old.mkv","old-identity");
    TdApi.Message oldSubtitle=subtitle(11,2,"old.srt");
    TdApi.Message newest=movie(30,3,"new.mkv","new-identity");
    TdApi.Message newestSubtitle=subtitle(35,4,"new.ass");
    ChannelScan.Result result=ChannelScan.scan(new TdApi.Message[]{newestSubtitle,old,newest,oldSubtitle},-100);
    assertEquals("new.mkv",result.movie.title);
    assertEquals(30,result.movie.messageId);
    assertEquals("new.ass",TelegramBridge.str(result.subtitle,"fileName"));
  }

  @Test public void confirmedEmptyPageDoesNotRetainOldMovie(){
    assertNull(ChannelScan.scan(new TdApi.Message[0],-100).movie);
    TdApi.Message unsupported=new TdApi.Message();unsupported.id=41;unsupported.content=new TdApi.MessageText();
    assertNull(ChannelScan.scan(new TdApi.Message[]{unsupported},-100).movie);
  }

  @Test public void loadedVideosAreSortedDeduplicatedAndSubtitlesStayWithTheirVideo(){
    java.util.List<TdApi.Message> messages=new java.util.ArrayList<>();for(int i=1;i<=14;i++){messages.add(movie(i*10,i,"Movie "+i+".mkv","identity"+i));messages.add(subtitle(i*10+1,100+i,"Subtitle "+i+".srt"));}messages.add(movie(140,14,"Movie 14.mkv","identity14"));java.util.Collections.shuffle(messages,new java.util.Random(42));
    ChannelScan.Result result=ChannelScan.scan(messages.toArray(new TdApi.Message[0]),-100);assertEquals(14,result.recent.size());assertEquals(140,result.recent.get(0).messageId);assertEquals(50,result.recent.get(9).messageId);for(Movie m:result.recent)assertEquals("Subtitle "+m.messageId/10+".srt",TelegramBridge.str(result.subtitles.get(m.cacheIdentity()),"fileName"));
  }
  static TdApi.Message movie(long messageId,int fileId,String name,String unique){
    TdApi.File file=new TdApi.File();file.id=fileId;file.size=1234;file.remote=new TdApi.RemoteFile();file.remote.uniqueId=unique;
    TdApi.Video video=new TdApi.Video();video.fileName=name;video.mimeType="video/x-matroska";video.video=file;
    TdApi.Message message=new TdApi.Message();message.id=messageId;TdApi.MessageVideo content=new TdApi.MessageVideo();content.video=video;message.content=content;return message;
  }
  static TdApi.Message subtitle(long messageId,int fileId,String name){
    TdApi.File file=new TdApi.File();file.id=fileId;file.size=200;
    TdApi.Document document=new TdApi.Document();document.fileName=name;document.mimeType="application/octet-stream";document.document=file;
    TdApi.Message message=new TdApi.Message();message.id=messageId;TdApi.MessageDocument content=new TdApi.MessageDocument();content.document=document;message.content=content;return message;
  }
}
