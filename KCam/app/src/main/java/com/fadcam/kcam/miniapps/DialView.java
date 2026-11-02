package com.fadcam.kcam.miniapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Reusable circular dial used by the compass, parking marker and metal detector.
 * <ul>
 *   <li>{@link Mode#COMPASS}: rotating card with N/E/S/W, fixed pointer at the top</li>
 *   <li>{@link Mode#ARROW}: fixed ring with an arrow pointing at {@code arrowDegrees}</li>
 *   <li>{@link Mode#GAUGE}: 270° gauge filled to {@code fraction} (0..1)</li>
 * </ul>
 */
public class DialView extends View {

    public enum Mode { COMPASS, ARROW, GAUGE }

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private Mode mode = Mode.COMPASS;
    private float headingDegrees;
    private float arrowDegrees;
    private float fraction;
    private int accentColor = 0xFFE43C3C;

    public DialView(Context context) {
        super(context);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFF3A3A3A);
        tick.setColor(0xFFBDBDBD);
        tick.setStrokeCap(Paint.Cap.ROUND);
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        accent.setColor(accentColor);
        accent.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        invalidate();
    }

    /** Compass heading (degrees clockwise from north). */
    public void setHeading(float degrees) {
        headingDegrees = degrees;
        invalidate();
    }

    /** Arrow direction relative to the top of the view (degrees clockwise). */
    public void setArrow(float degrees) {
        arrowDegrees = degrees;
        invalidate();
    }

    public void setFraction(float f) {
        fraction = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    public void setAccentColor(int color) {
        accentColor = color;
        accent.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f * 0.88f;
        ring.setStrokeWidth(r * 0.04f);
        tick.setStrokeWidth(r * 0.012f);
        label.setTextSize(r * 0.16f);

        switch (mode) {
            case COMPASS:
                drawCompass(canvas, cx, cy, r);
                break;
            case ARROW:
                canvas.drawCircle(cx, cy, r, ring);
                drawArrow(canvas, cx, cy, r, arrowDegrees);
                break;
            case GAUGE:
                drawGauge(canvas, cx, cy, r);
                break;
        }
    }

    private void drawCompass(Canvas canvas, float cx, float cy, float r) {
        canvas.drawCircle(cx, cy, r, ring);
        canvas.save();
        canvas.rotate(-headingDegrees, cx, cy);
        for (int d = 0; d < 360; d += 10) {
            float len = d % 30 == 0 ? r * 0.12f : r * 0.06f;
            canvas.save();
            canvas.rotate(d, cx, cy);
            canvas.drawLine(cx, cy - r, cx, cy - r + len, tick);
            canvas.restore();
        }
        String[] names = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            canvas.save();
            canvas.rotate(i * 90, cx, cy);
            label.setColor(i == 0 ? accentColor : Color.WHITE);
            canvas.drawText(names[i], cx, cy - r * 0.68f, label);
            canvas.restore();
        }
        label.setColor(Color.WHITE);
        canvas.restore();
        // fixed lubber line at the top
        accent.setStrokeWidth(r * 0.03f);
        canvas.drawLine(cx, cy - r * 1.08f, cx, cy - r * 0.86f, accent);
    }

    private void drawArrow(Canvas canvas, float cx, float cy, float r, float deg) {
        canvas.save();
        canvas.rotate(deg, cx, cy);
        path.reset();
        path.moveTo(cx, cy - r * 0.8f);
        path.lineTo(cx + r * 0.22f, cy + r * 0.35f);
        path.lineTo(cx, cy + r * 0.18f);
        path.lineTo(cx - r * 0.22f, cy + r * 0.35f);
        path.close();
        accent.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, accent);
        canvas.restore();
    }

    private void drawGauge(Canvas canvas, float cx, float cy, float r) {
        float start = 135f, sweep = 270f;
        android.graphics.RectF oval = new android.graphics.RectF(cx - r, cy - r, cx + r, cy + r);
        ring.setStrokeWidth(r * 0.1f);
        ring.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawArc(oval, start, sweep, false, ring);
        accent.setStyle(Paint.Style.STROKE);
        accent.setStrokeWidth(r * 0.1f);
        if (fraction > 0.001f) canvas.drawArc(oval, start, sweep * fraction, false, accent);
    }
}
