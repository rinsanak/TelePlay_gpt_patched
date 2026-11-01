package com.muthumol.player.dummy;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.drinkless.tdlib.TdApi;
import androidx.media3.extractor.SeekMap;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class ChannelSession {
  interface Listener{void changed();}
  private static ChannelSession instance;
  static synchronized ChannelSession get(Context c){if(instance==null)instance=new ChannelSession(c.getApplicationContext());return instance;}
  final Context context;final TelegramBridge tg;final SharedPreferences prefs;final Handler ui=new Handler(Looper.getMainLooper());
  final ExecutorService tasks=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Channel preparation");t.setDaemon(true);return t;});
  private final ExecutorService refreshTasks=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Channel refresh");t.setDaemon(true);return t;});
  private final Object refreshLock=new Object();private long refreshGeneration;private boolean refreshWorkerRunning,pendingRefresh;
  final Set<Listener> listeners=new HashSet<>();final StreamStats stats=new StreamStats();
  volatile List<Movie> recentMovies=Collections.emptyList();private Map<String,Object> recentSubtitles=Collections.emptyMap();
  volatile Movie movie;volatile ReadAheadStore store;volatile MovieIndex index;
  volatile String note="Choose a channel in Settings",subtitlePath="",subtitleType="",subtitleName="";
  volatile int subtitleId;volatile long channelId;volatile boolean visible=true;volatile boolean playbackVisible;
  private volatile boolean refreshing,clearing;private String preparedIdentity="";private int preparedFileId;private volatile int epoch;private long playbackMs;
  final Runnable poll=()->{refreshIfIdle();};
  Runnable pollRunnable(){return poll;}
  final Set<Object> visibleOwners=Collections.newSetFromMap(new WeakHashMap<>());
  final Runnable pauseTask=()->{if(visibleOwners.isEmpty())pause();};
  private ChannelSession(Context c){context=c;tg=TelegramBridge.getInstance(c);prefs=c.getSharedPreferences("channel",0);channelId=prefs.getLong("id",0);try{String raw=prefs.getString("movie","");if(!raw.isEmpty())movie=Movie.from(new JSONObject(raw));}catch(Exception ignored){}try{JSONArray saved=new JSONArray(prefs.getString("recentMovies","[]"));List<Movie> list=new ArrayList<>();for(int i=0;i<Math.min(ChannelScan.LIMIT,saved.length());i++)list.add(Movie.from(saved.getJSONObject(i)));recentMovies=Collections.unmodifiableList(list);}catch(Exception ignored){}subtitleId=prefs.getInt("subtitleId",0);subtitlePath=prefs.getString("subtitlePath","");subtitleType=prefs.getString("subtitleType","");subtitleName=prefs.getString("subtitleName","");if(!new File(subtitlePath).isFile())subtitlePath="";}
  boolean cacheMatches(Movie m){return m!=null&&preparedIdentity.equals(m.cacheIdentity())&&preparedFileId==m.fileId;}
  void add(Listener l){listeners.add(l);}void remove(Listener l){listeners.remove(l);}
  void notifyChanged(){ui.post(()->{for(Listener l:new ArrayList<>(listeners))l.changed();});}
  void updatePlaybackVisibility(){
    boolean playing=false;for(Object owner:visibleOwners)if(owner instanceof PlayerActivity){playing=true;break;}
    playbackVisible=playing;
    // Only the selected movie warms while Home/Player is visible. The shelf's
    // preview focus never switches selection or starts ten video downloads.
    // V5.1 keeps transfers alive across the 400 ms Activity hand-off.
    // Only pauseTask may suspend an ownerless foreground session.
    boolean warm=visible;stats.paused=!warm;
    if(store!=null)store.setPaused(!warm);
    BetaLog.event("selected_preload","enabled="+warm+" playerVisible="+playing);
  }
  void onVisible(Object owner){BetaLog.event("session_visible",owner.getClass().getSimpleName());visibleOwners.add(owner);visible=true;ui.removeCallbacks(pauseTask);updatePlaybackVisibility();ui.removeCallbacks(poll);ui.post(poll);if(movie!=null)prepare(movie);}
  void onHidden(Object owner){visibleOwners.remove(owner);updatePlaybackVisibility();ui.removeCallbacks(pauseTask);if(visibleOwners.isEmpty())ui.postDelayed(pauseTask,400);}
  void pause(){BetaLog.event("session_pause","owners="+visibleOwners.size());visible=false;playbackVisible=false;ui.removeCallbacks(poll);stats.paused=true;stats.transferEpoch++;if(store!=null)store.setPaused(true);if(movie!=null)tg.cancel(movie.fileId);}
  void choose(long id,String title){if(channelId!=id){Movie old=movie;epoch++;movie=null;recentMovies=Collections.emptyList();recentSubtitles=Collections.emptyMap();subtitleId=0;subtitlePath="";subtitleType="";subtitleName="";prefs.edit().remove("movie").remove("recentMovies").remove("subtitleId").remove("subtitlePath").remove("subtitleType").remove("subtitleName").apply();if(old!=null)tasks.execute(()->{closeStore();tg.deleteCached(old.fileId);MediaCache.delete(MediaCache.directory(context,old.cacheIdentity()));});note="Checking selected channel…";}channelId=id;prefs.edit().putLong("id",id).putString("title",title).apply();refresh();}
  void refresh(){requestRefresh(true);}
  private void refreshIfIdle(){if(!refreshing)requestRefresh(false);}
  private void requestRefresh(boolean manual){
    if(clearing)return;
    if(!tg.ready||channelId==0){if(manual){note=channelId==0?"Select a movie channel in Settings":"Telegram is not ready; refresh after login";notifyChanged();}return;}
    synchronized(refreshLock){refreshGeneration++;pendingRefresh=true;refreshing=true;if(!refreshWorkerRunning){refreshWorkerRunning=true;refreshTasks.execute(this::scanLatest);}}
    if(manual){note="Refreshing movie from channel…";notifyChanged();}
  }
  private void scanLatest(){
    final long token,chat;
    synchronized(refreshLock){if(!pendingRefresh){refreshWorkerRunning=false;return;}pendingRefresh=false;token=refreshGeneration;chat=channelId;}
    ChannelScan.Result result=null;String error=null;
    try{List<TdApi.Message> history=new ArrayList<>();long cursor=0;
      for(int page=0;page<20;page++){
        synchronized(refreshLock){if(token!=refreshGeneration||chat!=channelId||clearing)break;}
        TdApi.Object response=tg.waitRequest("GetChatHistory","chatId",chat,"fromMessageId",cursor,"offset",0,"limit",100,"onlyLocal",false);
        if(response instanceof TdApi.Error)throw new IOException("Channel history unavailable");
        Object raw=TelegramBridge.get(response,"messages");TdApi.Message[] messages=raw instanceof TdApi.Message[]?(TdApi.Message[])raw:new TdApi.Message[0];if(messages.length==0)break;
        long oldest=Long.MAX_VALUE;for(TdApi.Message message:messages){history.add(message);oldest=Math.min(oldest,message.id);}
        result=ChannelScan.scan(history.toArray(new TdApi.Message[0]),chat);
        if(result.recent.size()>=ChannelScan.LIMIT||oldest==Long.MAX_VALUE||oldest<=0||(cursor>0&&oldest>=cursor))break;cursor=oldest;
      }
      result=ChannelScan.scan(history.toArray(new TdApi.Message[0]),chat);
    }
    catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    final ChannelScan.Result scanned=result;final String failure=error;
    ui.post(()->{
      boolean current; synchronized(refreshLock){current=token==refreshGeneration&&chat==channelId&&!clearing;}
      if(current){if(failure==null)applyScan(scanned);else note=movie==null?"Refresh failed • "+failure:"Refresh failed; saved movie kept • "+failure;}
      boolean rerun=false;
      synchronized(refreshLock){if(clearing){pendingRefresh=false;refreshWorkerRunning=false;refreshing=false;}else if(pendingRefresh||token!=refreshGeneration){pendingRefresh=true;rerun=true;}else{refreshWorkerRunning=false;refreshing=false;}}
      if(rerun)refreshTasks.execute(this::scanLatest);
      notifyChanged();
    });
  }
  private void applyScan(ChannelScan.Result scan){
    recentMovies=scan.recent;recentSubtitles=scan.subtitles;JSONArray saved=new JSONArray();for(Movie m:recentMovies)saved.put(m.json());prefs.edit().putString("recentMovies",saved.toString()).apply();
    // Refresh the shelf without replacing an already selected/playing movie.
    Movie selected=movie;if(selected==null){if(scan.movie!=null)selectMovie(scan.movie);else note="No supported videos in recent channel history";return;}
    for(Movie fresh:recentMovies)if(fresh.cacheIdentity().equals(selected.cacheIdentity())){if(fresh.fileId==selected.fileId){movie=fresh;prefs.edit().putString("movie",fresh.json().toString()).apply();}break;}
    Object sub=recentSubtitles.get(movie.cacheIdentity());if(visible&&sub!=null)downloadSubtitle(sub,movie.cacheIdentity());
    note=recentMovies.isEmpty()?"No recent videos • selected movie kept":"Latest "+recentMovies.size()+" videos ready";
  }
  void selectMovie(Movie selected){
    if(clearing||selected==null)return;
    Movie old=movie;boolean changed=old==null||!old.cacheIdentity().equals(selected.cacheIdentity())||old.fileId!=selected.fileId;
    if(!changed){movie=selected;prepare(selected);return;}
    movie=selected;epoch++;int generation=epoch;subtitleId=0;subtitlePath="";subtitleType="";subtitleName="";
    prefs.edit().putString("movie",selected.json().toString()).remove("subtitleId").remove("subtitlePath").remove("subtitleType").remove("subtitleName").apply();note="Selected movie preparing";
    tasks.execute(()->{synchronized(ChannelSession.this){if(generation!=epoch||clearing)return;if(!cacheMatches(selected))closeStore();MediaCache.activate(MediaCache.key(selected.cacheIdentity()));MediaCache.keepOnly(MediaCache.root(context),MediaCache.key(selected.cacheIdentity()));if(visible&&tg.ready)prepareNow(selected);}});
    Object subtitle=recentSubtitles.get(selected.cacheIdentity());if(visible&&subtitle!=null)downloadSubtitle(subtitle,selected.cacheIdentity());notifyChanged();
  }
  void prepare(Movie m){if(m.fileId<=0)return;if(clearing||!visible||!tg.ready)return;tasks.execute(()->{if(!clearing&&visible&&movie!=null&&movie.cacheIdentity().equals(m.cacheIdentity())&&(!cacheMatches(m)||store==null||store.failed())){if(store!=null)closeStore();prepareNow(m);}});}
  synchronized void prepareNow(Movie m){if(m.fileId<=0){note="Offline video ready";return;}BetaLog.event("session_prepare","bytes="+m.size+" resumeMs="+m.savedPosition(context));if(store!=null&&cacheMatches(m)&&!store.failed())return;if(store!=null)closeStore();try{if(clearing||movie==null||!movie.cacheIdentity().equals(m.cacheIdentity()))return;if(m.size<=0){note="Waiting for movie size";return;}long budget=TelegramDataSource.budget(context);stats.diskBudget=Math.max(2L*ReadAheadStore.BLOCK,budget/2);stats.mediaBits=m.duration>0?m.size*8/m.duration:6000000;MediaCache.migrate(context,m);File dir=MediaCache.directory(context,m.cacheIdentity());dir.mkdirs();preparedIdentity=m.cacheIdentity();index=new MovieIndex(dir,m.size);playbackMs=m.savedPosition(context);long start=index.byteAt(playbackMs*1000,false);if(start<0)start=0;preparedFileId=m.fileId;stats.beginFile(m.fileId,tg.cachedBytes(m.fileId));tg.streamObservers.put(m.fileId,stats);store=new ReadAheadStore(dir,m.size,start,stats::preloadTargetBytes,stats::cacheBudgetBytes,stats::playbackBytePosition,new TelegramRangeLoader(tg,m.fileId,m.size,stats),true);stats.cache=store;store.warmRange(0,ReadAheadStore.HEADER_BYTES);// Tail/index bytes are fetched only if the extractor requests them.
    updatePosition(playbackMs);stats.paused=!visible;store.setPaused(!visible);note="Movie preparation started";notifyChanged();}catch(Exception e){note="Preparation will retry: "+e.getMessage();notifyChanged();}}
  synchronized ReadAheadStore acquire(int fileId)throws IOException{if(movie==null||movie.fileId!=fileId)throw new IOException("Movie changed; return home and select Play");if(!cacheMatches(movie))closeStore();if(store==null)prepareNow(movie);if(store==null)throw new IOException(note);if(store.failed()){closeStore();prepareNow(movie);}if(store==null)throw new IOException(note);return store;}
  synchronized void capture(SeekMap map){if(index!=null){index.capture(map);updatePosition(playbackMs);}}
  synchronized void updatePosition(long ms){playbackMs=Math.max(0,ms);if(index==null||store==null)return;if(index.durationUs>0)stats.mediaBits=movie.size*8000000L/index.durationUs;long from=index.byteAt(Math.max(0,ms-120000)*1000,false),to=index.byteAt((ms+300000)*1000,true);if(from>=0&&to>=from)store.window(from,to);}
  synchronized void closeStore(){ReadAheadStore old=store;store=null;stats.cache=null;index=null;preparedIdentity="";if(old!=null)old.closePreservingCompletedBlocks();if(preparedFileId!=0){tg.streamObservers.remove(preparedFileId,stats);stats.endFile(preparedFileId);preparedFileId=0;}}
  void clear(){
    if(clearing)return;clearing=true;pause();epoch++;
    synchronized(refreshLock){refreshGeneration++;pendingRefresh=false;}
    movie=null;recentMovies=Collections.emptyList();recentSubtitles=Collections.emptyMap();subtitleId=0;subtitlePath="";subtitleType="";subtitleName="";playbackMs=0;
    note="Clearing all movie data…";notifyChanged();BetaLog.event("cache_clear_start","scope=all_movies");
    tasks.execute(()->{
      try{
        closeStore();int removed=MovieData.clearPreferences(context);MovieData.clearFiles(context);tg.clearMediaCache();
        stats.resetMovie();note="All movie data cleared • Refresh movie to load again";
        BetaLog.event("cache_clear_complete","movieKeysRemoved="+removed);
      }catch(Exception e){note="Cache clear failed: "+e.getMessage();BetaLog.exception("cache_clear_error",e);}
      ui.post(()->{clearing=false;visible=!visibleOwners.isEmpty();notifyChanged();});
    });
  }
  void downloadSubtitle(Object doc,String identity){Object f=TelegramBridge.get(doc,"document");int id=(int)TelegramBridge.num(f,"id");String name=TelegramBridge.str(doc,"fileName"),mime=PlayerActivity.subtitleMime(name);if(id==subtitleId&&!subtitlePath.isEmpty())return;long bytes=TelegramBridge.num(f,"size");if(bytes>4L*1024*1024)return;int token=epoch;tasks.execute(()->{if(clearing||token!=epoch)return;try{tg.download(id,0,bytes>0?bytes:4L*1024*1024,8);long end=SystemClock.elapsedRealtime()+30000;String path="";while(SystemClock.elapsedRealtime()<end){TdApi.Object fresh=tg.waitRequest("GetFile","fileId",id);Object local=TelegramBridge.get(fresh,"local");if(TelegramBridge.bool(local,"isDownloadingCompleted")){path=TelegramBridge.str(local,"path");break;}TelegramRangeLoader.sleep(100);}if(path.isEmpty())return;File folder=new File(context.getFilesDir(),"channel-subtitles");folder.mkdirs();File target=new File(folder,MediaCache.key(identity)+"-"+id+".srt");SmallFileIO.copySubtitle(new File(path),target);ui.post(()->{if(clearing||token!=epoch||movie==null||!movie.cacheIdentity().equals(identity)){target.delete();return;}for(File old:folder.listFiles())if(!old.equals(target))old.delete();subtitleId=id;subtitlePath=target.getAbsolutePath();subtitleType=mime;subtitleName=name;prefs.edit().putInt("subtitleId",id).putString("subtitlePath",subtitlePath).putString("subtitleType",mime).putString("subtitleName",name).apply();notifyChanged();});}catch(Exception ignored){}});}
}
