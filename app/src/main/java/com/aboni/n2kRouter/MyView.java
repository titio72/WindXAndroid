package com.aboni.n2kRouter;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

public abstract class MyView extends View {
    public MyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    protected Paint getStrokePaint(Context c, float width, int color) {
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        return p;
    }

    protected Paint getFillPaint(Context c, float width, int color) {
        Paint p = new Paint();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    protected int resolveThemColor(Context c, int themeColor) {
        TypedValue outValue = new TypedValue();
        c.getTheme().resolveAttribute(themeColor, outValue, true);
        return outValue.data;
    }
}