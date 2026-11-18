package com.teamdark.core.app;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.teamdark.core.R;

/** Displays the official TeamDarkCore TD monogram on SDK-owned surfaces. */
public final class TeamDarkMonogramView extends ImageView {

    public TeamDarkMonogramView(Context context) {
        super(context);
        init();
    }

    public TeamDarkMonogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TeamDarkMonogramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setScaleType(ScaleType.CENTER_INSIDE);
        setAdjustViewBounds(true);
        setImageResource(R.drawable.teamdark_logo_icon);
        setContentDescription("TeamDarkCore");
    }
}
