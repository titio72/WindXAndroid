package com.aboni.n2kRouter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;

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

    private Bitmap cache;

    private CompassData compassData;

    private int angle = 0;
    private int angleSmooth = 0;
    private int angleOut = 0;
    private float err = 1.0f;
    private Calibration calibration;
    private final Paint dialPaint;
    private final Paint anglePaint;
    private final Paint angleOutPaint;
    private final Paint angleSmoothPaint;
    private final Paint calibPaint;
    private final Paint starboardPaint;
    private final Paint portPaint;
    private int desiredHeight = 3;
    private int desiredWidth = 3;

    public DialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        int needleColor = getThemeColorId(context, R.attr.needle_color);
        int smoothNeedleColor = getThemeColorId(context, R.attr.smooth_needle_color);
        int outNeedleColor = getThemeColorId(context, R.attr.out_needle_color);
        int greenSectorColor = getThemeColorId(context, R.attr.green_sector_color);
        int redSectorColor = getThemeColorId(context, R.attr.red_sector_color);
        int calibProgressColor = getThemeColorId(context, R.attr.calib_progress_color);
        dialPaint = getStrokePaint(getContext(), 4f, getThemeColorId(context, android.R.attr.colorAccent));
        dialPaint.setTextSize(50);
        anglePaint = getStrokePaint(getContext(), 24f, needleColor);
        angleOutPaint = getStrokePaint(getContext(), 24f, outNeedleColor);
        angleSmoothPaint = getStrokePaint(getContext(), 24f, smoothNeedleColor);
        starboardPaint = getStrokePaint(getContext(), 12f, greenSectorColor);
        portPaint = getStrokePaint(getContext(), 12f, redSectorColor);
        calibPaint = getStrokePaint(getContext(), 12f, calibProgressColor);
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

    public void setAngle(int angle, int smooth, int angleOut) {
        this.angle = angle;
        this.angleSmooth = smooth;
        this.angleOut = angleOut;
    }

    public void setErr(float err) {
        this.err = err;
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

    private void drawCrossAir(Canvas canvas, CompassData d) {
        canvas.drawLine(0, getHeight()/2.0f, getWidth()/2.0f - 0.5f * d.rDial, getHeight()/2.0f, dialPaint);
        canvas.drawLine(getWidth()/2.0f + 0.5f * d.rDial, getHeight()/2.0f, getWidth(), getHeight()/2.0f, dialPaint);
        canvas.drawLine(getWidth()/2.0f, 0, getWidth()/2.0f, getHeight()/2.0f - 0.5f * d.rDial, dialPaint);
        canvas.drawLine(getWidth()/2.0f, getHeight()/2.0f + 0.5f * d.rDial, getWidth()/2.0f, getHeight(), dialPaint);
    }

    private void drawRedGreenSectors(Canvas canvas, CompassData d) {
        float quadFactor = 0.95f * d.rDial;
        canvas.drawArc((d.cx - quadFactor), (d.cy - quadFactor), (d.cx + quadFactor), (d.cy + quadFactor), 300.0f, 120.0f, false, starboardPaint);
        canvas.drawArc((d.cx - quadFactor), (d.cy - quadFactor), (d.cx + quadFactor), (d.cy + quadFactor), 120.0f, 120.0f, false, portPaint);
    }

    private void drawDial(Canvas canvas, CompassData d) {
        float totA = 0.0f;
        int step = 2;
        for (int i = 0; i < 360; i+=step) {
            float a0 = (i == 0) ? 0 : (i - step);
            canvas.rotate((float) i - a0, d.cx, d.cy);
            totA = (float) i;
            canvas.drawLine(d.cx, d.cy - d.rDial, d.cx, (d.cy - (d.rDial * 1.1f)), dialPaint);
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
    }

    void drawCalibration(Canvas canvas, CompassData d) {
        float totA = 0.0f;
        int step = 2;
        for (int i = 0; i < 360; i+=step) {
            float a0 = (i == 0) ? 0 : (i - step);
            canvas.rotate((float) i - a0, d.cx, d.cy);
            totA = (float) i;
            if (calibration.isAngleOk(i)) {
                canvas.drawLine(d.cx, d.cy - (d.rDial * 1.05f) , d.cx, (d.cy - (d.rDial * 1.08f)), calibPaint);
            }
        }
        canvas.rotate(-totA, d.cx, d.cy);
    }

    private void drawNeedle(Canvas canvas, CompassData d, Paint paint, float angle, float err) {
        if (angle!=-1) {
            canvas.rotate(angle, d.cx, d.cy);
            Path path = new Path();
            path.moveTo(d.cx, d.cy - d.rDial);
            path.lineTo(d.cx - d.rDial * 0.03f, d.cy - d.rDial * 1.08f);
            path.lineTo(d.cx + d.rDial * 0.03f, d.cy - d.rDial * 1.08f);
            path.close();
            canvas.drawPath(path, paint);
            if (err>0.0f) canvas.drawLine(d.cx, d.cy - (d.rDial * 0.8f) * err, d.cx, d.cy - d.rDial * 0.8f, dialPaint);
            canvas.rotate(-angle, d.cx, d.cy);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        if (cache==null) {
            compassData = fillCompassData();
            cache = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas bpmCanvas = new Canvas(cache);
            drawCrossAir(bpmCanvas, compassData);
            drawRedGreenSectors(bpmCanvas, compassData);
            drawDial(bpmCanvas, compassData);
        }
        canvas.drawBitmap(cache, 0, 0, null);
        if (calibration!=null) drawCalibration(canvas, compassData);
        if (angle!=-1) drawNeedle(canvas, compassData, anglePaint, angle, err);
        if (angleSmooth!=-1) drawNeedle(canvas, compassData, angleSmoothPaint, angleSmooth, -1.0f);
        if (angleOut!=-1) drawNeedle(canvas, compassData, angleOutPaint, angleOut, -1.0f);
    }
}