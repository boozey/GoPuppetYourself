package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by Nathan on 6/9/2015.
 */
public class PuppetDesigner extends View {

    private static  final String LOG_TAG = "PuppetDesigner";

    private static final int NO_SELECTION = 0;
    private static final int UPPER_JAW = 1000;
    private static final int UPPER_JAW_TOP = 1001;
    private static final int UPPER_JAW_BOTTOM = 1002;
    private static final int UPPER_JAW_LEFT = 1003;
    private static final int UPPER_JAW_RIGHT = 1004;
    private static final int UPPER_JAW_PIVOT = 1005;
    private static final int LOWER_JAW = 2000;
    private static final int LOWER_JAW_TOP = 2001;
    private static final int LOWER_JAW_BOTTOM = 2002;
    private static final int LOWER_JAW_LEFT = 2003;
    private static final int LOWER_JAW_RIGHT = 2004;
    private static final int LOWER_JAW_PIVOT = 2005;
    private static final int MIN_BOX_SIZE = 80;

    private Context context;
    private Bitmap background, drawBitmap;
    private Rect upperJawBox, lowerJawBox;
    private Point upperJawPivotPoint, lowerJawPivotPoint;
    private Paint upperJawPaint, upperTextPaint, lowerJawPaint, lowerTextPaint, pivotPaint1, pivotPaint2, drawPaint;
    private Path drawPath;
    private Canvas drawCanvas, backgroundCanvas;
    private int edgeThresh = 10, pointThresh = 30;
    private int magicEraseThreshold = 75;
    private int selectionId = 0;
    private float prevX = -1, prevY = -1;
    private boolean isMagicErase = false, showUpperJawBox = false, showLowerJawBox = false,
            pivotsSnapped = true, isDrawMode = false, isEraseMode = false, isBackgroundEraseMode = false;


    private int orientation = Puppet.PROFILE_RIGHT;

    public PuppetDesigner(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        upperJawPaint = new Paint();
        upperJawPaint.setARGB(64, 128, 0, 0);
        upperTextPaint = new Paint();
        upperTextPaint.setColor(Color.RED);
        lowerJawPaint = new Paint();
        lowerJawPaint.setARGB(64, 0, 128, 0);
        lowerTextPaint = new Paint();
        lowerTextPaint.setColor(Color.GREEN);
        pivotPaint1 = new Paint();
        pivotPaint1.setColor(Color.BLACK);
        pivotPaint2 = new Paint();
        pivotPaint2.setColor(Color.WHITE);
        drawPaint = new Paint();
        drawPaint.setColor(Color.BLACK);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(18);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        background = null;
        drawBitmap = null;
        drawPath = new Path();
    }

    public int getOrientation() {
        return orientation;
    }
    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }
    public void SetNewImage(Bitmap image) {
        background = image.copy(Bitmap.Config.ARGB_8888, true);
        background.setHasAlpha(true);
        backgroundCanvas = new Canvas(background);
        drawBitmap = Bitmap.createBitmap(background.getWidth(), background.getHeight(), Bitmap.Config.ARGB_8888);
        drawBitmap.setHasAlpha(true);
        drawCanvas = new Canvas(drawBitmap);
        Log.d(LOG_TAG, "Image width, height = " + String.valueOf(image.getWidth()) + ", " + String.valueOf(image.getHeight()));
        Log.d(LOG_TAG, "View width, height = " + String.valueOf(getWidth()) + ", " + String.valueOf(getHeight()));
        upperJawBox = new Rect(0, 0, image.getWidth(), image.getHeight() / 2);
        upperJawPivotPoint = new Point(upperJawBox.left, upperJawBox.bottom);
        lowerJawBox = new Rect(0, image.getHeight() / 2, image.getWidth(), image.getHeight());
        lowerJawPivotPoint = new Point(lowerJawBox.left, lowerJawBox.top);
    }
    public Bitmap getUpperJaw(){
        Bitmap bmOverlay = Bitmap.createBitmap(background.getWidth(), background.getHeight(), background.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(background, new Matrix(), null);
        canvas.drawBitmap(drawBitmap, 0, 0, null);

        Bitmap upperJaw = null;
        int left = upperJawBox.left, top = upperJawBox.top;
           int width = upperJawBox.width(), height = upperJawBox.height();
        if (left < 0) {
            width = width + left;
            left = 0;
        }
        if (top < 0) {
            height = height + top;
            top = 0;
        }

        if (width + left > background.getWidth()) width = background.getWidth() - left;
        if (height + top > background.getHeight()) height = background.getHeight() - top;
        Log.d(LOG_TAG, "left " + String.valueOf(left) + " top " + String.valueOf(top) + " right " + String.valueOf(height) + " height " + String.valueOf(height));
        Log.d(LOG_TAG, "Actual " + " right " + String.valueOf(background.getWidth()) + " height " + String.valueOf(background.getHeight()));
        upperJaw = Bitmap.createBitmap(bmOverlay, left, top, width, height);
        return upperJaw.copy(Bitmap.Config.ARGB_8888, true);
    }
    public Bitmap getLowerJaw(){
        Bitmap bmOverlay = Bitmap.createBitmap(background.getWidth(), background.getHeight(), background.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(background, new Matrix(), null);
        canvas.drawBitmap(drawBitmap, 0, 0, null);
        Bitmap lowerJaw = null;
        int left = lowerJawBox.left, top = lowerJawBox.top,
                width = lowerJawBox.width(), height = lowerJawBox.height();
        if (left < 0) {
            width = width + left;
            left = 0;
        }
        if (top < 0) {
            height = height + top;
            top = 0;
        }
        if (width + left > background.getWidth()) width = background.getWidth() - left;
        if (height + top > background.getHeight()) height = background.getHeight() - top;
        lowerJaw = Bitmap.createBitmap(bmOverlay, left, top, width, height);
        return lowerJaw.copy(Bitmap.Config.ARGB_8888, true);
    }
    public void setShowUpperJawBox(boolean showUpperJawBox){
        this.showUpperJawBox = showUpperJawBox;
        invalidate();
    }
    public void setShowLowerJawBox(boolean showLowerJawBox){
        this.showLowerJawBox = showLowerJawBox;
        invalidate();
    }

    public void setMagicEraseMode(){
        isMagicErase = true;
        isEraseMode = false;
        invalidate();
    }
    public void cancelMagicEraseMode(){
        isMagicErase = false;
        invalidate();
    }
    public boolean isMagicErase(){
        return isMagicErase;
    }
    public void setMagicEraseThreshold(int threshold){
        magicEraseThreshold = threshold;
    }

    public void setIsDrawMode(boolean isDrawMode) {
        if (isDrawMode) {
            this.isDrawMode = true;
            showLowerJawBox = false;
            showUpperJawBox = false;
            cancelBackgroundErase();
            cancelMagicEraseMode();
            invalidate();
        }
        else {
            this.isDrawMode = false;
            cancelBackgroundErase();
            cancelMagicEraseMode();
            invalidate();
        }
    }
    public boolean isDrawMode() {
        return isDrawMode;
    }

    public void setBackgroundErase(){
        isBackgroundEraseMode = true;
        drawPaint.setColor(Color.TRANSPARENT);
        drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

    }
    public void cancelBackgroundErase(){
        isBackgroundEraseMode = false;
        drawPaint.setXfermode(null);
    }

    public void setColor(int color){
        drawPaint.setColor(color);
    }
    public void setEraseMode(boolean erase){
        if (erase) {
            setIsDrawMode(true);
            isEraseMode = true;
            drawPaint.setColor(Color.TRANSPARENT);
            drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
        else {
            isEraseMode = false;
            drawPaint.setXfermode(null);
        }
    }
    public boolean isEraseMode(){return isEraseMode;}
    public void setStrokeWidth(float width){
        drawPaint.setStrokeWidth(width);
    }

    public void flipHorz(){
        Matrix m = new Matrix();
        m.preScale(-1, 1);
        Bitmap dst = Bitmap.createBitmap(background, 0, 0, background.getWidth(), background.getHeight(), m, false);
        background = dst;
        backgroundCanvas = new Canvas(background);
        invalidate();
        //dst.setDensity(DisplayMetrics.DENSITY_DEFAULT);
    }

    public Point getUpperJawPivotPoint(){ // (0, h) is bottom left of box
        if (pivotsSnapped){
            upperJawPivotPoint = new Point(lowerJawPivotPoint.x, lowerJawPivotPoint.y);
        }
        Log.d(LOG_TAG, "Upper jaw pivot x = " + String.valueOf(upperJawPivotPoint.x - upperJawBox.left));
        return new Point(upperJawPivotPoint.x - upperJawBox.left, upperJawBox.height());
    }
    public Point getLowerJawPivotPoint() { // (0, 0) is top left of box
        Log.d(LOG_TAG, "Lower jaw pivot x = " + String.valueOf(lowerJawPivotPoint.x - lowerJawBox.left));
        return new Point(lowerJawPivotPoint.x - lowerJawBox.left, 0);
    }

    public void loadPuppet(PuppetData data){
        int height, width;
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        Bitmap upperJawBitmap = BitmapFactory.decodeFile(data.getUpperJawBitmapPath(), bmOptions);
        Bitmap lowerJawBitmap = BitmapFactory.decodeFile(data.getLowerJawBitmapPath(), bmOptions);
        width = lowerJawBitmap.getWidth() + data.getLowerLeftPadding() + data.getLowerRightPadding();
        height = upperJawBitmap.getHeight() + lowerJawBitmap.getHeight();
        Bitmap overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        canvas.drawBitmap(upperJawBitmap, data.getUpperLeftPadding(), 0, null);
        canvas.drawBitmap(lowerJawBitmap, data.getLowerLeftPadding(), upperJawBitmap.getHeight(), null);
        SetNewImage(overlay);
        invalidate();
    }
    public void loadPuppet(Puppet puppet){
        int height, width;
        Bitmap upperJawBitmap = puppet.getUpperJawBitmap();
        Bitmap lowerJawBitmap = puppet.getLowerJawBitmap();
        width = lowerJawBitmap.getWidth() + puppet.getLowerLeftPadding() + puppet.getLowerRightPadding();
        height = upperJawBitmap.getHeight() + lowerJawBitmap.getHeight();
        Bitmap overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        canvas.drawBitmap(upperJawBitmap, puppet.getUpperLeftPadding(), 0, null);
        canvas.drawBitmap(lowerJawBitmap, puppet.getLowerLeftPadding(), upperJawBitmap.getHeight(), null);
        SetNewImage(overlay);
        upperJawBox = new Rect(puppet.getUpperLeftPadding(), 0, puppet.getUpperLeftPadding() + upperJawBitmap.getWidth(), upperJawBitmap.getHeight());
        lowerJawBox = new Rect(puppet.getLowerLeftPadding(), upperJawBitmap.getHeight(), puppet.getLowerLeftPadding() + lowerJawBitmap.getWidth(), overlay.getHeight());
        lowerJawPivotPoint = new Point(puppet.getLowerLeftPadding() + puppet.getLowerPivotPoint().x, upperJawBitmap.getHeight());
        upperJawPivotPoint = lowerJawPivotPoint;
        orientation = puppet.getOrientation();
        invalidate();
    }
    // Touch related methods
    @Override
    public boolean onTouchEvent(MotionEvent event){
        super.onTouchEvent(event);
        float x = event.getX(), y = event.getY();
        if(isMagicErase){
            switch (event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    magicErase(x, y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    magicErase(x, y);
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
                default:
                    return true;
            }
        }
        else if(isBackgroundEraseMode){
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    prevX = x;
                    prevY = y;
                    drawPath.moveTo(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    drawPath.lineTo(x, y);
                    backgroundCanvas.drawPath(drawPath, drawPaint);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    backgroundCanvas.drawPath(drawPath, drawPaint);
                    drawPath.reset();
                    selectionId = NO_SELECTION;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
        else if (isDrawMode){
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    prevX = x;
                    prevY = y;
                    drawPath.moveTo(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (prevX <= 0 || prevY <= 0){
                        prevX = x;
                        prevY = y;
                        drawPath.moveTo(x, y);
                    }
                    else {
                        drawPath.lineTo(x, y);
                        drawCanvas.drawPath(drawPath, drawPaint);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (prevX <= 0 || prevY <= 0){
                        prevX = x;
                        prevY = y;
                        drawPath.moveTo(x, y);
                    }
                    else {
                        drawCanvas.drawPath(drawPath, drawPaint);
                        drawPath.reset();
                        selectionId = NO_SELECTION;
                    }
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
        else {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    prevX = x;
                    prevY = y;
                    setSelectionId(x, y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (selectionId == NO_SELECTION) {
                        prevX = x;
                        prevY = y;
                        setSelectionId(x, y);
                    } else
                        moveSelection((int)prevX, (int)prevY, (int)event.getX(), (int)event.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                    checkForSnap();
                    selectionId = NO_SELECTION;
                    return true;
                default:
                    return true;
            }
        }
    }
    private void checkForSnap(){
        if (Math.abs(lowerJawPivotPoint.x - upperJawPivotPoint.x) < edgeThresh && Math.abs(lowerJawPivotPoint.y - upperJawPivotPoint.y) < edgeThresh ) {
            if (selectionId < LOWER_JAW) {
                lowerJawPivotPoint.set(upperJawPivotPoint.x, upperJawPivotPoint.y);
                upperJawPivotPoint = lowerJawPivotPoint;
                lowerJawBox.top = upperJawBox.bottom;
            } else {
                upperJawPivotPoint = lowerJawPivotPoint;
                upperJawBox.bottom = lowerJawBox.top;
            }
            pivotsSnapped = true;
        }
    }
    private void setSelectionId(float x, float y){
            if (upperJawBox.contains((int) x, (int) y)) {
                selectionId = UPPER_JAW;
                if (Math.abs(y - upperJawBox.top) < edgeThresh) selectionId = UPPER_JAW_TOP;
                if (Math.abs(y - upperJawBox.bottom) < edgeThresh) selectionId = UPPER_JAW_BOTTOM;
                if (Math.abs(x - upperJawBox.left) < edgeThresh) selectionId = UPPER_JAW_LEFT;
                if (Math.abs(x - upperJawBox.right) < edgeThresh) selectionId = UPPER_JAW_RIGHT;
            }
            if (Math.abs(x - upperJawPivotPoint.x) < pointThresh && Math.abs(y - upperJawPivotPoint.y) < pointThresh)
                selectionId = UPPER_JAW_PIVOT;
            if (lowerJawBox.contains((int) x, (int) y)) {
                selectionId = LOWER_JAW;
                if (Math.abs(y - lowerJawBox.top) < edgeThresh) selectionId = LOWER_JAW_TOP;
                if (Math.abs(y - lowerJawBox.bottom) < edgeThresh) selectionId = LOWER_JAW_BOTTOM;
                if (Math.abs(x - lowerJawBox.left) < edgeThresh) selectionId = LOWER_JAW_LEFT;
                if (Math.abs(x - lowerJawBox.right) < edgeThresh) selectionId = LOWER_JAW_RIGHT;
            }
            if (Math.abs(x - lowerJawPivotPoint.x) < pointThresh && Math.abs(y - lowerJawPivotPoint.y) < pointThresh)
                selectionId = LOWER_JAW_PIVOT;
    }
    private void moveSelection(int x1, int y1, int x2, int y2){
        int dx = x2 - x1, dy = y2 - y1;
        switch (selectionId){
            case NO_SELECTION:
                break;

            // Upper Jaw cases
            case UPPER_JAW:
                upperJawBox.offset(dx, dy);
                upperJawPivotPoint.offset(dx, dy);
                if (pivotsSnapped) {
                    lowerJawBox.offset(dx, dy);
                    //lowerJawPivotPoint.offset(dx, dy);
                }
                break;
            case UPPER_JAW_TOP:
                upperJawBox.top = y2;
                break;
            case UPPER_JAW_BOTTOM:
                upperJawBox.bottom = y2;
                upperJawPivotPoint.y = y2;
                if (pivotsSnapped){
                    lowerJawBox.top = upperJawBox.bottom;
                    //lowerJawPivotPoint.y = upperJawPivotPoint.y;
                }
                break;
            case UPPER_JAW_LEFT:
                upperJawBox.left = x2;
                if (upperJawBox.left > upperJawPivotPoint.x) {
                    upperJawPivotPoint.x = x2;
                    if (pivotsSnapped) lowerJawPivotPoint.x = x2;
                }
                break;
            case UPPER_JAW_RIGHT:
                upperJawBox.right = x2;
                break;
            case UPPER_JAW_PIVOT:
                upperJawPivotPoint.offset(x2 - x1, 0);
                /*if (pivotsSnapped){
                    lowerJawPivotPoint.offset(x2- x1, 0);
                }*/
                break;

            // Lower Jaw cases
            case LOWER_JAW:
                lowerJawBox.offset(x2 - x1, y2 - y1);
                lowerJawPivotPoint.offset(x2 - x1, y2 - y1);
                if (pivotsSnapped) {
                    upperJawBox.offset(dx, dy);
                    //upperJawPivotPoint.offset(dx, dy);
                }
                break;
            case LOWER_JAW_TOP:
                lowerJawBox.top = y2;
                lowerJawPivotPoint.y = y2;
                if (pivotsSnapped){
                    upperJawBox.bottom = lowerJawBox.top;
                    //upperJawPivotPoint.y = lowerJawPivotPoint.y;
                }
                break;
            case LOWER_JAW_BOTTOM:
                lowerJawBox.bottom = y2;
                break;
            case LOWER_JAW_LEFT:
                lowerJawBox.left = x2;
                if (lowerJawBox.left > lowerJawPivotPoint.x) lowerJawPivotPoint.x = x2;
                break;
            case LOWER_JAW_RIGHT:
                lowerJawBox.right = x2;
                break;
            case LOWER_JAW_PIVOT:
                lowerJawPivotPoint.offset(dx, 0);
                /*
                if (pivotsSnapped){
                    upperJawPivotPoint.offset(dx, 0);
                }*/
        }
        prevX = x2; prevY = y2;
        if (lowerJawBox.width() < MIN_BOX_SIZE) {
            if (selectionId == LOWER_JAW_RIGHT) lowerJawBox.right = lowerJawBox.left + MIN_BOX_SIZE;
            else lowerJawBox.left = lowerJawBox.right - MIN_BOX_SIZE;
        }
        if (lowerJawBox.height() < MIN_BOX_SIZE) {
            if (selectionId == LOWER_JAW_TOP)  lowerJawBox.top = lowerJawBox.bottom - MIN_BOX_SIZE;
            else lowerJawBox.bottom = lowerJawBox.top + MIN_BOX_SIZE;

        }
        if (upperJawBox.width() < MIN_BOX_SIZE) {
            if (selectionId == UPPER_JAW_RIGHT) upperJawBox.right = upperJawBox.left + MIN_BOX_SIZE;
            else upperJawBox.left = upperJawBox.right - MIN_BOX_SIZE;
        }
        if (upperJawBox.height() < MIN_BOX_SIZE) {
            if (selectionId == UPPER_JAW_TOP)  upperJawBox.top = upperJawBox.bottom - MIN_BOX_SIZE;
            else upperJawBox.bottom = upperJawBox.top + MIN_BOX_SIZE;

        }
        invalidate();
    }
    private void magicErase(float x, float y){
        boolean keepSearchingy = true;
        boolean keepSearchingx = true;
        int rgbInit = background.getPixel((int)x, (int)y), rgbCurrent;
        int r0, g0, b0, r1, g1, b1;
        r0 = (rgbInit >> 16) & 0xFF;
        g0 = (rgbInit >> 8) & 0xFF;
        b0 = rgbInit & 0xFF;
        Log.d(LOG_TAG, "r0=" + String.valueOf(r0) + " g0=" + String.valueOf(g0) + " b0=" + String.valueOf(b0));
        int xN, yN;
        for (xN = (int)x; xN < background.getWidth() && keepSearchingx; xN++){
            for (yN = (int)y; yN < background.getHeight() && keepSearchingy; yN++){
                rgbCurrent = background.getPixel(xN, yN);
                r1 = (rgbCurrent >> 16) & 0xFF;
                g1 = (rgbCurrent >> 8) & 0xFF;
                b1 = rgbCurrent & 0xFF;
                if (Math.sqrt((r1-r0)*(r1-r0) + (g1-g0)*(g1-g0) + (b1-b0)*(b1-b0)) < magicEraseThreshold){
                    background.setPixel(xN, yN, 0x00000000);
                }
                else {
                    keepSearchingy = false;
                    if (yN == y) keepSearchingx = false;
                }
            }
            keepSearchingy = true;
            for (yN = (int)y - 1; yN >= 0 && keepSearchingy; yN--){
                rgbCurrent = background.getPixel(xN, yN);
                r1 = (rgbCurrent >> 16) & 0xFF;
                g1 = (rgbCurrent >> 8) & 0xFF;
                b1 = rgbCurrent & 0xFF;
                if (Math.sqrt((r1-r0)*(r1-r0) + (g1-g0)*(g1-g0) + (b1-b0)*(b1-b0)) < magicEraseThreshold){
                    background.setPixel(xN, yN, 0x00000000);
                }
                else {
                    keepSearchingy = false;
                    if (yN == y - 1) keepSearchingx = false;
                }
            }
            keepSearchingy = true;
            invalidate();
        }
        keepSearchingy = true;
        keepSearchingx = true;
        for (xN = (int)x - 1; xN >= 0 && keepSearchingx; xN--){
            for (yN = (int)y; yN < background.getHeight() && keepSearchingy; yN++){
                rgbCurrent = background.getPixel(xN, yN);
                r1 = (rgbCurrent >> 16) & 0xFF;
                g1 = (rgbCurrent >> 8) & 0xFF;
                b1 = rgbCurrent & 0xFF;
                if (Math.sqrt((r1-r0)*(r1-r0) + (g1-g0)*(g1-g0) + (b1-b0)*(b1-b0)) < magicEraseThreshold){
                    background.setPixel(xN, yN, 0x00000000);
                }
                else {
                    keepSearchingy = false;
                    if (yN == y) keepSearchingx = false;
                }
            }
            keepSearchingy = true;
            for (yN = (int)y - 1; yN >= 0 && keepSearchingy; yN--){
                rgbCurrent = background.getPixel(xN, yN);
                r1 = (rgbCurrent >> 16) & 0xFF;
                g1 = (rgbCurrent >> 8) & 0xFF;
                b1 = rgbCurrent & 0xFF;
                if (Math.sqrt((r1-r0)*(r1-r0) + (g1-g0)*(g1-g0) + (b1-b0)*(b1-b0)) < magicEraseThreshold){
                    background.setPixel(xN, yN, 0x00000000);
                }
                else {
                    keepSearchingy = false;
                    if (yN == y - 1) keepSearchingx = false;
                }
            }
            keepSearchingy = true;
            invalidate();
        }

    }

    @Override
    protected void onDraw(Canvas canvas){
        if (background != null) {
            canvas.drawBitmap(background, 0, 0, null);
            canvas.drawBitmap(drawBitmap, 0, 0, null);
            canvas.drawPath(drawPath, drawPaint);
            if (showLowerJawBox) {
                canvas.drawRect(lowerJawBox, lowerJawPaint);
                canvas.drawCircle(lowerJawPivotPoint.x, lowerJawPivotPoint.y, 16, pivotPaint1);
                canvas.drawCircle(lowerJawPivotPoint.x, lowerJawPivotPoint.y, 12, pivotPaint2);
                canvas.drawCircle(lowerJawPivotPoint.x, lowerJawPivotPoint.y, 8, pivotPaint1);
            }
            if (showUpperJawBox) {
                canvas.drawRect(upperJawBox, upperJawPaint);
                canvas.drawCircle(upperJawPivotPoint.x, upperJawPivotPoint.y, 16, pivotPaint1);
                canvas.drawCircle(upperJawPivotPoint.x, upperJawPivotPoint.y, 12, pivotPaint2);
                canvas.drawCircle(upperJawPivotPoint.x, upperJawPivotPoint.y, 8, pivotPaint1);
            }
        }
    }
}
