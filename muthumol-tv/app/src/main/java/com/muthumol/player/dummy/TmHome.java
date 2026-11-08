package com.muthumol.player.dummy;

import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

/** Native TV and touch layout using TMPlayer's rail, hero and poster shelves. */
final class TmHome {
  static void build(MainActivity a){
    final long buildStarted=android.os.SystemClock.elapsedRealtime();
    boolean phone=a.phoneLayout(),compact=!phone&&a.getResources().getDisplayMetrics().heightPixels/a.getResources().getDisplayMetrics().density<500;
    a.screen="home";a.uiColor=TmStyle.ACCENT;
    a.homeRoot=new FrameLayout(a);a.homeRoot.setBackgroundColor(TmStyle.BACKGROUND);
    LinearLayout frame=new LinearLayout(a);frame.setOrientation(LinearLayout.VERTICAL);a.homeRoot.addView(frame,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout rail=new LinearLayout(a);rail.setOrientation(phone?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setPadding(Ui.dp(a,8),Ui.dp(a,phone?8:16),Ui.dp(a,8),Ui.dp(a,8));rail.setBackgroundColor(TmStyle.SURFACE);
    HorizontalScrollView phoneRail=new HorizontalScrollView(a);
    phoneRail.setHorizontalScrollBarEnabled(false);phoneRail.addView(rail);frame.addView(phoneRail,new LinearLayout.LayoutParams(-1,Ui.dp(a,58)));rail.setOrientation(LinearLayout.HORIZONTAL);
    a.playFromStartButton=Ui.button(a,"Start over",()->a.launchPreview(true));a.continueButton=Ui.button(a,"Continue",()->a.launchPreview(false));
    a.playFromStartButton.setContentDescription("Play from start");a.continueButton.setContentDescription("Continue");
    Button home=nav(a,"⌂","Home",()->{a.browseChat=0;a.libraryQuery="";a.libraryFilter="All";a.previewMovie=null;if(a.tg.ready)a.accountLibrary.browse(0,"");a.home();});
    Button search=nav(a,"⌕","Search",a::searchLibrary),chats=nav(a,"☷","Chats",a::channels);
    Button resume=nav(a,"◷","Continue",()->a.selectHomeFilter("Continue"));
    Button favourites=nav(a,"☆","All movies",()->a.openGrid(0));
    Button downloads=nav(a,"↓","Downloads",()->OfflineDownloads.get(a).show(a));
    Button settings=nav(a,"⚙","Settings",a::settingsPopup),refresh=nav(a,"↻","Refresh",a::refreshLibrary);
    search.setContentDescription("Search library");refresh.setContentDescription("Refresh latest videos");chats.setContentDescription("Browse chats");
    a.homeActions=new Button[]{a.playFromStartButton,a.continueButton,search,chats,downloads,refresh,settings};a.continueButton.setNextFocusRightId(search.getId());a.playFromStartButton.setLayoutParams(new LinearLayout.LayoutParams(-2,Ui.dp(a,36)));
    for(Button b:a.homeActions){if(b.getId()==View.NO_ID)b.setId(View.generateViewId());}
    TextView brand=Ui.text(a,"TG stream",18,TmStyle.ACCENT);brand.setTypeface(Typeface.DEFAULT_BOLD);brand.setGravity(Gravity.CENTER);rail.addView(brand,new LinearLayout.LayoutParams(Ui.dp(a,110),Ui.dp(a,48)));
    for(Button button:new Button[]{home,search,refresh,settings})rail.addView(button,new LinearLayout.LayoutParams(Ui.dp(a,80),Ui.dp(a,48)));
    ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setClipToPadding(false);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    a.column=new LinearLayout(a);a.column.setOrientation(LinearLayout.VERTICAL);a.column.setPadding(Ui.dp(a,phone?12:16),Ui.dp(a,compact?8:12),Ui.dp(a,phone?12:16),Ui.dp(a,12));scroll.addView(a.column);
    FrameLayout hero=new FrameLayout(a);a.poster=new PosterArtView(a);a.poster.cinematic=true;hero.addView(a.poster,new FrameLayout.LayoutParams(-1,-1));a.column.addView(hero,new LinearLayout.LayoutParams(-1,Ui.dp(a,phone?310:compact?168:232)));
    a.heroPanel=new LinearLayout(a);a.heroPanel.setOrientation(LinearLayout.VERTICAL);a.heroPanel.setPadding(Ui.dp(a,16),Ui.dp(a,compact?4:14),Ui.dp(a,12),Ui.dp(a,4));
    a.movieState=label(a,"YOUR TELEGRAM CINEMA",10,TmStyle.ACCENT);a.title=label(a,"Your cinema awaits",compact?23:phone?28:32,TmStyle.TEXT);a.title.setTypeface(Typeface.DEFAULT_BOLD);a.title.setMaxLines(compact?1:2);a.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    a.subtitles=label(a,"",12,TmStyle.MUTED);a.subtitles.setMaxLines(2);a.resumeSummary=label(a,"",11,TmStyle.MUTED);
    for(TextView text:new TextView[]{a.movieState,a.title,a.subtitles,a.resumeSummary}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=Ui.dp(a,compact?3:5);a.heroPanel.addView(text,p);}
    a.resumeProgress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);a.resumeProgress.setMax(1000);a.resumeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(TmStyle.ACCENT));a.heroPanel.addView(a.resumeProgress,new LinearLayout.LayoutParams(Ui.dp(a,180),Ui.dp(a,2)));
    LinearLayout actions=new LinearLayout(a);actions.setPadding(0,Ui.dp(a,compact?4:8),0,0);
    a.heroContinueButton=Ui.button(a,"▶  PLAY",()->a.launchPreview(false));a.heroContinueButton.setId(View.generateViewId());a.moreButton=Ui.button(a,"Details",()->{Movie m=a.displayedMovie();if(m!=null)a.movieOptions(m);});a.moreButton.setId(View.generateViewId());
    for(Button b:new Button[]{a.heroContinueButton,a.moreButton}){b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setPadding(Ui.dp(a,12),0,Ui.dp(a,12),0);b.setOnFocusChangeListener((v,f)->a.styleHomeButton(b,false));a.styleHomeButton(b,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Ui.dp(a,b==a.moreButton?100:168),Ui.dp(a,phone?48:36));p.rightMargin=Ui.dp(a,10);actions.addView(b,p);}
    a.heroContinueButton.setNextFocusRightId(a.moreButton.getId());a.moreButton.setNextFocusLeftId(a.heroContinueButton.getId());a.heroPanel.addView(actions);
    a.status=label(a,"",11,TmStyle.MUTED);a.status.setMaxLines(2);a.heroPanel.addView(a.status);
    a.stateAction=Ui.button(a,"Browse chats",()->{if(!a.libraryFilter.equals("All")||!a.libraryQuery.isEmpty())a.selectHomeFilter("All");else if(a.tg.ready)a.channels();else a.authChanged(a.tg.state);});a.stateAction.setId(View.generateViewId());a.stateAction.setTextSize(12);a.stateAction.setMinHeight(0);a.stateAction.setMinimumHeight(0);a.stateAction.setPadding(Ui.dp(a,12),0,Ui.dp(a,12),0);a.heroPanel.addView(a.stateAction,new LinearLayout.LayoutParams(-2,Ui.dp(a,phone?48:32)));
    FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(phone?-1:(int)(a.getResources().getDisplayMetrics().widthPixels*.57f),-2,Gravity.CENTER_VERTICAL|Gravity.LEFT);hero.addView(a.heroPanel,hp);
    a.libraryDock=new LinearLayout(a);a.libraryDock.setOrientation(LinearLayout.VERTICAL);a.libraryDock.setPadding(0,Ui.dp(a,6),0,0);a.libraryDock.setBackgroundColor(TmStyle.BACKGROUND);a.column.addView(a.libraryDock,new LinearLayout.LayoutParams(-1,-2));
    HorizontalScrollView filters=new HorizontalScrollView(a);filters.setHorizontalScrollBarEnabled(false);LinearLayout chips=new LinearLayout(a);filters.addView(chips);a.filterButtons.clear();
    for(String filter:MainActivity.HOME_FILTERS){Button chip=Ui.button(a,filter,()->a.selectHomeFilter(filter));chip.setId(View.generateViewId());chip.setTextSize(11);chip.setMinHeight(0);chip.setMinimumHeight(0);chip.setMinWidth(0);chip.setMinimumWidth(0);chip.setPadding(Ui.dp(a,14),0,Ui.dp(a,14),0);chip.setOnFocusChangeListener((v,f)->a.styleFilter(chip,filter));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,Ui.dp(a,phone?48:28));p.rightMargin=Ui.dp(a,6);chips.addView(chip,p);a.filterButtons.add(chip);}
    a.libraryDock.addView(filters);a.shelfTitle=label(a,"Recently added",15,TmStyle.TEXT);a.shelfTitle.setTypeface(Typeface.DEFAULT_BOLD);a.libraryDock.addView(a.shelfTitle);
    a.emptyLibrary=label(a,"",13,TmStyle.MUTED);a.libraryDock.addView(a.emptyLibrary);
    HorizontalScrollView shelf=new HorizontalScrollView(a);shelf.setTag("bottom-library");shelf.setHorizontalScrollBarEnabled(false);shelf.setClipChildren(false);a.movieRail=new LinearLayout(a);a.movieRail.setClipChildren(false);shelf.addView(a.movieRail);a.libraryDock.addView(shelf);a.movieCards.clear();a.shelfKey=null;
    a.libraryLoad=Ui.button(a,"Load more videos",()->a.accountLibrary.loadMore());a.libraryLoad.setId(View.generateViewId());a.libraryLoad.setTextSize(12);a.libraryLoad.setMinHeight(0);a.libraryLoad.setMinimumHeight(0);a.libraryDock.addView(a.libraryLoad,new LinearLayout.LayoutParams(-2,Ui.dp(a,phone?48:32)));
    a.focusHint=label(a,"",10,TmStyle.MUTED);a.focusHint.setMaxLines(1);a.libraryDock.addView(a.focusHint);a.artCredit=new TextView(a);a.artCredit.setVisibility(View.GONE);a.previewIdentity="";
    a.extraShelves=new LinearLayout(a);a.extraShelves.setOrientation(LinearLayout.VERTICAL);a.column.addView(a.extraShelves,1);a.extraShelfKey="";
    a.setContentView(a.homeRoot);a.changed();if(a.displayedMovie()!=null)a.heroContinueButton.requestFocus();else search.requestFocus();TvMotion.enter(a.heroPanel,0);TvMotion.enter(a.libraryDock,90);BetaLog.event("home_build","durationMs="+(android.os.SystemClock.elapsedRealtime()-buildStarted)+" movieCards="+a.movieCards.size()+" phone="+phone+" compact="+compact);
  }
  static Button nav(MainActivity a,String symbol,String title,Runnable action){Button b=Ui.button(a,title,action);b.setId(View.generateViewId());b.setContentDescription(title);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(Ui.dp(a,5),0,Ui.dp(a,5),0);b.setBackground(TmStyle.pill(a,false,false));b.setOnFocusChangeListener((v,f)->{b.setBackground(TmStyle.pill(a,f,false));b.setTextColor(f?TmStyle.ON_ACCENT:TmStyle.MUTED);});return b;}
  static TextView label(MainActivity a,String text,int size,int color){TextView v=Ui.text(a,text,size,color);v.setPadding(0,Ui.dp(a,2),0,Ui.dp(a,2));return v;}
  private TmHome(){}
}
