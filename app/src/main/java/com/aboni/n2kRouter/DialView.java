package com.aboni.n2kRouter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DialView extends MyView {
    public static class CompassData {
        public float w;
        public float h;
        public float pb;
        public float pt;
        public float pl;
        public float pr;

        public float cx;
        public float cy;
        public float r;
        public float rDial;
    }

    private int angle = 0;
    private int angleSmooth = 0;
    private float err = 1.0f;
    private Calibration calibration;
    private final Paint dialPaint;
    private final Paint anglePaint;
    private final Paint angleSmoothPaint;
    private final Paint calibPaint;
    private final Paint starboardPaint;
    private final Paint portPaint;
    private int desiredHeight = 3;
    private int desiredWidth = 3;

    public DialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        dialPaint = getStrokePaint(getContext(), 4f, typedValue.data);
        dialPaint.setTextSize(50);
        anglePaint = getStrokePaint(getContext(), 24f, Color.RED);
        angleSmoothPaint = getStrokePaint(getContext(), 24f, Color.BLUE);
        starboardPaint = getStrokePaint(getContext(), 12f, Color.GREEN);
        portPaint = getStrokePaint(getContext(), 12f, Color.RED);
        calibPaint = getStrokePaint(getContext(), 12f, 0xAAAA6600);
    }

    public int getDesiredHeight() {
        return desiredHeight;
    }

    public void setDesiredHeight(int desiredHeight) {
        this.desiredHeight = desiredHeight;
    }

    public int getDesiredWidth() {
        return desiredWidth;
    }

    public void setDesiredWidth(int desiredWidth) {
        this.desiredWidth = desiredWidth;
    }

    public Calibration getCalibration() {
        return calibration;
    }

    public void setCalibration(Calibration calibration) {
        if (calibration==null) this.calibration = null;
        else this.calibration = new Calibration(calibration);
    }

    public int getAngle() {
        return angle;
    }

    public int getAngleSmooth() {
        return angleSmooth;
    }

    public void setAngle(int angle, int smooth) {
        this.angle = angle;
        this.angleSmooth = smooth;
    }

    public void setErr(float err) {
        this.err = err;
    }

    public float getErr() {
        return err;
    }

    protected CompassData fillCompassData() {
        CompassData cd = new CompassData();
        cd.w = getWidth();
        cd.h = getHeight();
        cd.pb = getPaddingBottom();
        cd.pt = getPaddingTop();
        cd.pl = getPaddingLeft();
        cd.pr = getPaddingRight();
        cd.cx = (cd.pl + (cd.w - cd.pr)) * 0.5f;
        cd.cy = (cd.pt + (cd.h - cd.pb)) * 0.5f;
        float rx = Math.min(cd.w - cd.pr - cd.cx, cd.cx - cd.pl);
        float ry = Math.min(cd.h - cd.pb - cd.cy, cd.cy - cd.pt);
        cd.r = Math.min(rx, ry);
        cd.rDial = cd.r * 0.8f;
        return cd;
    }

    private final Rect rect = new Rect();

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        CompassData d = fillCompassData();
        canvas.drawLine(0, getHeight()/2.0f, getWidth()/2.0f - 0.5f * d.rDial, getHeight()/2.0f, dialPaint);
        canvas.drawLine(getWidth()/2.0f + 0.5f * d.rDial, getHeight()/2.0f, getWidth(), getHeight()/2.0f, dialPaint);
        canvas.drawLine(getWidth()/2.0f, 0, getWidth()/2.0f, getHeight()/2.0f - 0.5f * d.rDial, dialPaint);
        canvas.drawLine(getWidth()/2.0f, getHeight()/2.0f + 0.5f * d.rDial, getWidth()/2.0f, getHeight(), dialPaint);

        float quadFactor = 0.95f * d.rDial;
        canvas.drawArc((d.cx - quadFactor), (d.cy - quadFactor), (d.cx + quadFactor), (d.cy + quadFactor), 300.0f, 120.0f, false, starboardPaint);
        canvas.drawArc((d.cx - quadFactor), (d.cy - quadFactor), (d.cx + quadFactor), (d.cy + quadFactor), 120.0f, 120.0f, false, portPaint);

        float totA = 0.0f;
        int step = 2;
        for (int i = 0; i < 360; i+=step) {
            float a0 = (i == 0) ? 0 : (i - step);
            canvas.rotate((float) i - a0, d.cx, d.cy);
            totA = (float) i;
            canvas.drawLine(d.cx, d.cy - d.rDial, d.cx, (d.cy - (d.rDial * 1.1f)), dialPaint);
            if (calibration!=null && calibration.isAngleOk(i)) {
                canvas.drawLine(d.cx, d.cy - (d.rDial * 1.05f) , d.cx, (d.cy - (d.rDial * 1.08f)), calibPaint);
            }
            if (i % 30 == 0) {
                int x = i>180 ? 360 - i : i;
                String t = "" + x;//String.format("%d", x);
                dialPaint.getTextBounds(t, 0, t.length(), rect);
                canvas.drawText(t, 0, t.length(), d.cx - (rect.width() / 2f), (d.cy - (d.rDial * 1.01f)) - rect.height(), dialPaint);
                canvas.drawLine(d.cx, d.cy - (d.rDial * 0.9f), d.cx, (d.cy - (d.rDial * 1.1f)), dialPaint);
            }
        }
        canvas.rotate(-totA, d.cx, d.cy);
        canvas.drawCircle(d.cx, d.cy, d.rDial * 0.8f, dialPaint);
        if (angle!=-1) {
            canvas.rotate(angle, d.cx, d.cy);
            canvas.drawLine(d.cx, d.cy - d.rDial, d.cx, (d.cy - (d.rDial * 1.1f)), anglePaint);
            canvas.drawLine(d.cx, d.cy - (d.rDial * 0.8f) * err, d.cx, d.cy - d.rDial * 0.8f, dialPaint);
            canvas.rotate(-angle, d.cx, d.cy);
        }
        if (angleSmooth!=-1) {
            canvas.rotate(angleSmooth, d.cx, d.cy);
            canvas.drawLine(d.cx, d.cy - d.rDial, d.cx, (d.cy - (d.rDial * 1.1f)), angleSmoothPaint);
            canvas.rotate(-angleSmooth, d.cx, d.cy);
        }
    }
}