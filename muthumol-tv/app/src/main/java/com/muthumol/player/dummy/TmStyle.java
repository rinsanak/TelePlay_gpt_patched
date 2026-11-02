package com.muthumol.player.dummy;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** TMPlayer's default dark colour roles, kept separate from playback preferences. */
final class TmStyle {
  static final int BACKGROUND=0xff0c0e10, SURFACE=0xff15181b, RAISED=0xff23272a;
  static int TEXT=0xfff2f4f5, MUTED=0xffaeb5bb, ACCENT=0xffe50914;
  static int ON_ACCENT=0xffffffff, OUTLINE=0xff2e3337;
  static GradientDrawable card(Context c){GradientDrawable d=new GradientDrawable();d.setCornerRadius(Ui.dp(c,14));d.setColor(SURFACE);d.setStroke(Ui.dp(c,1),OUTLINE);return d;}
  static GradientDrawable cardFocus(Context c,boolean focused){GradientDrawable d=new GradientDrawable();d.setCornerRadius(Ui.dp(c,14));d.setColor(focused?0xff271317:SURFACE);d.setStroke(Ui.dp(c,focused?2:1),focused?ACCENT:OUTLINE);return d;}
  static GradientDrawable pill(Context c,boolean focused,boolean selected){
    GradientDrawable d=new GradientDrawable();d.setCornerRadius(Ui.dp(c,24));
    d.setColor(focused?ACCENT:selected?0xff4b141a:RAISED);
    if(focused)d.setStroke(Ui.dp(c,2),TEXT);return d;
  }
  private TmStyle(){}
}
