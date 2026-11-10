package com.ikp.dev.app.ui.utilities;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** A device heading compass with a fixed Qibla bearing pointer. */
public final class CompassView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float azimuth;
    private float bearing;
    private boolean hasHeading;

    public CompassView(Context c) { super(c); init(); }
    public CompassView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }
    public CompassView(Context c, @Nullable AttributeSet a, int d) { super(c, a, d); init(); }
    private void init() { setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

    public void setAngles(float azimuth, float bearing) {
        this.azimuth = azimuth;
        this.bearing = bearing;
        this.hasHeading = true;
        invalidate();
    }

    public void setBearing(float bearing) { this.bearing = bearing; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * .47f;
        canvas.save();
        canvas.rotate(hasHeading ? -azimuth : 0, cx, cy);
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0x14C59B4B);
        canvas.drawCircle(cx, cy, radius + dp(5), p);
        p.setColor(0xFFC59B4B); canvas.drawCircle(cx, cy, radius, p);
        p.setColor(0xFF0D3B2E); canvas.drawCircle(cx, cy, radius - dp(5), p);
        p.setColor(0xFFD4AF72); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1));
        canvas.drawCircle(cx, cy, radius - dp(19), p);
        p.setStyle(Paint.Style.FILL);
        float inner = radius - dp(24);
        p.setShader(new RadialGradient(cx, cy, inner, new int[]{0xFFFFFCF4,0xFFF2E8D6}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, inner, p); p.setShader(null);

        for (int deg=0; deg<360; deg+=5) {
            float a=(float)Math.toRadians(deg), r1=radius-dp(deg%45==0?14:deg%15==0?10:7), r2=radius-dp(3);
            p.setColor(deg%45==0?0xFFE6C77C:0x99E6C77C); p.setStrokeWidth(dp(deg%45==0?1.8f:1));
            canvas.drawLine(cx+(float)Math.sin(a)*r1, cy-(float)Math.cos(a)*r1,
                    cx+(float)Math.sin(a)*r2, cy-(float)Math.cos(a)*r2, p);
        }
        String[] labels={"N","NE","E","SE","S","SW","W","NW"};
        p.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        for (int i=0;i<8;i++) {
            float a=(float)Math.toRadians(i*45), rr=radius-dp(34);
            p.setTextSize(dp(i%2==0?22:15)); p.setColor(i==0?0xFFFFD67A:0xFFFFFFFF);
            canvas.drawText(labels[i], cx+(float)Math.sin(a)*rr, cy-(float)Math.cos(a)*rr+dp(7), p);
        }
        // Fine geometric rosette on the ivory face.
        p.setColor(0x22C59B4B); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1));
        for (int i=0;i<8;i++) {
            float a=(float)Math.toRadians(i*45);
            canvas.drawLine(cx+(float)Math.sin(a)*dp(30),cy-(float)Math.cos(a)*dp(30),
                    cx+(float)Math.sin(a)*inner*0.68f,cy-(float)Math.cos(a)*inner*0.68f,p);
        }
        canvas.restore();

        // Needle points toward Qibla relative to the phone's current heading.
        canvas.save();
        if (hasHeading) canvas.rotate(bearing-azimuth,cx,cy);
        float tip=inner*.72f, tail=inner*.48f;
        Path needle=new Path(); needle.moveTo(cx,cy-tip); needle.lineTo(cx-dp(12),cy+dp(9));
        needle.lineTo(cx,cy+dp(5)); needle.lineTo(cx+dp(12),cy+dp(9)); needle.close();
        p.setStyle(Paint.Style.FILL); p.setColor(0xFFD4A844); canvas.drawPath(needle,p);
        Path back=new Path(); back.moveTo(cx,cy+tail); back.lineTo(cx-dp(8),cy); back.lineTo(cx+dp(8),cy); back.close();
        p.setColor(0xFF0D654A); canvas.drawPath(back,p);
        canvas.restore();
        p.setColor(0xFFC59B4B); canvas.drawCircle(cx,cy,dp(27),p);
        p.setColor(0xFF063B2D); canvas.drawCircle(cx,cy,dp(22),p);
        p.setColor(0xFFF5D77D); canvas.drawRect(cx-dp(12),cy-dp(6),cx+dp(12),cy+dp(9),p);
        p.setColor(0xFF171711); canvas.drawRect(cx-dp(10),cy-dp(4),cx+dp(10),cy+dp(8),p);
        p.setColor(0xFFEBCB67); canvas.drawRect(cx-dp(10),cy-dp(4),cx+dp(10),cy-dp(1),p);
    }
    private float dp(float n) { return n*getResources().getDisplayMetrics().density; }
}
