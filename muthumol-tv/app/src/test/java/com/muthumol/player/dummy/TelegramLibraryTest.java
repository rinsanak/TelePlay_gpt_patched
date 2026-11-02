package com.muthumol.player.dummy;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.drinkless.tdlib.TdApi;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class TelegramLibraryTest {
  TelegramLibrary library;
  @After public void close(){if(library!=null)library.close();}
  void drain()throws Exception{library.worker.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();}
  static Object field(Object[] fields,String name){for(int i=0;i<fields.length;i+=2)if(name.equals(fields[i]))return fields[i+1];return null;}
  static TdApi.Chat chat(long id){TdApi.Chat c=new TdApi.Chat();c.id=id;c.title="Chat "+id;return c;}
  static TdApi.Message video(long chat,int id){TdApi.Message m=ChannelScanTest.movie(id,id,"Film "+id+".mkv","media"+id);m.chatId=chat;m.date=id;return m;}
  @Test public void searchesEveryChatIncludingArchiveAndLoadsBothMediaTypes()throws Exception{
    List<String> filters=new ArrayList<>();
    library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{
      if(name.equals("GetChat"))return chat((long)field(args,"chatId"));
      assertEquals("SearchMessages",name);assertNull(field(args,"chatList"));
      String type=TelegramBridge.type(field(args,"filter"));filters.add(type);
      TdApi.Message m=type.equals("SearchMessagesFilterVideo")?video(-100,1):ChannelScanTest.subtitle(2,2,"Movie.mkv");m.chatId=-200;
      return new TdApi.FoundMessages(1,new TdApi.Message[]{m},"");
    });
    library.browse(0,"");drain();assertEquals(2,library.items().size());assertEquals(2,filters.size());assertFalse(library.hasMore());assertTrue(library.chats.containsKey(-200L));
  }
  @Test public void cursorFromOneFilterDoesNotSkipTheOtherFilterAndDuplicateFileIsFolded()throws Exception{
    List<String> offsets=new ArrayList<>();
    library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{
      if(name.equals("GetChat"))return chat((long)field(args,"chatId"));
      String type=TelegramBridge.type(field(args,"filter")),offset=(String)field(args,"offset");offsets.add(type+":"+offset);
      if(type.equals("SearchMessagesFilterDocument"))return new TdApi.FoundMessages(1,new TdApi.Message[]{video(-3,1)},"");
      return new TdApi.FoundMessages(2,new TdApi.Message[]{video(-2,offset.isEmpty()?1:3)},offset.isEmpty()?"video-page-2":"");
    });
    library.browse(0,"");drain();assertEquals(1,library.items().size());library.loadMore();drain();assertEquals(2,library.items().size());
    assertEquals(Arrays.asList("SearchMessagesFilterVideo:","SearchMessagesFilterDocument:","SearchMessagesFilterVideo:video-page-2"),offsets);
  }
  @Test public void documentErrorPreservesBothCursorsForRetry()throws Exception{
    final boolean[] fail={true};
    library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{
      if(name.equals("GetChat"))return chat((long)field(args,"chatId"));
      if(TelegramBridge.type(field(args,"filter")).equals("SearchMessagesFilterDocument")&&fail[0])return new TdApi.Error(429,"FLOOD_WAIT_3");
      return new TdApi.FoundMessages(1,new TdApi.Message[]{video(-1,1)},"next");
    });
    library.browse(0,"");drain();assertEquals("",library.videoOffset);assertTrue(library.items().isEmpty());assertFalse(library.loading);assertFalse(library.error.isEmpty());
    fail[0]=false;library.loadMore();drain();assertEquals(1,library.items().size());assertEquals("next",library.videoOffset);
  }
  @Test public void chatBrowseUsesReturnedMessageCursorRatherThanGlobalOffsets()throws Exception{
    List<Long> from=new ArrayList<>();
    library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{
      if(name.equals("GetChat"))return chat((long)field(args,"chatId"));
      assertEquals("SearchChatMessages",name);assertEquals(-100L,field(args,"chatId"));
      long cursor=(long)field(args,"fromMessageId");from.add(cursor);
      return new TdApi.FoundChatMessages(2,new TdApi.Message[]{video(-100,cursor==0?9:4)},cursor==0?8:0);
    });
    library.browse(-100,"");drain();library.loadMore();drain();assertEquals(Arrays.asList(0L,0L,8L,8L),from);assertFalse(library.hasMore());assertEquals(2,library.items().size());
  }
  @Test public void lateSearchResultsCannotReplaceTheNewSearch()throws Exception{
    library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{
      if(name.equals("GetChat"))return chat((long)field(args,"chatId"));
      return new TdApi.FoundMessages(1,new TdApi.Message[]{video(-1,"new".equals(field(args,"query"))?2:1)},"");
    });
    library.browse(0,"old");library.worker.submit(()->{}).get(5,TimeUnit.SECONDS);library.browse(0,"new");drain();
    assertEquals(1,library.items().size());assertEquals("Film 2.mkv",library.items().get(0).title);
  }
  @Test public void nonChannelChatsAndTheArchiveAreRetained()throws Exception{
    List<String> lists=new ArrayList<>();
    library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{
      if(name.equals("LoadChats")){lists.add(TelegramBridge.type(field(args,"chatList")));return new TdApi.Error(404,"All chats loaded");}
      if(name.equals("GetChats"))return new TdApi.Chats(1,new long[]{TelegramBridge.type(field(args,"chatList")).equals("ChatListMain")?1:2});
      return chat((long)field(args,"chatId"));
    });
    library.loadChats();drain();library.loadChats();drain();assertEquals(Arrays.asList("ChatListMain","ChatListArchive"),lists);assertEquals(2,library.chats.size());assertTrue(library.mainDone&&library.archiveDone);
  }
  @Test public void chatGridGroupsShowsAndReturnsFromSeasonsToThatGrid()throws Exception{
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create();
    MainActivity activity=controller.get();
    try{
      activity.accountLibrary.close();
      library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{
        if(name.equals("GetChat"))return chat(-100);
        TdApi.Message first=video(-100,20);((TdApi.MessageVideo)first.content).video.fileName="LOST.S01E01.mkv";
        TdApi.Message second=video(-100,21);((TdApi.MessageVideo)second.content).video.fileName="LOST.S02E01.mkv";
        return new TdApi.FoundChatMessages(2,new TdApi.Message[]{first,second},0);
      });
      activity.accountLibrary=library;library.listener=activity;activity.openGrid(-100);drain();
      android.widget.GridLayout grid=(android.widget.GridLayout)activity.gridBody.getChildAt(0);
      assertEquals(1,grid.getChildCount());assertEquals("videos",activity.screen);
      grid.getChildAt(0).performClick();assertEquals("seasons",activity.screen);
      assertEquals(2,TvLibrary.seasons(activity.seriesMovies(),activity.browsingSeries).size());
      activity.onBackPressed();assertEquals("videos",activity.screen);assertEquals(-100,activity.browseChat);
    }finally{controller.destroy();}
  }
  @Test public void openingSeriesAutomaticallyDrainsEveryPageWithoutLoadMore()throws Exception{
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
    try{activity.accountLibrary.close();List<String> pages=new ArrayList<>();library=new TelegramLibrary(RuntimeEnvironment.getApplication(),(name,args)->{if(name.equals("GetChat"))return chat(-100);String filter=TelegramBridge.type(field(args,"filter"));if(filter.equals("SearchMessagesFilterDocument"))return new TdApi.FoundMessages(0,new TdApi.Message[0],"");String offset=(String)field(args,"offset");pages.add(offset);int page=offset.isEmpty()?0:Integer.parseInt(offset);List<TdApi.Message> found=new ArrayList<>();for(int i=page*40+1;i<=Math.min(117,(page+1)*40);i++){TdApi.Message message=video(-100,i);((TdApi.MessageVideo)message.content).video.fileName=String.format(Locale.US,"Lost.S01E%03d.1080p.mkv",i);found.add(message);}return new TdApi.FoundMessages(117,found.toArray(new TdApi.Message[0]),page<2?""+(page+1):"");});activity.accountLibrary=library;Movie selected=Movie.fromMessage(video(-100,1),-100);selected.title="Lost.S01E001.mkv";activity.openSeries(selected);for(int i=0;i<5;i++){activity.seriesLibrary.worker.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();}assertEquals(Arrays.asList("","1","2"),pages);assertFalse(activity.seriesLibrary.hasMore());assertEquals(117,TvLibrary.episodes(activity.seriesMovies(),selected).size());
    }finally{controller.destroy();}
  }

}
