package com.muthumol.player.dummy;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Touch-transparent, frame paced border; respects reduced motion and attachment. */
final class ColourBorder extends View {
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Matrix matrix=new Matrix();final int[] colors={0xffc8e810,0xffcf10e8,0xff10e8e8,0xff14e810,0xff00ff1e,0xffffae00,0xffc8e810};
 ColourBorder(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(c,3));}
 protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f;SweepGradient gradient=new SweepGradient(x,y,colors,null);matrix.setRotate(TvMotion.enabled(this)?(android.os.SystemClock.uptimeMillis()%9000)*.04f:0,x,y);gradient.setLocalMatrix(matrix);paint.setShader(gradient);float pad=paint.getStrokeWidth()/2;canvas.drawRoundRect(pad,pad,getWidth()-pad,getHeight()-pad,Ui.dp(getContext(),8),Ui.dp(getContext(),8),paint);if(isShown()&&TvMotion.enabled(this))postInvalidateDelayed(33);}
}
