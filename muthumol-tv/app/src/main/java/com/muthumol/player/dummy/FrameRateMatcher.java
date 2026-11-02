package com.muthumol.player.dummy;
import android.hardware.display.DisplayManager;import android.content.Context;import android.content.pm.PackageManager;import android.os.Build;import android.view.*;
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class FrameRateMatcher {
 static void apply(PlayerActivity a,float fps){if(Build.VERSION.SDK_INT<31||!a.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)||!Float.isFinite(fps)||fps<0||fps>240)return;DisplayManager dm=(DisplayManager)a.getSystemService(Context.DISPLAY_SERVICE);if(dm==null||dm.getMatchContentFrameRateUserPreference()!=DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS)return;View view=a.view.getVideoSurfaceView();if(view instanceof SurfaceView){Surface surface=((SurfaceView)view).getHolder().getSurface();if(surface!=null&&surface.isValid())try{surface.setFrameRate(fps,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,Surface.CHANGE_FRAME_RATE_ALWAYS);}catch(Exception ignored){}}}
}
