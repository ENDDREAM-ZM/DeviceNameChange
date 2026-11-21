package com.enddream.dnc;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class MiuixSwitch extends View {

    public interface OnChange {
        void onChange(boolean checked);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean checked;
    private float pos;
    private OnChange listener;

    public MiuixSwitch(Context c) {
        super(c);
        track.setColor(0xFFD8D8DC);
        knob.setColor(Color.WHITE);
        knob.setShadowLayer(dp(1.2f), 0f, dp(0.8f), 0x33000000);
        setClickable(true);
        setFocusable(true);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setChecked(boolean v) {
        setChecked(v, true);
    }

    public void setChecked(boolean v, boolean animate) {
        if (checked == v) {
            return;
        }
        checked = v;
        float from = pos;
        float to = v ? 1f : 0f;
        if (!animate) {
            pos = to;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(160L);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> {
            pos = (float) an.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setOnChangeListener(OnChange l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension((int) dp(50), (int) dp(30));
    }

    @Override
    public boolean performClick() {
        toggle();
        return true;
    }

    private void toggle() {
        boolean want = !checked;
        if (listener != null) {
            listener.onChange(want);
        } else {
            setChecked(want);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight();
        float w = getWidth();
        float pad = dp(2);
        float radius = h / 2f;
        rect.set(0f, 0f, w, h);
        int off = 0xFFD8D8DC;
        int on = isNight() ? 0xFF7FB4F5 : 0xFF2C7FE0;
        track.setColor(blend(off, on, pos));
        c.drawRoundRect(rect, radius, radius, track);

        float knobR = radius - pad;
        float travel = w - 2 * (knobR + pad);
        float cx = knobR + pad + travel * pos;
        c.drawCircle(cx, h / 2f, knobR, knob);
    }

    private boolean isNight() {
        int flags = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return flags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
