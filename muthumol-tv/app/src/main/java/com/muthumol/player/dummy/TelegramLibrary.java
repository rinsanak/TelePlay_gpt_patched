package com.muthumol.player.dummy;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import org.drinkless.tdlib.TdApi;
import org.json.*;

/** Account-wide media browsing. Video and document cursors are independent. */
final class TelegramLibrary {
  interface Listener { void libraryChanged(); }
  interface Calls { TdApi.Object call(String name,Object... fields) throws Exception; }
  static final int PAGE=50;
  final Handler ui=new Handler(Looper.getMainLooper());
  final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Telegram library");t.setDaemon(true);return t;});
  final Calls calls; final Context context;
  final LinkedHashMap<String,Movie> media=new LinkedHashMap<>();
  final LinkedHashMap<Long,String> chats=new LinkedHashMap<>();
  Listener listener;
  String query="",videoOffset="",documentOffset="",error="";
  long chatId,videoFrom,documentFrom; int generation;
  boolean videoDone,documentDone,loading,chatLoading,mainDone,archiveDone;
  int mainLimit,archiveLimit;
  TelegramLibrary(Context c,TelegramBridge tg){this(c,tg::waitRequest);}
  TelegramLibrary(Context c,Calls calls){context=c.getApplicationContext();this.calls=calls;}
  void notifyChanged(){if(listener!=null)listener.libraryChanged();}
  void browse(long chat,String text){
    generation++;chatId=chat;query=text.trim();videoOffset="";documentOffset="";
    videoFrom=0;documentFrom=0;videoDone=false;documentDone=false;loading=false;error="";media.clear();notifyChanged();loadMore();
  }
  List<Movie> items(){return merge(new ArrayList<>(media.values()),Collections.emptyList());}
  boolean hasMore(){return !videoDone||!documentDone;}
  static List<Movie> merge(List<Movie> a,List<Movie> b){
    LinkedHashMap<String,Movie> unique=new LinkedHashMap<>();
    for(Movie m:a)unique.put(m.cacheIdentity(),m);for(Movie m:b)unique.putIfAbsent(m.cacheIdentity(),m);
    List<Movie> result=new ArrayList<>(unique.values());result.sort((x,y)->{int n=Long.compare(y.date,x.date);return n!=0?n:Long.compare(y.messageId,x.messageId);});return result;
  }
  void loadMore(){
    if(loading||!hasMore())return;loading=true;error="";notifyChanged();
    final int token=generation;final long chat=chatId,vFrom=videoFrom,dFrom=documentFrom;
    final String q=query,vOffset=videoOffset,dOffset=documentOffset;
    final boolean vDone=videoDone,dDone=documentDone;
    worker.execute(()->{
      try {
        TdApi.Object videos=vDone?null:fetch(chat,q,vOffset,vFrom,"SearchMessagesFilterVideo");
        TdApi.Object documents=dDone?null:fetch(chat,q,dOffset,dFrom,"SearchMessagesFilterDocument");
        List<Movie> found=new ArrayList<>();Map<Long,String> titles=new LinkedHashMap<>();
        for(TdApi.Object page:new TdApi.Object[]{videos,documents}){
          if(page==null)continue;if(page instanceof TdApi.Error)throw new IllegalStateException(TelegramBridge.str(page,"message"));
          Object raw=TelegramBridge.get(page,"messages");
          if(!(raw instanceof TdApi.Message[]))throw new IllegalStateException("Unexpected Telegram media response");
          for(TdApi.Message message:(TdApi.Message[])raw){Movie m=Movie.fromMessage(message,message.chatId);if(m!=null&&m.fileId>0){found.add(m);titles.putIfAbsent(message.chatId,"");}}
        }
        for(long id:new ArrayList<>(titles.keySet())){
          TdApi.Object detail=calls.call("GetChat","chatId",id);
          titles.put(id,TelegramBridge.str(detail,"title"));
          if(TelegramBridge.bool(detail,"hasProtectedContent"))for(Movie m:found)if(m.chatId==id)m.saveAllowed=false;
        }
        ui.post(()->{if(token!=generation)return;
          for(Movie m:found)media.putIfAbsent(m.cacheIdentity(),m);chats.putAll(titles);
          if(videos!=null){videoOffset=TelegramBridge.str(videos,"nextOffset");videoFrom=TelegramBridge.num(videos,"nextFromMessageId");videoDone=chat==0?videoOffset.isEmpty()||videoOffset.equals(vOffset):videoFrom<=0||videoFrom==vFrom;}
          if(documents!=null){documentOffset=TelegramBridge.str(documents,"nextOffset");documentFrom=TelegramBridge.num(documents,"nextFromMessageId");documentDone=chat==0?documentOffset.isEmpty()||documentOffset.equals(dOffset):documentFrom<=0||documentFrom==dFrom;}
          loading=false;notifyChanged();
        });
      }catch(Exception e){ui.post(()->{if(token!=generation)return;loading=false;error=e.getMessage()==null?"Telegram library unavailable":e.getMessage();notifyChanged();});}
    });
  }
  TdApi.Object fetch(long chat,String q,String offset,long from,String filter)throws Exception{
    Object f=TelegramBridge.obj(filter);
    if(chat==0)return calls.call("SearchMessages","chatList",null,"query",q,"offset",offset,"limit",PAGE,"filter",f,"chatTypeFilter",null,"minDate",0,"maxDate",0);
    return calls.call("SearchChatMessages","chatId",chat,"topicId",null,"query",q,"senderId",null,"fromMessageId",from,"offset",0,"limit",PAGE,"filter",f);
  }
  void loadChats(){
    if(chatLoading||mainDone&&archiveDone)return;chatLoading=true;notifyChanged();
    final boolean main= !mainDone; final int limit=(main?mainLimit:archiveLimit)+100;
    worker.execute(()->{try{
      Object list=TelegramBridge.obj(main?"ChatListMain":"ChatListArchive");
      TdApi.Object loaded=calls.call("LoadChats","chatList",list,"limit",100);
      boolean done=loaded instanceof TdApi.Error&&TelegramBridge.num(loaded,"code")==404;
      if(loaded instanceof TdApi.Error&&!done)throw new IllegalStateException(TelegramBridge.str(loaded,"message"));
      TdApi.Object response=calls.call("GetChats","chatList",list,"limit",limit);
      if(response instanceof TdApi.Error)throw new IllegalStateException(TelegramBridge.str(response,"message"));
      Map<Long,String> titles=new LinkedHashMap<>();Object raw=TelegramBridge.get(response,"chatIds");
      if(raw instanceof long[])for(long id:(long[])raw){TdApi.Object detail=calls.call("GetChat","chatId",id);if(!(detail instanceof TdApi.Error))titles.put(id,TelegramBridge.str(detail,"title"));}
      final boolean finished=done;
      ui.post(()->{chats.putAll(titles);if(main){mainLimit=limit;mainDone=finished;}else{archiveLimit=limit;archiveDone=finished;}chatLoading=false;error="";notifyChanged();});
    }catch(Exception e){ui.post(()->{chatLoading=false;error=e.getMessage()==null?"Chats unavailable":e.getMessage();notifyChanged();});}});
  }
  String chatTitle(long id){String name=chats.get(id);return name==null||name.isEmpty()?"Telegram chat":name;}
  void close(){generation++;listener=null;worker.shutdownNow();}
}
