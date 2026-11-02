package com.muthumol.player.dummy;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
  static final int ACCENT=0xff63e6d1;
  static final int[] MENU_COLORS={0xffb65353,0xffb7793d,0xffaa9143,0xff4d8b65,0xff477fa8,0xff5f639c,0xff88649b};
  static int nextColorIndex=(int)(android.os.SystemClock.elapsedRealtime()%MENU_COLORS.length);
  static synchronized int nextMenuColor(Context c) { if(c instanceof MainActivity)return TmStyle.ACCENT;int color=MENU_COLORS[nextColorIndex];nextColorIndex=(nextColorIndex+1)%MENU_COLORS.length;return color; }
  static int color(Context c) {
    if(c instanceof MainActivity)return ((MainActivity)c).uiColor;
    if(c instanceof PlayerActivity)return ((PlayerActivity)c).uiColor;
    return MENU_COLORS[0];
  }
  static GradientDrawable glass(int color, boolean focus) {
    GradientDrawable d=new GradientDrawable();
    d.setColor(focus ? Color.WHITE : 0xee111c2a);
    d.setCornerRadius(dpRadius(color));d.setStroke(focus?3:2,color);return d;
  }
  static GradientDrawable playerCircle(Context c,boolean focused) {
    GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(focused?Color.WHITE:Color.TRANSPARENT);
    d.setStroke(dp(c,focused?2:1),color(c));return d;
  }
  static android.graphics.drawable.Drawable focusUnderline(Context c,boolean focused) {
    if(!focused)return null;GradientDrawable line=new GradientDrawable();line.setColor(Color.WHITE);
    LayerDrawable layers=new LayerDrawable(new android.graphics.drawable.Drawable[]{line});layers.setLayerHeight(0,dp(c,3));layers.setLayerGravity(0,Gravity.BOTTOM);return layers;
  }
  private static int dpRadius(int ignored){return 18;}
  static void menu(Activity a,String title,String[] labels,java.util.function.IntConsumer pick) {
    int color=nextMenuColor(a);Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(a,18),dp(a,14),dp(a,18),dp(a,12));body.setBackground(a instanceof MainActivity?TmStyle.pill(a,false,false):glass(color,false));
    TextView heading=text(a,title,23,Color.WHITE);body.addView(heading);
    ScrollView scroll=new ScrollView(a);LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);
    int selectedIndex=0;for(int n=0;n<labels.length;n++)if(labels[n].contains("✓")){selectedIndex=n;break;}
    for(int i=0;i<labels.length;i++) {
      final int selected=i;Button row=button(a,labels[i],() -> {d.dismiss();pick.accept(selected);});
      row.setMaxLines(2);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setContentDescription(labels[i]);row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);row.setMinHeight(dp(a,48));row.setTextSize(17);
      row.setBackgroundColor(Color.TRANSPARENT);
      row.setOnFocusChangeListener((v,focused) -> {if(focused) v.setBackground(glass(color,true));else v.setBackgroundColor(Color.TRANSPARENT);((TextView)v).setTextColor(focused?0xff101820:Color.WHITE);});
      add(rows,row);
    }
    int maxHeight=(int)(a.getResources().getDisplayMetrics().heightPixels*.58f);
    body.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(maxHeight,dp(a,labels.length*58))));
    Button close=button(a,"Close",d::dismiss);close.setTextColor(color);close.setBackgroundColor(Color.TRANSPARENT);
    close.setOnFocusChangeListener((v,f)->{if(f)v.setBackground(glass(color,true));else v.setBackgroundColor(Color.TRANSPARENT);((TextView)v).setTextColor(f?0xff101820:color);});body.addView(close);
    d.setContentView(body);d.show();TvMotion.panel(body);Window w=d.getWindow();
    if(w!=null) {w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(dp(a,a instanceof PlayerActivity?380:800),(int)(a.getResources().getDisplayMetrics().widthPixels*.84f)),-2);if(a instanceof PlayerActivity)w.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
      w.setDimAmount(.10f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
    if(rows.getChildCount()>0) rows.getChildAt(selectedIndex).requestFocus();
  }
  static Dialog sideDialog(Activity a,String title,LinearLayout contents){
    Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));body.setBackground(glass(color(a),false));body.addView(text(a,title,21,Color.WHITE));
    ScrollView scroll=new ScrollView(a);scroll.addView(contents);body.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(a,350),(int)(a.getResources().getDisplayMetrics().heightPixels*.65f))));Button done=button(a,"Done",d::dismiss);body.addView(done);d.setContentView(body);d.show();TvMotion.panel(body);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(dp(a,390),(int)(a.getResources().getDisplayMetrics().widthPixels*.88f)),-2);w.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);w.setDimAmount(.1f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}if(contents.getChildCount()>1)contents.getChildAt(1).requestFocus();return d;
  }
  static AlertDialog.Builder dialog(Activity a) {
    return new AlertDialog.Builder(a) {
      @Override public AlertDialog show() {
        AlertDialog d=super.show();int color=nextMenuColor(a);Window w=d.getWindow();
        if(w!=null) {w.setBackgroundDrawable(a instanceof MainActivity?TmStyle.pill(a,false,false):glass(color,false));w.setDimAmount(.10f);}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
          Button b=d.getButton(which);if(b!=null) {b.setTextColor(color);b.setBackgroundColor(Color.TRANSPARENT);b.setOnFocusChangeListener((v,f)->{v.setBackground(f?glass(color,true):null);((TextView)v).setTextColor(f?0xff101820:color);});}
        }
        return d;
      }
    };
  }
  static final int BG = Color.rgb(5, 10, 17),
      CARD = Color.rgb(15, 24, 36),
      MUTED = Color.rgb(172, 188, 204);

  static int dp(Context c, int v) {
    return (int) (v * c.getResources().getDisplayMetrics().density + .5f);
  }

  static void full(Activity a) {
    a.getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    a.getWindow().setBackgroundDrawableResource(android.R.color.black);
  }

  static TextView text(Context c, String s, int size, int color) {
    TextView v = new TextView(c);
    v.setText(s);
    v.setTextSize(size);v.setTypeface(Appearance.font);
    v.setTextColor(color);
    v.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
    return v;
  }

  static GradientDrawable bg(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(24);
    d.setStroke(1, 0xff304052);
    return d;
  }

  static Button button(Context c, String s, Runnable click) {
    Button b = new Button(c);
    b.setFocusable(true);b.setFocusableInTouchMode(false);
    b.setAllCaps(false);
    b.setText(s);
    b.setTextColor(Color.WHITE);
    b.setTextSize(18);
    b.setMinHeight(dp(c, 58));
    b.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
    b.setBackground(glass(color(c),false));
    b.setElevation(dp(c,2));
    b.setOnFocusChangeListener(
        (v, f) -> {
          v.setBackground(glass(color(c),f));((TextView)v).setTextColor(f?0xff101820:Color.WHITE);
          TvMotion.focus(v,f,1.045f);
        });
    b.setOnClickListener(v -> click.run());
    return b;
  }

  static Button roundButton(Context c, String text, String label, Runnable click) {
    Button b=button(c,text,click);b.setTag("round-action");b.setContentDescription(label);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);b.setTextSize(19);
    b.setBackground(roundShape(c,false));b.setOnFocusChangeListener((v,focused)-> {
      v.setBackground(roundShape(c,focused));((TextView)v).setTextColor(focused?0xff101820:Color.WHITE);
      TvMotion.focus(v,focused,1.10f);
    });return b;
  }
  static GradientDrawable roundShape(Context c,boolean focused) {
    GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);
    d.setColor(focused ? Color.WHITE : 0x8503070d);d.setStroke(3,color(c));return d;
  }

  static void recolor(View v) {
    if(v instanceof Button){v.setBackground("round-action".equals(v.getTag())?roundShape(v.getContext(),v.hasFocus()):glass(color(v.getContext()),v.hasFocus()));((Button)v).setTextColor(v.hasFocus()?0xff101820:Color.WHITE);}
    else if("accent-panel".equals(v.getTag()))v.setBackground(panel(v.getContext()));
    else if("accent-text".equals(v.getTag()) && v instanceof TextView)((TextView)v).setTextColor(color(v.getContext()));
    if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)recolor(g.getChildAt(i));}
  }

  static void add(LinearLayout l, View v) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, 0, 0, dp(l.getContext(), 8));
    l.addView(v, p);
  }

  static GradientDrawable panel(Context c) {
    GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[]{0xff172536,0xff0c1521});
    d.setCornerRadius(dp(c,28));d.setStroke(dp(c,2),color(c));return d;
  }

  static TextView eyebrow(Context c,String text) {
    TextView v=text(c,text,12,color(c));v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    v.setLetterSpacing(.12f);return v;
  }

  static EditText input(Context c, String hint, boolean secret) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setSingleLine(true);
    e.setTextColor(Color.WHITE);
    e.setHintTextColor(MUTED);
    e.setBackground(glass(color(c),false));
    e.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));
    e.setOnFocusChangeListener((v,focused)->{v.setBackground(glass(color(c),focused));e.setTextColor(focused?0xff101820:Color.WHITE);});
    if (secret) e.setInputType(129);
    return e;
  }

  static void message(Activity a, String title, String msg) {
    dialog(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
  }
}
