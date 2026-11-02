package com.muthumol.player.dummy;
import android.view.View;
import android.widget.Button;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class SeriesBrowseTest {
 Movie movie(String title,String key){Movie m=new Movie();m.title=title;m.identity=key;m.uri="file:///"+key;return m;}
 @Test public void homeGroupsLostButKeepsMoviesAndOtherShows(){List<Movie> input=Arrays.asList(movie("LOST.S02E03.mkv","a"),movie("Lost.S01E01.mkv","b"),movie("Other.S01E01.mkv","c"),movie("Cinema.2025.mkv","d"));List<Movie> cards=TvLibrary.homeEntries(input);assertEquals(3,cards.size());assertEquals("LOST",MediaTitle.parse(cards.get(0).title).name);}
 @Test public void seasonsSortEpisodesAndKeepSpecialsAndIgnoreDuplicateReferences(){Movie a=movie("LOST.S02E03.mkv","a"),b=movie("Lost.S01E02.mkv","b"),c=movie("Lost.S01E01.mkv","c"),d=movie("Lost.S00E01.mkv","d"),other=movie("Other.S01E01.mkv","x");SortedMap<Integer,List<Movie>> seasons=TvLibrary.seasons(Arrays.asList(a,b,c,d,b,other),a);assertEquals(Arrays.asList(0,1,2),new ArrayList<>(seasons.keySet()));assertEquals(Arrays.asList(c,b),seasons.get(1));}
 @Test public void clickingShowOpensSeasonsThenEpisodesBeforePlayback(){org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=controller.get();Movie old=a.session.movie;List<Movie> recent=a.session.recentMovies;
  try{Movie one=movie("LOST.S01E01.mkv","lost1"),two=movie("Lost.S02E01.mkv","lost2");a.session.movie=one;a.session.recentMovies=Arrays.asList(two,one);a.home();assertEquals(1,a.movieCards.size());assertEquals(View.GONE,a.movieCards.get(0).episode.getVisibility());a.movieCards.get(0).performClick();assertEquals("seasons",a.screen);assertEquals("LOST",MediaTitle.parse(a.browsingSeries.title).name.toUpperCase(Locale.ROOT));find(a,"Season 1").performClick();assertEquals("episodes",a.screen);find(a,"S1 E1").performClick();android.content.Intent launch=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(launch);assertTrue(launch.getStringExtra("movies").contains("lost1"));assertTrue(launch.getStringExtra("movies").contains("lost2"));assertEquals(0,launch.getIntExtra("index",-1));a.onBackPressed();assertEquals("seasons",a.screen);a.onBackPressed();assertEquals("home",a.screen);}
  finally{a.session.movie=old;a.session.recentMovies=recent;controller.destroy();}}
 @Test public void missingBuildCredentialsDoNotOfferAnApiForm(){org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();try{MainActivity a=c.get();a.configuration();assertEquals("auth-unavailable",a.screen);for(int i=0;i<a.column.getChildCount();i++)assertFalse(a.column.getChildAt(i) instanceof android.widget.EditText);}finally{c.destroy();}}
 Button find(MainActivity a,String prefix){for(int i=0;i<a.column.getChildCount();i++){View v=a.column.getChildAt(i);if(v instanceof Button&&((Button)v).getText().toString().startsWith(prefix))return (Button)v;}throw new AssertionError(prefix);}
}
