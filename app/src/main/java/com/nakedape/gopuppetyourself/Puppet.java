package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/**
 * Created by Nathan on 6/8/2015.
 */
public class Puppet extends RelativeLayout {
    private static final String LOG_TAG = "Puppet";

    public static final int ROTATION_CW = 1;
    public static final int ROTATION_CCW = -1;
    public static final int PROFILE_RIGHT = 0;
    public static final int PROFILE_LEFT = 1;

    private Context context;
    public ImageView lowerJaw, upperJaw;
    private int leftMargin, topMargin;
    private Point upperPivotPoint, lowerPivotPoint;
    private int upperBitmapWidth, upperBitmapHeight, lowerBitmapWidth, lowerBitmapHeight;

    private int orientation = 0;

    public Puppet(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        lowerJaw = new ImageView(context);
        lowerJaw.setId(0);
        upperJaw = new ImageView(context);
        upperJaw.setId(1);
        leftMargin = 0;
        topMargin = 0;

    }

    // Setters and getters
    public Point getUpperPivotPoint() {
        return upperPivotPoint;
    }
    public void setUpperPivotPoint(Point upperPivotPoint) {
        this.upperPivotPoint = upperPivotPoint;
    }
    public Point getLowerPivotPoint() {
        return lowerPivotPoint;
    }
    public void setLowerPivotPoint(Point lowerPivotPoint) {
        this.lowerPivotPoint = lowerPivotPoint;
    }
    public void setUpperJawImage(ImageView upperJaw){this.upperJaw = upperJaw;}
    public ImageView getUpperJaw(){return upperJaw;}
    public void setLowerJawImage(ImageView lowerJaw){this.lowerJaw = lowerJaw;}
    public ImageView getLowerJaw(){return lowerJaw;}
    public void setLeftMargin(int leftMargin){this.leftMargin = leftMargin;}
    public int getLeftMargin(){return leftMargin;}
    public void setTopMargin(int topMargin){this.topMargin = topMargin;}
    public int getTopMargin(){return topMargin;}
    public int getOrientation() {
        return orientation;
    }
    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }
    public int getPivotDirection(){
        if (orientation == PROFILE_RIGHT)
            return ROTATION_CCW;
        else
            return ROTATION_CW;
    }


    public void setImages(Bitmap upperJawBitmap, Bitmap lowerJawBitmap, Point upperPivotPoint, Point lowerPivotPoint){
        this.upperPivotPoint = upperPivotPoint;
        this.lowerPivotPoint = lowerPivotPoint;

        setUpperJawImage(upperJawBitmap);
        setLowerJawImage(lowerJawBitmap);
        applyLayoutParams();

    }

    public void setUpperJawImage(Bitmap bitmap){
        upperBitmapHeight = bitmap.getHeight();
        upperBitmapWidth = bitmap.getWidth();
        upperJaw.setBackground(new BitmapDrawable(getResources(), bitmap));

    }
    public void setLowerJawImage(Bitmap bitmap){
        lowerBitmapHeight = bitmap.getHeight();
        lowerBitmapWidth = bitmap.getWidth();
        lowerJaw.setBackground(new BitmapDrawable(getResources(), bitmap));
    }
    public void applyLayoutParams(){
        removeAllViews();
        int upperLeft = upperPivotPoint.x;
        int upperRight = upperBitmapWidth - upperPivotPoint.x;
        int lowerLeft = lowerPivotPoint.x;
        int lowerRight = lowerBitmapWidth - lowerPivotPoint.x;
        int upperLeftPadding = 0, upperRightPadding = 0, lowerLeftPadding = 0, lowerRightPadding = 0;

        if (upperLeft > lowerLeft) lowerLeftPadding = upperLeft - lowerLeft;
        else upperLeftPadding = lowerLeft - upperLeft;
        if (upperRight > lowerRight) lowerRightPadding = upperRight - lowerRight;
        else upperRightPadding = lowerRight - upperRight;

        RelativeLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(upperLeftPadding, upperBitmapHeight, upperRightPadding, 0);
        upperJaw.setLayoutParams(params);
        Log.d(LOG_TAG, "UpperLeftPadding, UpperRightPadding = " + String.valueOf(upperLeftPadding) + ", " + String.valueOf(upperRightPadding));
        upperJaw.setPivotX(upperPivotPoint.x);
        upperJaw.setPivotY(upperPivotPoint.y);

        RelativeLayout.LayoutParams params2 = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params2.setMargins(lowerLeftPadding, 0, lowerRightPadding, 0);
        params2.addRule(BELOW, upperJaw.getId());
        lowerJaw.setLayoutParams(params2);
        addView(lowerJaw, params2);
        addView(upperJaw, params);
        Log.d(LOG_TAG, "LowerLeftPadding, LowerRightPadding = " + String.valueOf(lowerLeftPadding) + ", " + String.valueOf(lowerRightPadding));
    }
}
