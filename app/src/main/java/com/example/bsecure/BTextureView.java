package com.example.bsecure;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

import androidx.annotation.NonNull;

public class BTextureView extends TextureView {
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    public BTextureView(@NonNull Context context) {
        this(context, null);
    }
    public BTextureView(Context con, AttributeSet attrs) {
        this(con, attrs, 0);
    }
    public BTextureView(Context con, AttributeSet attrs, int defStyle) {
        super(con, attrs, defStyle);
    }
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
        mRatioHeight = height;
        mRatioWidth = width;
        requestLayout();
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }
}
