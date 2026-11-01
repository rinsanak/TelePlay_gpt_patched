package com.muthumol.player.dummy;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** TMPlayer's default dark colour roles, kept separate from playback preferences. */
final class TmStyle {
  static final int BACKGROUND=0xff0c0e10, SURFACE=0xff15181b, RAISED=0xff23272a;
  static final int TEXT=0xfff2f4f5, MUTED=0xffaeb5bb, ACCENT=0xff79d0f5;
  static final int ON_ACCENT=0xff003546, OUTLINE=0xff2e3337;
  static GradientDrawable pill(Context c,boolean focused,boolean selected){
    GradientDrawable d=new GradientDrawable();d.setCornerRadius(Ui.dp(c,24));
    d.setColor(focused?ACCENT:selected?0xff004d64:RAISED);
    if(focused)d.setStroke(Ui.dp(c,2),TEXT);return d;
  }
  private TmStyle(){}
}
