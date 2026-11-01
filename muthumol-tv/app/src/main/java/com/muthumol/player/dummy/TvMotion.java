package com.muthumol.player.dummy;
import android.view.*;import android.view.animation.*;
final class TvMotion {
 static boolean enabled(View v){return v.getContext().getSharedPreferences("tv-ui",0).getBoolean("motion",true)&&android.provider.Settings.Global.getFloat(v.getContext().getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}
 static void enter(View v,int delay){if(!enabled(v))return;v.setAlpha(0);v.setTranslationY(Ui.dp(v.getContext(),18));v.animate().alpha(1).translationY(0).setStartDelay(delay).setDuration(360).setInterpolator(new DecelerateInterpolator()).start();}
 static void focus(View v,boolean yes,float scale){v.animate().cancel();if(enabled(v))v.animate().scaleX(yes?scale:1).scaleY(yes?scale:1).translationZ(yes?Ui.dp(v.getContext(),8):0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();else{v.setScaleX(yes?scale:1);v.setScaleY(yes?scale:1);}}
 static void panel(View v){if(!enabled(v))return;v.setAlpha(0);v.setTranslationX(Ui.dp(v.getContext(),38));v.animate().alpha(1).translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();}
}
