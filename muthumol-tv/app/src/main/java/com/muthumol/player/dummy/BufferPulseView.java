package com.muthumol.player.dummy;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Three travelling light bars; no images, animators or allocations per frame. */
final class BufferPulseView extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private int color;
  BufferPulseView(Context c,int color){super(c);this.color=color;setContentDescription("Loading video");}
  void setColor(int value){color=value;invalidate();}
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if(getVisibility()!=VISIBLE||!isShown())return;
    float phase=(SystemClock.uptimeMillis()%1200)/1200f;
    float unit=getWidth()/9f,mid=getHeight()/2f;
    paint.setColor(color);
    for(int i=0;i<3;i++) {
      float pulse=(float)(.5+.5*Math.cos((phase-i/3f)*Math.PI*2));
      paint.setAlpha((int)(65+190*pulse));
      float x=unit*(2+i*2.5f),half=getHeight()*(.12f+.23f*pulse);
      canvas.drawRoundRect(x-unit*.35f,mid-half,x+unit*.35f,mid+half,unit*.35f,unit*.35f,paint);
    }
    postInvalidateDelayed(50);
  }
  @Override protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);if(visibility==VISIBLE)invalidate();}
}
