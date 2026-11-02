package com.muthumol.player.dummy;
import java.util.*;import android.view.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=25,qualifiers="w960dp-h540dp-mdpi")
public class RecentMoviesTest {
 @Test public void remoteCardPreviewKeepsActiveStreamAndOpensTheChosenMovie()throws Exception{
  org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=c.get();a.updater.close();ChannelSession s=a.session;Movie old=s.movie;List<Movie> before=s.recentMovies;long chat=s.channelId;String note=s.note;
  try{Movie first=Movie.fromMessage(ChannelScanTest.movie(30,3,"Newest.mkv","newest"),42),second=Movie.fromMessage(ChannelScanTest.movie(20,2,"Previous.mkv","previous"),42);s.channelId=42;s.movie=first;s.recentMovies=Arrays.asList(first,second);a.home();assertEquals(2,a.movieCards.size());MovieCardView card=a.movieCards.get(1);card.getOnFocusChangeListener().onFocusChange(card,true);assertEquals("Previous",a.title.getText().toString());assertEquals(second.cacheIdentity(),a.poster.displayedIdentity);assertTrue(a.poster.getParent() instanceof android.widget.FrameLayout);assertSame(first,s.movie);assertEquals(a.filterButtons.get(0).getId(),card.getNextFocusUpId());assertEquals(card.getId(),a.movieCards.get(0).getNextFocusRightId());a.changed();assertSame(card,a.movieCards.get(1));card.performClick();assertEquals(second.cacheIdentity(),s.movie.cacheIdentity());android.content.Intent launch=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(launch);assertTrue(launch.getStringExtra("movies").contains("Previous.mkv"));}
  finally{s.tasks.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);s.movie=old;s.recentMovies=before;s.channelId=chat;s.note=note;s.closeStore();c.destroy();}
 }
 @Test public void refreshDoesNotSwitchSelectedMovieAndShelfPersists()throws Exception{
  android.content.Context context=RuntimeEnvironment.getApplication();ChannelSession s=ChannelSession.get(context);Movie old=s.movie;List<Movie> before=s.recentMovies;String note=s.note;
  try{Movie selected=Movie.fromMessage(ChannelScanTest.movie(10,1,"Watching.mkv","watching"),42);s.movie=selected;java.lang.reflect.Method apply=ChannelSession.class.getDeclaredMethod("applyScan",ChannelScan.Result.class);apply.setAccessible(true);apply.invoke(s,ChannelScan.scan(new org.drinkless.tdlib.TdApi.Message[]{ChannelScanTest.movie(20,2,"New.mkv","new")},42));assertSame(selected,s.movie);assertEquals("New.mkv",s.recentMovies.get(0).title);org.json.JSONArray saved=new org.json.JSONArray(s.prefs.getString("recentMovies","[]"));assertEquals("New.mkv",Movie.from(saved.getJSONObject(0)).title);}
  finally{s.movie=old;s.recentMovies=before;s.note=note;s.prefs.edit().remove("recentMovies").apply();}
 }
 @Test public void hiddenSelectionDoesNotCreateVideoPreloadStore()throws Exception{org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=c.get();a.updater.close();ChannelSession s=a.session;Movie old=s.movie;boolean ready=s.tg.ready;
  try{s.closeStore();s.playbackVisible=false;s.visible=false;s.stats.paused=true;s.tg.ready=true;Movie m=Movie.fromMessage(ChannelScanTest.movie(30,3,"A Movie.mkv","no-preload"),42);s.selectMovie(m);s.prepare(m);s.tasks.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);assertNull(s.store);assertTrue(s.stats.paused);}
  finally{s.visible=true;s.tg.ready=ready;s.movie=old;s.closeStore();c.destroy();}
 }

 @Test public void homeKeepsSelectedPreloadRunningButBackgroundPauses()throws Exception{
  ChannelSession s=ChannelSession.get(RuntimeEnvironment.getApplication());Set<Object> owners=new HashSet<>(s.visibleOwners);boolean visible=s.visible,play=s.playbackVisible;StreamStats stats=s.stats;boolean paused=stats.paused;
  try{s.visibleOwners.clear();s.visible=true;s.visibleOwners.add(new MainActivity());s.updatePlaybackVisibility();assertFalse(s.playbackVisible);assertFalse(stats.paused);s.visibleOwners.clear();s.updatePlaybackVisibility();assertFalse(stats.paused);s.pause();assertTrue(stats.paused);}
  finally{s.visibleOwners.clear();s.visibleOwners.addAll(owners);s.visible=visible;s.playbackVisible=play;stats.paused=paused;}
 }

}
