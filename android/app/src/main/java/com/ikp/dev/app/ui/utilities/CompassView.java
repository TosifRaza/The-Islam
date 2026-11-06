package com.ikp.dev.app.ui.utilities;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Minimal dial + qibla needle compass view (drawn; no external dependency). */
public final class CompassView extends View {
    private float azimuth;
    private float bearing;

    public CompassView(Context context) {
        super(context);
    }

    public CompassView(Context context, @androidx.annotation.Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public CompassView(Context context, @androidx.annotation.Nullable android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAngles(float azimuth, float bearing) {
        this.azimuth = azimuth;
        this.bearing = bearing;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 12f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFF164638);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText("N", cx - 8, cy - radius + 2, paint);
        float needleAngle = bearing - azimuth;
        double rad = Math.toRadians(needleAngle);
        float dx = (float) Math.sin(rad) * (radius - 16);
        float dy = (float) -Math.cos(rad) * (radius - 16);
        Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
        needle.setColor(0xFFC7A75A);
        needle.setStyle(Paint.Style.STROKE);
        needle.setStrokeWidth(6f);
        canvas.drawLine(cx, cy, cx + dx, cy + dy, needle);
        canvas.drawCircle(cx + dx, cy + dy, 8, needle);
    }
}
