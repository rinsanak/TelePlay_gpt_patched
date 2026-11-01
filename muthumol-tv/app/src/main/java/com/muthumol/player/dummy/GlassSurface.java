package com.muthumol.player.dummy;
import android.content.Context;import android.graphics.*;import android.graphics.drawable.Drawable;
/** Static translucent glass treatment; never captures or re-blurs the screen. */
final class GlassSurface extends Drawable {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final float radius,stroke;private final boolean focused,button;private int alpha=255;
 GlassSurface(Context c,int corners,boolean focus,boolean control){radius=Ui.dp(c,corners);stroke=Ui.dp(c,1);focused=focus;button=control;}
 @Override public void draw(Canvas c){Rect b=getBounds();float left=b.left+stroke,top=b.top+stroke,right=b.right-stroke,bottom=b.bottom-stroke;RectF r=new RectF(left,top,right,bottom);p.setAlpha(alpha);p.setStyle(Paint.Style.FILL);
  int[] colors=focused?new int[]{0xd9f6fbff,0xd9dcecfb}:button?new int[]{0x533d5679,0x66303f5c}:new int[]{0x422b3a58,0x70202b44};p.setShader(new LinearGradient(left,top,right,bottom,colors,null,Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setShader(new LinearGradient(left,top,right,bottom,new int[]{focused?0xffeafaff:0x80e4f3ff,focused?0xff8bd9e8:0x16c4d9ee},null,Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);
  p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(left,top,left,top+Math.max(10,(bottom-top)*.35f),new int[]{0x20ffffff,0x00ffffff},null,Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);p.setAlpha(255);
 }
 @Override public void setAlpha(int a){alpha=a;invalidateSelf();}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
