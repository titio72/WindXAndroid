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
    private final Paint anglePaint;

    private Calibration calibration;

    private Double angle;

    public Calibration getCalibration() {
        return calibration;
    }

    public void setCalibration(Calibration calibration, Double a) {
        this.calibration = calibration;
        angle = a;
        invalidate();
    }

    public CalibProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        dialPaint = getStrokePaint(getContext(), 4f, typedValue.data);
        calibPaint = getStrokePaint(getContext(), 12f, Color.GREEN);
        anglePaint = getStrokePaint(getContext(), 4f, Color.RED);
    }

    public void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        canvas.drawRect(0, h, w, 0, dialPaint);

        if (calibration!=null) {
            int step = 2;
            for (int i = 0; i < 360; i += step) {
                Paint p = (calibration == null || !calibration.isAngleOk(i)) ? dialPaint : calibPaint;
                float x = (float) ((w - 4) * i / 360.0 + 2);
                canvas.drawLine(x, 2, x, h - 2, p);
            }
        }

        if (angle!=null) {
            float x = (float) ((w - 4) * angle / 360.0 + 2);
            canvas.drawLine(x, 2, x, h - 2, anglePaint);
        }
    }
}
