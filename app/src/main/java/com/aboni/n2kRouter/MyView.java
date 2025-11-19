package com.aboni.n2kRouter;

import android.content.Context;
import android.content.res.Configuration;
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

    protected int getThemeColorId(Context context, int id) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(id, typedValue, true);
        return typedValue.data;
    }

    boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}