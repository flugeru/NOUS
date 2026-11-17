package com.limelight.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.cardview.widget.CardView;

/** A cover whose height follows the width of a library column. */
public class GameCoverView extends CardView {
    public GameCoverView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = Math.round(width * (9f / 16f));
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
