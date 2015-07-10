package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
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
public class Puppet extends View implements Serializable {
    private static final String LOG_TAG = "Puppet";

    public static final int ROTATION_CW = 1;
    public static final int ROTATION_CCW = -1;
    public static final int PROFILE_RIGHT = 0;
    public static final int PROFILE_LEFT = 1;

    // Instance variables
    transient private Context context;
    transient private Point upperPivotPoint, lowerPivotPoint;
    transient private int orientation = 0;
    transient private Bitmap upperJawBitmap, lowerJawBitmap;
    transient private Canvas upperJawCanvas, lowerJawCanvas;
    transient private int upperLeftPadding = 0, upperRightPadding = 0, lowerLeftPadding = 0, lowerRightPadding = 0, topPadding = 0, leftClipPadding = 0, rightClipPadding = 0;
    transient private int upperBitmapWidth, upperBitmapHeight, lowerBitmapWidth, lowerBitmapHeight;
    transient private String name = getResources().getString(R.string.default_puppet_name);
    transient private String path = "";
    transient private boolean onStage = true;
    transient private float scaleX = 1;
    transient private float scaleY = 1;
    transient private Matrix upperJawMatrix, lowerJawMatrix;
    transient private int degrees;
    transient private boolean isFlippedHorz = false;


    // Constructors
    public Puppet(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        upperJawMatrix = new Matrix();
        lowerJawMatrix = new Matrix();
    }

    // Setters and getters
    public int getTotalWidth(){
        return upperBitmapWidth + upperLeftPadding + upperRightPadding;
    }
    public int getTotalHeight(){
        return topPadding + upperBitmapHeight + lowerBitmapHeight;
    }

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

    public int getLeftClipPadding(){
        return leftClipPadding;
    }
    public int getRightClipPadding(){
        return rightClipPadding;
    }
    public int getTopClipPadding() {
        return topPadding;
    }

    public Point getLowerPivotPoint() {
        return lowerPivotPoint;
    }
    public void setLowerPivotPoint(Point lowerPivotPoint) {
        this.lowerPivotPoint = lowerPivotPoint;
    }

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
    }

    public void setUpperJawImage(Bitmap bitmap){
        upperJawBitmap = bitmap;
        upperBitmapHeight = bitmap.getHeight();
        upperBitmapWidth = bitmap.getWidth();
    }

    public void setLowerJawImage(Bitmap bitmap){
        lowerJawBitmap = bitmap;
        lowerBitmapHeight = bitmap.getHeight();
        lowerBitmapWidth = bitmap.getWidth();
        Matrix matrix = new Matrix();
        matrix.postScale(scaleX, scaleY);
        //lowerJawCanvas = new Canvas(lowerJawBitmap);
    }

    // Public methods
    // Movement methods
    public void OpenMouth(int degrees){
        this.degrees = degrees;
    }
    public void FlipHoriz(){
        Matrix m = new Matrix();
        m.preScale(-1, 1);
        upperJawBitmap = Bitmap.createBitmap(upperJawBitmap, 0, 0, upperJawBitmap.getWidth(), upperJawBitmap.getHeight(), m, false);
        lowerJawBitmap = Bitmap.createBitmap(lowerJawBitmap, 0, 0, lowerJawBitmap.getWidth(), lowerJawBitmap.getHeight(), m, false);
        upperPivotPoint.x = upperBitmapWidth - upperPivotPoint.x;
        lowerPivotPoint.x = lowerBitmapWidth - lowerPivotPoint.x;
        if (orientation == PROFILE_LEFT) orientation = PROFILE_RIGHT;
        else orientation = PROFILE_LEFT;
        setPadding();
        isFlippedHorz = !isFlippedHorz;
        invalidate();
        requestLayout();
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){
        setPadding();
    }
    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec){
        setMeasuredDimension((int) ((upperLeftPadding + upperBitmapWidth + upperRightPadding) * scaleX), (int) ((topPadding + upperBitmapHeight + lowerBitmapHeight) * scaleY));
    }
    private void setPadding(){
        leftClipPadding = 0;
        rightClipPadding = 0;
        lowerLeftPadding = 0;
        upperLeftPadding = 0;
        upperRightPadding = 0;
        lowerRightPadding = 0;
        int upperLeft = upperPivotPoint.x;
        int upperRight = upperBitmapWidth - upperPivotPoint.x;
        int lowerLeft = lowerPivotPoint.x;
        int lowerRight = lowerBitmapWidth - lowerPivotPoint.x;
        if (orientation == PROFILE_RIGHT){
            // Calculate top padding
            double h = upperBitmapHeight, x = upperBitmapWidth - upperPivotPoint.x;
            double l = Math.sqrt(h*h + x*x);
            double theta = Math.atan(h/x);
            topPadding = (int)(l * Math.sin(theta + 30*3.14/180) - h);
            // Calculate left padding
            x = upperPivotPoint.x; // Set x value to distance from left
            leftClipPadding = (int)(Math.sqrt(h*h + x*x) * Math.cos(Math.atan(h/x) - 30*Math.PI/180) - x);
        }
        else{
            // Calculate top padding
            double h = upperBitmapHeight, x = upperPivotPoint.x;
            double l = Math.sqrt(h*h + x*x);
            double theta = Math.atan(h/x);
            topPadding = (int)(l * Math.sin(theta + 30*3.14/180) - h);
            // Calculate right padding
            x = upperBitmapWidth - upperPivotPoint.x; // Set x value to distance from right
            rightClipPadding = (int)(Math.sqrt(h*h + x*x) * Math.cos(Math.atan(h/x) - 30*Math.PI/180) - x);
        }

        // Decide which sides need padding to keep images aligned
        if (upperLeft > lowerLeft) lowerLeftPadding = upperLeft - lowerLeft;
        else upperLeftPadding = lowerLeft - upperLeft;
        if (upperRight > lowerRight) lowerRightPadding = upperRight - lowerRight;
        else upperRightPadding = lowerRight - upperRight;

        // Set final upper and lower padding, minimum of zero
        leftClipPadding = Math.max(leftClipPadding, 0);
        rightClipPadding = Math.max(rightClipPadding, 0);
        upperLeftPadding += leftClipPadding; Log.d(LOG_TAG, "upper left padding = " + upperLeftPadding);
        lowerLeftPadding += leftClipPadding; Log.d(LOG_TAG, "lower left padding = " + lowerLeftPadding);
        upperRightPadding += rightClipPadding; Log.d(LOG_TAG, "upper right padding = " + upperRightPadding);
        lowerRightPadding += rightClipPadding; Log.d(LOG_TAG, "lower right padding = " + lowerRightPadding);

        upperJawMatrix.setTranslate(upperLeftPadding, topPadding);
        lowerJawMatrix.setTranslate(lowerLeftPadding, topPadding + upperJawBitmap.getHeight());
    }

    @Override
    public void setScaleX(float scaleX){
        this.scaleX = Math.abs(scaleX);
        if (scaleX < 0 && !isFlippedHorz) FlipHoriz();
        if (scaleX > 0 && isFlippedHorz) FlipHoriz();
        invalidate();
        requestLayout();
    }
    @Override
    public float getScaleX(){
        if (isFlippedHorz) return -1 * scaleX;
        else return scaleX;
    }
    @Override
    public void setScaleY(float scaleY){
        this.scaleY = Math.abs(scaleY);
        invalidate();
        requestLayout();
    }
    @Override
    public float getScaleY(){
        return scaleY;
    }


    @Override
    protected void onDraw(Canvas canvas){
        upperJawMatrix.setTranslate(upperLeftPadding, topPadding);
        upperJawMatrix.postRotate(degrees * getPivotDirection(), upperPivotPoint.x + upperLeftPadding, upperPivotPoint.y + topPadding);
        upperJawMatrix.postScale(scaleX, scaleY);
        lowerJawMatrix.setTranslate(lowerLeftPadding, topPadding + upperJawBitmap.getHeight());
        lowerJawMatrix.postScale(scaleX, scaleY);
        canvas.drawBitmap(upperJawBitmap, upperJawMatrix, null);
        canvas.drawBitmap(lowerJawBitmap, lowerJawMatrix, null);
    }


    public Bitmap getThumbnail(){
        // Combine upperjaw and lowerjaw bitmaps into one
        int height, width;
        Bitmap upperJawBitmap = getUpperJawBitmap();
        Bitmap lowerJawBitmap = getLowerJawBitmap();
        width = lowerJawBitmap.getWidth() + getLowerLeftPadding() + getLowerRightPadding();
        height = upperJawBitmap.getHeight() + lowerJawBitmap.getHeight();
        Bitmap overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        canvas.drawBitmap(upperJawBitmap, getUpperLeftPadding(), 0, null);
        canvas.drawBitmap(lowerJawBitmap, getLowerLeftPadding(), upperJawBitmap.getHeight(), null);

        // Scale down to max of 256 by 256
        int maxHeight = 256;
        int maxWidth = 256;
        float scale = Math.min(((float) maxHeight / overlay.getWidth()), ((float) maxWidth / overlay.getHeight()));
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        return Bitmap.createBitmap(overlay, 0, 0, overlay.getWidth(), overlay.getHeight(), matrix, true);
    }

    public byte[] getBytes(){
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            writeObject(oos);
            oos.close();
            bos.close();
        } catch (IOException e)
        {e.printStackTrace();}
        return bos.toByteArray();
    }

    public void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(name);
        out.writeInt(orientation);
        out.writeBoolean(onStage);
        out.writeInt(upperPivotPoint.x);
        out.writeInt(upperPivotPoint.y);
        out.writeInt(lowerPivotPoint.x);
        out.writeInt(lowerPivotPoint.y);
        out.writeFloat(getScaleX());
        out.writeFloat(getScaleY());

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
        Log.d(LOG_TAG, "Byte array size: " + stream.toByteArray().length);

    }
    public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
        name = (String)in.readObject();
        orientation = in.readInt();
        onStage = in.readBoolean();
        upperPivotPoint = new Point(in.readInt(), in.readInt());
        lowerPivotPoint = new Point(in.readInt(), in.readInt());
        scaleX = in.readFloat();
        scaleY =in.readFloat();

        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        upperJawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Log.d(LOG_TAG, "Bitmap size " + bytes.length);
        setUpperJawImage(upperJawBitmap);

        bytes = new byte[in.readInt()];
        in.readFully(bytes);
        lowerJawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        setLowerJawImage(lowerJawBitmap);
        setScaleX(scaleX);
        setScaleY(scaleY);
        setPadding();
        //applyLayoutParams();
    }
}
