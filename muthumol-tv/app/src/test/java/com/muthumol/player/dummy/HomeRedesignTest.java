package com.muthumol.player.dummy;

import android.view.View;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class HomeRedesignTest {
  @Test public void searchAcceptsOneTypoButRequiresEveryWord() {
    assertTrue(TvLibrary.matches("Strange Things S01E02.mkv", "strnage things"));
    assertTrue(TvLibrary.matches("Café Cinema 2025.mkv", "cafe cinma"));
    assertFalse(TvLibrary.matches("Strange Things.mkv", "strange unrelated"));
    assertFalse(TvLibrary.matches("Toy.mkv", "top"));
    assertTrue(LibrarySearch.rank("Strange Things", "strange")
        < LibrarySearch.rank("Strnage Things", "strange"));
  }

  @Test public void searchDefaultOrderPlacesExactWordsBeforeTypos() {
    android.content.Context context=RuntimeEnvironment.getApplication();
    TvLibrary library=new TvLibrary(context);
    Movie typo=movie("Strnage Things", "typo"), exact=movie("Strange Things", "exact");
    List<Movie> result=library.list(context,Arrays.asList(typo,exact),"All","strange",0);
    assertEquals(2,result.size()); assertSame(exact,result.get(0));
  }

  @Test public void emptyCollectionHasGuidanceAndNoUnrelatedPlayTarget() {
    org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
    MainActivity a=c.get();Movie previous=a.session.movie;List<Movie> previousRecent=a.session.recentMovies;
    try {
      a.getSharedPreferences("tv-library",0).edit().clear().commit();
      a.session.movie=movie("Not on Watch later", "unmarked");
      a.session.recentMovies=Collections.singletonList(a.session.movie); a.home();
      a.selectHomeFilter("Watch later");
      assertEquals(View.VISIBLE,a.emptyLibrary.getVisibility());
      assertTrue(a.emptyLibrary.getText().toString().contains("Hold OK"));
      assertFalse(a.heroContinueButton.isEnabled()); assertFalse(a.moreButton.isEnabled());
      assertNull(a.displayedMovie());
      assertEquals(a.filterButtons.get(0).getId(),a.filterButtons.get(0).getNextFocusDownId());
    } finally { a.session.movie=previous;a.session.recentMovies=previousRecent;c.destroy(); }
  }

  @Test public void collectionSelectionClearsSearchAndKeepsRemoteRoutes() {
    org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
    MainActivity a=c.get();Movie previous=a.session.movie;List<Movie> previousRecent=a.session.recentMovies;
    try {
      Movie m=movie("Local Film (2025)","local");
      a.session.movie=m; a.session.recentMovies=Collections.singletonList(m); a.home();
      a.libraryQuery="no match"; a.selectHomeFilter("Movies");
      assertEquals("",a.libraryQuery); assertEquals(1,a.movieCards.size());
      assertTrue(a.filterButtons.get(1).isSelected());
      assertEquals(a.filterButtons.get(1).getId(),a.heroContinueButton.getNextFocusDownId());
      assertEquals(a.moreButton.getId(),a.heroContinueButton.getNextFocusRightId());
      assertEquals(a.heroContinueButton.getId(),a.moreButton.getNextFocusLeftId());
      assertEquals("Search",a.homeActions[2].getText().toString());
    } finally { a.session.movie=previous;a.session.recentMovies=previousRecent;c.destroy(); }
  }

  private Movie movie(String title,String identity) {
    Movie m=new Movie();m.title=title;m.identity=identity;m.uri="file:///"+identity;return m;
  }
}
