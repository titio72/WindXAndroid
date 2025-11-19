package com.aboni.n2kRouter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.Nullable;

public class CalibProgressView extends MyView {

    private final Paint dialPaint;
    private final Paint calibPaint;
    private final Paint calibOkPaint;
    private final Paint anglePaint;

    private Calibration calibration;

    private Double angle;

    private boolean ok;

    public void setCalibration(Calibration calibration, Double a, boolean ok) {
        this.calibration = calibration;
        angle = a;
        this.ok = ok;
        invalidate();
    }

    public CalibProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ok = false;
        dialPaint = getStrokePaint(getContext(), 4f, getThemeColorId(context, android.R.attr.colorAccent));
        calibPaint = getStrokePaint(getContext(), 12f, getThemeColorId(context, R.attr.calib_progress_color));
        calibOkPaint = getStrokePaint(getContext(), 12f, getThemeColorId(context, R.attr.calib_progress_ok_color));
        anglePaint = getStrokePaint(getContext(), 12f, getThemeColorId(context, R.attr.needle_color));
    }

    public void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        canvas.drawRect(0, h, w, 0, dialPaint);

        if (calibration!=null) {
            int step = 2;
            for (int i = 0; i < 360; i += step) {
                Paint p = (calibration == null || !calibration.isAngleOk(i)) ? dialPaint : (ok?calibOkPaint:calibPaint);
                float x = (float) ((w - 4) * i / 360.0 + 2);
                canvas.drawLine(x, 10, x, h - 10, p);
            }
        }

        if (angle!=null) {
            float x = (float) ((w - 4) * angle / 360.0 + 2);
            canvas.drawLine(x, 2, x, h - 2, anglePaint);
        }
    }
}
