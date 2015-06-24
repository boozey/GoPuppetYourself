package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Created by Nathan on 6/8/2015.
 */
public class Puppet extends RelativeLayout implements Serializable {
    private static final String LOG_TAG = "Puppet";

    public static final int ROTATION_CW = 1;
    public static final int ROTATION_CCW = -1;
    public static final int PROFILE_RIGHT = 0;
    public static final int PROFILE_LEFT = 1;

    // Instance variables
    transient private Context context;
    transient public ImageView lowerJaw, upperJaw;
    transient private Point upperPivotPoint, lowerPivotPoint;
    transient private int orientation = 0;
    transient private Bitmap upperJawBitmap, lowerJawBitmap;
    transient private int upperLeftPadding = 0, upperRightPadding = 0, lowerLeftPadding = 0, lowerRightPadding = 0;
    transient private int upperBitmapWidth, upperBitmapHeight, lowerBitmapWidth, lowerBitmapHeight;
    transient private String name = getResources().getString(R.string.default_puppet_name);
    transient private String path = "";
    transient private boolean onStage = true;


    // Constructors
    public Puppet(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        lowerJaw = new ImageView(context);
        lowerJaw.setId(0);
        upperJaw = new ImageView(context);
        upperJaw.setId(1);

    }


    // Setters and getters

    public boolean isOnStage() {
        return onStage;
    }

    public void setOnStage(boolean onStage) {
        this.onStage = onStage;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Bitmap getUpperJawBitmap() {
        return upperJawBitmap;
    }

    public Bitmap getLowerJawBitmap() {
        return lowerJawBitmap;
    }

    public int getUpperLeftPadding() {
        return upperLeftPadding;
    }

    public int getUpperRightPadding() {
        return upperRightPadding;
    }

    public int getLowerLeftPadding() {
        return lowerLeftPadding;
    }

    public int getLowerRightPadding() {
        return lowerRightPadding;
    }

    public int getUpperBitmapWidth() {
        return upperBitmapWidth;
    }

    public int getUpperBitmapHeight() {
        return upperBitmapHeight;
    }

    public int getLowerBitmapWidth() {
        return lowerBitmapWidth;
    }

    public int getLowerBitmapHeight() {
        return lowerBitmapHeight;
    }

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
        upperJawBitmap = bitmap;
        upperBitmapHeight = bitmap.getHeight();
        upperBitmapWidth = bitmap.getWidth();
        upperJaw.setBackground(new BitmapDrawable(getResources(), bitmap));

    }

    public void setLowerJawImage(Bitmap bitmap){
        lowerJawBitmap = bitmap;
        lowerBitmapHeight = bitmap.getHeight();
        lowerBitmapWidth = bitmap.getWidth();
        lowerJaw.setBackground(new BitmapDrawable(getResources(), bitmap));
    }

    public PuppetData getData(String path){
        PuppetData data = new PuppetData();
        data.setOrientation(this.orientation);
        data.setLowerPivotPointx(this.lowerPivotPoint.x);
        data.setLowerPivotPointy(this.lowerPivotPoint.y);
        data.setUpperPivotPointx(this.upperPivotPoint.x);
        data.setUpperPivotPointy(this.upperPivotPoint.y);
        data.setLowerLeftPadding(lowerLeftPadding);
        data.setLowerRightPadding(lowerRightPadding);
        data.setUpperLeftPadding(upperLeftPadding);
        data.setUpperRightPadding(upperRightPadding);
        data.setLowerJawBitmapPath(Utils.WriteImage(lowerJawBitmap, path + File.pathSeparator + "lowerJaw"));
        data.setUpperJawBitmapPath(Utils.WriteImage(upperJawBitmap, path + File.pathSeparator + "upperJaw"));
        return data;
    }

    // Public methods
    public void applyLayoutParams(){
        removeAllViews();
        int upperLeft = upperPivotPoint.x;
        int upperRight = upperBitmapWidth - upperPivotPoint.x;
        int lowerLeft = lowerPivotPoint.x;
        int lowerRight = lowerBitmapWidth - lowerPivotPoint.x;
        int topPadding = (int)Math.sqrt(upperBitmapWidth*upperBitmapWidth + upperBitmapHeight*upperBitmapHeight) - upperBitmapHeight;

        if (upperLeft > lowerLeft) lowerLeftPadding = upperLeft - lowerLeft;
        else upperLeftPadding = lowerLeft - upperLeft;
        if (upperRight > lowerRight) lowerRightPadding = upperRight - lowerRight;
        else upperRightPadding = lowerRight - upperRight;

        RelativeLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(upperLeftPadding, topPadding, upperRightPadding, 0);
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

    public void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(name);
        out.writeInt(orientation);
        out.writeBoolean(onStage);
        out.writeInt(upperPivotPoint.x);
        out.writeInt(upperPivotPoint.y);
        out.writeInt(lowerPivotPoint.x);
        out.writeInt(lowerPivotPoint.y);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        upperJawBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        int byteLength = stream.toByteArray().length;
        out.writeInt(byteLength);
        out.write(stream.toByteArray());

        stream = new ByteArrayOutputStream();
        lowerJawBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byteLength = stream.toByteArray().length;
        out.writeInt(byteLength);
        out.write(stream.toByteArray());

    }

    public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
        name = (String)in.readObject();
        orientation = in.readInt();
        onStage = in.readBoolean();
        upperPivotPoint = new Point(in.readInt(), in.readInt());
        lowerPivotPoint = new Point(in.readInt(), in.readInt());

        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        upperJawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Log.d(LOG_TAG, "Bitmap size " + bytes.length);
        setUpperJawImage(upperJawBitmap);

        bytes = new byte[in.readInt()];
        in.readFully(bytes);
        lowerJawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        setLowerJawImage(lowerJawBitmap);
        applyLayoutParams();
    }
}