package com.aboni.n2kRouter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.Nullable;

public class CalibView extends MyView {

    private final Paint dialPaint;
    private final Paint anglePaint;
    private final Paint rangePaint;
    private final Paint rangeSelectedPaint;


    private boolean sel = false;
    private int low = 0;
    private int high = 4096;
    private int max = 4096;
    private int value = 0;

    public CalibView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        dialPaint = getStrokePaint(getContext(), 4f, typedValue.data);
        anglePaint = getStrokePaint(getContext(), 12f, Color.RED);
        rangePaint = getFillPaint(getContext(), 0.0f, typedValue.data);
        rangeSelectedPaint = getFillPaint(getContext(), 0.0f, Color.YELLOW);
    }

    public void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), dialPaint);
        float start = (float) (getWidth() * getLow()) /getMax();
        float end = (float) (getWidth() * getHigh()) /getMax();
        float v = (float) (getWidth() * getValue()) /getMax();
        canvas.drawRect(start, 0, end, getHeight(), sel?rangeSelectedPaint:rangePaint);
        canvas.drawLine(v, 0, v, getHeight(), anglePaint);
    }

    public void setAll(int low, int value, int high) {
        setValue(value);
        setLow(low);
        setHigh(high);
        invalidate();
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public int getHigh() {
        return high;
    }

    public void setHigh(int high) {
        this.high = high;
    }

    public int getLow() {
        return low;
    }

    public void setLow(int low) {
        this.low = low;
    }

    public boolean isCalSelected() {
        return sel;
    }

    public void setCalSelected(boolean selected) {
        this.sel = selected;
    }
}
