package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Nathan on 6/9/2015.
 */
public class PuppetDesigner extends View {

    private static  final String LOG_TAG = "PuppetDesigner";

    public static final int SMALL_WIDTH = 512;
    public static final int SMALL_HEIGHT = 768;

    // Selection ids
    private static final int NO_SELECTION = 0;
    private static final int UPPER_JAW = 1000;
    private static final int UPPER_JAW_TOP = 1001;
    private static final int UPPER_JAW_BOTTOM = 1002;
    private static final int UPPER_JAW_LEFT = 1003;
    private static final int UPPER_JAW_RIGHT = 1004;
    private static final int UPPER_JAW_PIVOT = 1005;
    private static final int ROTATE_HANDLE = 1006;
    private static final int LOWER_JAW = 2000;
    private static final int LOWER_JAW_TOP = 2001;
    private static final int LOWER_JAW_BOTTOM = 2002;
    private static final int LOWER_JAW_LEFT = 2003;
    private static final int LOWER_JAW_RIGHT = 2004;
    private static final int LOWER_JAW_PIVOT = 2005;
    private static final int MIN_BOX_SIZE = 80;

    // Mode strings
    public static final String MODE_SELECT = "select";
    public static final String MODE_DRAW = "draw";
    public static final String MODE_DRAW_ERASE = "erase";
    public static final String MODE_BACKGROUND_ERASE = "bg_erase";
    public static final String MODE_MAGIC_ERASE = "magic_erase";
    public static final String MODE_CUT_PATH = "cut_path";
    public static final String MODE_HEAL = "heal";
    public static final String MODE_PAN_ZOOM = "pan_zoom";
    public String designerMode = MODE_PAN_ZOOM;

    // Undo id strings
    private static final String UNDO_BACKGROUND = "background";
    private static final String UNDO_DRAW = "draw";

    private Context context;
    private Bitmap backgroundBitmap, backgroundOriginal, drawBitmap;
    private File cacheDir;
    private ArrayList<Bitmap> backgroundUndoStack;
    private ArrayList<Bitmap> drawUndoStack;
    private ArrayList<String> undoStack;
    private RectF upperJawBox, upperBoxDrawRect, lowerJawBox, lowerBoxDrawRect;
    private Bitmap upperProfileBitmap, lowerProfileBitmap;
    private Point upperJawPivotPoint, lowerJawPivotPoint;
    private float[] upperPivotDrawPoint;
    private Paint upperJawPaint, lowerJawPaint, pivotPaint1, pivotPaint2, drawPaint;
    private int drawColor = Color.BLACK;
    private Path drawPath;
    private Canvas drawCanvas, backgroundCanvas;
    private int edgeThresh = 30, pointThresh = 30;
    private double colorSimilarity = 75;
    private Path cutPath;
    private float cutPathStrokeWidth = 75;
    private double colorSimilaritySensitivity = 1.0;
    private Paint cutPathPaint;
    private ArrayList<Point> cutPathPoints;
    private int selectionId = 0;
    private float prevX = -1, prevY = -1;
    private boolean isCutPath = false, showUpperJawBox = false, showLowerJawBox = false,
            pivotsSnapped = true, isDrawMode = false, isEraseMode = false, isSaved = true;
    private float rotation = 0;
    private float prevAngle = 0;
    private float prevRotation = 0;
    private Point rotateHandle;
    private float[] rotateHandleDrawPoint;
    private float rotateHandleLengthScale = 0.30859375f;
    private int orientation = Puppet.PROFILE_RIGHT;
    private float x1Start, x2Start, y1Start, y2Start, zoomFactor;
    private Point zoomPoint;
    private Matrix zoomMatrix;
    private ScaleGestureDetector scaleGestureDetector;

    public PuppetDesigner(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        // Setup initial paint properties
        lowerJawPaint = new Paint();
        lowerJawPaint.setARGB(64, 0, 128, 0);
        upperJawPaint = new Paint();
        upperJawPaint.setARGB(64, 128, 0, 0);
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

        // Initialize rotation handle
        rotateHandle = new Point();

        // Setup blank drawing canvas
        drawBitmap = Bitmap.createBitmap(SMALL_WIDTH, SMALL_HEIGHT, Bitmap.Config.ARGB_8888);
        drawBitmap.setHasAlpha(true);
        drawCanvas = new Canvas(drawBitmap);
        drawPath = new Path();

        // Initialize zoom
        zoomFactor = 1f;
        zoomMatrix = new Matrix();
        zoomMatrix.setScale(1f, 1f, drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomPoint = new Point(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomMatrix.postTranslate((getWidth() - drawBitmap.getWidth()) / 2, (getHeight() - drawBitmap.getHeight()) / 2);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        // Set initial positions of jaw boxes
        upperJawBox = new RectF(40, 40, drawBitmap.getWidth() - 40, drawBitmap.getHeight() / 2);
        upperBoxDrawRect = new RectF(upperJawBox);
        lowerJawBox = new RectF(40, drawBitmap.getHeight() / 2, drawBitmap.getWidth() - 40, drawBitmap.getHeight() - 40);
        lowerBoxDrawRect = new RectF(lowerJawBox);
        upperJawPivotPoint= new Point(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        lowerJawPivotPoint = upperJawPivotPoint;
        upperPivotDrawPoint = new float[]{upperJawPivotPoint.x, upperJawPivotPoint.y};
        rotateHandle = new Point(upperJawPivotPoint.x + 150, upperJawPivotPoint.y);
        rotateHandleDrawPoint = new float[]{rotateHandle.x, rotateHandle.y};


        // Prepare cache directory for access
        if (Utils.isExternalStorageWritable()){
            cacheDir = context.getExternalCacheDir();
        }
        else {
            cacheDir = context.getCacheDir();
        }

        // Initialize undo stacks
        backgroundUndoStack = new ArrayList<>(5);
        drawUndoStack = new ArrayList<>();
        undoStack = new ArrayList<>();
    }
    public void release(){
        if (backgroundOriginal != null) backgroundOriginal.recycle();
        if (backgroundBitmap != null) backgroundBitmap.recycle();
        if (backgroundUndoStack != null) {
            for (Bitmap b : backgroundUndoStack)
                b.recycle();
        }
        if (drawUndoStack != null) {
            for (Bitmap b : drawUndoStack)
                b.recycle();
        }
    }
    public void saveData(DesignerActivityDataFrag data){
        data.undoStack = undoStack;
        data.backgroundBitmap = backgroundBitmap;
        data.backgroundOriginal = backgroundOriginal;
        data.backgroundUndoStack = backgroundUndoStack;
        data.drawBitmap = drawBitmap;
        data.drawColor = drawColor;
        data.drawUndoStack = drawUndoStack;
        data.lowerJawBox = lowerJawBox;
        data.upperJawBox = upperJawBox;
        data.upperJawPivotPoint = upperJawPivotPoint;
        data.lowerJawPivotPoint = lowerJawPivotPoint;
        data.orientation = orientation;
        data.rotation = rotation;
        data.zoomMatrix = zoomMatrix;
        data.zoomPoint = zoomPoint;
        data.zoomFactor = zoomFactor;
        data.isSaved = isSaved;
    }
    public void loadData(DesignerActivityDataFrag data){
        undoStack = data.undoStack;
        backgroundBitmap = data.backgroundBitmap;
        backgroundCanvas = new Canvas(backgroundBitmap);
        backgroundOriginal = data.backgroundOriginal;
        backgroundUndoStack = data.backgroundUndoStack;
        drawBitmap = data.drawBitmap;
        drawCanvas = new Canvas(drawBitmap);
        drawColor = data.drawColor;
        drawUndoStack = data.drawUndoStack;
        lowerJawBox = data.lowerJawBox;
        upperJawBox = data.upperJawBox;
        upperJawPivotPoint = data.upperJawPivotPoint;
        lowerJawPivotPoint = data.lowerJawPivotPoint;
        orientation = data.orientation;
        rotation = data.rotation;
        zoomMatrix = data.zoomMatrix;
        zoomPoint = data.zoomPoint;
        zoomFactor = data.zoomFactor;
        isSaved = data.isSaved;
    }

    public String getMode(){
        return designerMode;
    }
    public void setMode(String mode){
        designerMode = mode;
        switch (mode){
            case MODE_SELECT:
                setSelectionMode();
                break;
            case MODE_PAN_ZOOM:
                showLowerJawBox = false;
                showUpperJawBox = false;
                setPanZoomMode();
                invalidate();
        }
    }
    public boolean isSaved(){
        return isSaved;
    }

    public int getOrientation() {
        return orientation;
    }
    public void setOrientation(int orientation) {
        if (orientation != this.orientation){
            this.orientation = orientation;
            Matrix m = new Matrix();
            m.setScale(-1, 1);
            upperProfileBitmap = Bitmap.createBitmap(upperProfileBitmap, 0, 0, upperProfileBitmap.getWidth(), upperProfileBitmap.getHeight(), m, false);
            lowerProfileBitmap = Bitmap.createBitmap(lowerProfileBitmap, 0, 0, lowerProfileBitmap.getWidth(), lowerProfileBitmap.getHeight(), m, false);
            resetJawBoxes();
            /*float[] point = {rotateHandle.x, rotateHandle.y};
            m.setScale(-1, 1, upperJawPivotPoint.x, upperJawPivotPoint.y / 2);
            m.mapPoints(point);
            rotateHandle.x = (int)point[0];
            rotateHandle.y = (int)point[1];
            point = new float[]{upperJawPivotPoint.x, upperJawPivotPoint.y};
            m.mapPoints(point);
            upperJawPivotPoint.x = (int)point[0];
            upperJawPivotPoint.y = (int)point[1];
            lowerJawPivotPoint = upperJawPivotPoint;
            Log.d(LOG_TAG, "rotate handle: " + rotateHandle.toString());
            Log.d(LOG_TAG, "pivot point: " + upperJawPivotPoint.toString());
            */
            invalidate();
        }
    }
    public void setShowUpperJawBox(boolean showUpperJawBox){
        this.showUpperJawBox = showUpperJawBox;
        invalidate();
    }
    public void setShowLowerJawBox(boolean showLowerJawBox){
        this.showLowerJawBox = showLowerJawBox;
        invalidate();
    }
    public void resetJawBoxes(){
        // Set initial positions of jaw boxes
        int padding = (int)(Math.min(drawBitmap.getHeight() * 0.2, drawBitmap.getWidth()) * 0.2);
        upperJawBox = new RectF(padding, padding, drawBitmap.getWidth() - padding, drawBitmap.getHeight() / 2);
        lowerJawBox = new RectF(padding, drawBitmap.getHeight() / 2, drawBitmap.getWidth() - padding, drawBitmap.getHeight() - padding);
        upperJawPivotPoint = new Point(drawBitmap.getWidth() / 2, (int)upperJawBox.bottom);
        lowerJawPivotPoint = upperJawPivotPoint;
        if (orientation == Puppet.PROFILE_RIGHT) {
            rotateHandle = new Point(upperJawPivotPoint.x + 150, upperJawPivotPoint.y);
        } else {
            rotateHandle = new Point(upperJawPivotPoint.x - 150, upperJawPivotPoint.y);
        }
        rotation = 0;
    }
    public float getRotation(){
        return rotation;
    }

    // Undo methods
    private void addBackgroundUndo(){
        if (backgroundBitmap != null) {
            undoStack.add(UNDO_BACKGROUND);
            backgroundUndoStack.add(backgroundBitmap.copy(backgroundBitmap.getConfig(), true));
            Runtime runtime = Runtime.getRuntime();
            if (3 * backgroundBitmap.getByteCount() + runtime.totalMemory() >= runtime.maxMemory()) {
                backgroundUndoStack.get(0).recycle();
                backgroundUndoStack.remove(0);
                Log.i(LOG_TAG, "Memory low, removing undo level");
            }
        }
    }
    private void addDrawUndo(){
        undoStack.add(UNDO_DRAW);
        drawUndoStack.add(drawBitmap.copy(drawBitmap.getConfig(), true));
        Runtime runtime = Runtime.getRuntime();
        if (3 * drawBitmap.getByteCount() + runtime.totalMemory() >= runtime.maxMemory()) {
            drawUndoStack.remove(0);
            Log.i(LOG_TAG, "Memory low, removing undo level");
        }
    }
    public boolean Undo(){
        if (undoStack.size() > 0) {
            String type = undoStack.get(undoStack.size() - 1);
            undoStack.remove(undoStack.size() - 1);
            switch (type) {
                case UNDO_BACKGROUND:
                    if (backgroundUndoStack.size() > 0) {
                        backgroundBitmap = backgroundUndoStack.get(backgroundUndoStack.size() - 1);
                        backgroundCanvas = new Canvas(backgroundBitmap);
                        backgroundUndoStack.remove(backgroundUndoStack.size() - 1);
                    }
                    break;
                case UNDO_DRAW:
                    if (drawUndoStack.size() > 0) {
                        drawBitmap = drawUndoStack.get(drawUndoStack.size() - 1);
                        drawUndoStack.remove(drawUndoStack.size() - 1);
                        drawCanvas = new Canvas(drawBitmap);
                    }
                    break;
            }
            invalidate();
            return true;
        }
        else
            return false;
    }
    public boolean canUndo(){
        return undoStack.size() > 0;
    }

    // Heal methods
    public void setHealMode(boolean isHealMode){
        designerMode = MODE_HEAL;
    }
    private void Heal(int x, int y){
        if (backgroundBitmap != null && backgroundOriginal != null) {
            x = Utils.getInBounds(x, 0, backgroundBitmap.getWidth() - 1);
            y = Utils.getInBounds(y, 0, backgroundBitmap.getHeight() - 1);
            int r = (int) drawPaint.getStrokeWidth() / 2;
            for (int xN = x - r; x >= 0 && x < backgroundOriginal.getWidth() && xN <= x + r; xN++) {
                for (int yN = y - r; y >= 0 && y < backgroundOriginal.getHeight() && yN <= y + r; yN++) {
                    if (xN >= 0 && xN < backgroundOriginal.getWidth() &&
                            yN >= 0 && yN < backgroundOriginal.getHeight() &&
                            Math.pow(xN - x, 2) + Math.pow(yN - y, 2) <= r * r) {
                        int pixel = backgroundOriginal.getPixel(xN, yN);
                        backgroundBitmap.setPixel(xN, yN, pixel);
                    }
                }
            }
            isSaved = false;
            invalidate();

        }
    }
    private boolean handleHealTouch(MotionEvent event){
        float [] point = {event.getX(), event.getY()};
        Matrix unZoom = new Matrix(zoomMatrix);
        unZoom.invert(unZoom);
        unZoom.mapPoints(point);
        int x = (int)point[0], y = (int)point[1];
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                addBackgroundUndo();
                Heal(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                Heal(x, y);
                return true;
            default:
                return true;
        }
    }

    // Cut path methods
    public void setCutPathMode(){
        designerMode = MODE_CUT_PATH;
        startCutPathFlow();
        invalidate();
    }
    private void startCutPathFlow(){
        cutPath = new Path();
        cutPathPoints = new ArrayList<>(100);
        cutPathPaint = new Paint();
        cutPathPaint.setColor(Color.parseColor("#66FF0000"));
        cutPathPaint.setStrokeWidth(cutPathStrokeWidth);
        cutPathPaint.setAntiAlias(true);
        cutPathPaint.setStyle(Paint.Style.STROKE);
        cutPathPaint.setStrokeJoin(Paint.Join.ROUND);
        cutPathPaint.setStrokeCap(Paint.Cap.ROUND);
    }
    private boolean handleCutPathTouch(MotionEvent event){
        if (backgroundBitmap != null) {
            float [] point = {event.getX(), event.getY()};
            Matrix unZoom = new Matrix(zoomMatrix);
            unZoom.invert(unZoom);
            unZoom.mapPoints(point);
            float x = point[0], y = point[1];
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startCutPathFlow();
                    cutPath.moveTo(event.getX(), event.getY());
                    cutPathPoints.add(new Point((int) x, (int) y));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    cutPath.lineTo(event.getX(), event.getY());
                    cutPathPoints.add(new Point((int) x, (int) y));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    isSaved = false;
                    cutPath.lineTo(event.getX(), event.getY());
                    cutPathPoints.add(new Point((int) x, (int) y));
                    cutPath.lineTo(cutPathPoints.get(0).x, cutPathPoints.get(0).y);
                    cutPath.setLastPoint(cutPathPoints.get(0).x, cutPathPoints.get(0).y);
                    invalidate();
                    addBackgroundUndo();
                    determineCutPath((int) cutPathPaint.getStrokeWidth());
                    cutPathPaint.setColor(Color.TRANSPARENT);
                    cutPathPaint.setStrokeWidth(1);
                    cutPathPaint.setStyle(Paint.Style.FILL);
                    cutPathPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    cutPath.toggleInverseFillType();
                    backgroundCanvas.drawPath(cutPath, cutPathPaint);
                    cutPathPaint.setMaskFilter(new BlurMaskFilter(3, BlurMaskFilter.Blur.NORMAL));
                    cutPathPaint.setStrokeWidth(5);
                    cutPathPaint.setStyle(Paint.Style.STROKE);
                    cutPath.toggleInverseFillType();
                    backgroundCanvas.drawPath(cutPath, cutPathPaint);
                    cutPath.reset();
                    invalidate();  // End cut path flow
                    return true;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }
    public void cancelCutPathMode(){
        isCutPath = false;
        invalidate();
    }
    public boolean isCutPath(){
        return isCutPath;
    }
    public void setCutPathWidth(int width){
        cutPathStrokeWidth = width;
        cutPathPaint.setStrokeWidth(cutPathStrokeWidth);
    }
    private int getLeftColorSliceAvg(Bitmap bitmap, int x, int y, double tanSlope, int lengthOver10){
        int r = lengthOver10;
        double m = -1 / tanSlope;
        int stopValue = lengthOver10 - 8;
        int[] colors = new int[r - stopValue];
        int currentX, currentY;
        double a, b;
        for (int i = r; i > stopValue; i--){
            a = ((double)i) / Math.sqrt(m*m + 1);
            b = ((double)i) / Math.sqrt(1/(m*m) + 1);
            currentX = (int)Math.round(x - a);
            if (currentX < 0) currentX = 0;
            else if (currentX >= bitmap.getWidth()) currentX = bitmap.getWidth() - 1;
            currentY = (int)Math.round(y - b);
            if (currentY < 0) currentY = 0;
            else if (currentY >= bitmap.getHeight()) currentY = bitmap.getHeight() - 1;
            colors[r - i] = bitmap.getPixel(currentX, currentY);
            //backgroundBitmap.setPixel(currentX, currentY, Color.RED);
        }

        return getColorAverage(colors);
    }
    private int getRightColorSliceAvg(Bitmap bitmap, int x, int y, double tanSlope, int lengthOver10){
        int[] colors;
        double m = -1 / tanSlope;
        int r = lengthOver10;
        int stopValue = lengthOver10 - 8;
        double a, b;
        int currentX, currentY;
        colors = new int[r - stopValue];
        for (int i = r; i > stopValue; i--){
            a = ((double)i) / Math.sqrt(m*m + 1);
            b = ((double)i) / Math.sqrt(1/(m*m) + 1);
            currentX = (int)Math.round(x + a);
            if (currentX < 0) currentX = 0;
            else if (currentX >= bitmap.getWidth()) currentX = bitmap.getWidth() - 1;
            currentY = (int)Math.round(y + b);
            if (currentY < 0) currentY = 0;
            else if (currentY >= bitmap.getHeight()) currentY = bitmap.getHeight() - 1;
            colors[r - i] = bitmap.getPixel(currentX, currentY);
            //backgroundBitmap.setPixel(currentX, currentY, Color.BLUE);
        }

        return getColorAverage(colors);
    }
    private int getTopColorSliceAvg(Bitmap bitmap, int x, int y, int lengthOver10){
        int[] colors = new int[Math.abs(x - (lengthOver10 - 8))];
        int currentY;
        x = Math.max(x, 0);
        x = Math.min(x, bitmap.getWidth() - 1);
        for (int i = 0; i < colors.length; i++){
            currentY = y - (i+10);
            currentY = Math.max(currentY, 0);
            currentY = Math.min(currentY, bitmap.getHeight() - 1);
            colors[i] = bitmap.getPixel(x, currentY);
        }
        return getColorAverage(colors);
    }
    private int getBottomColorSliceAvg(Bitmap bitmap, int x, int y, int lengthOver10){
        int[] colors = new int[Math.abs(x - (lengthOver10 - 8))];
        int currentY;
        x = Math.max(x, 0);
        x = Math.min(x, bitmap.getWidth() - 1);
        for (int i = 0; i < colors.length; i++){
            currentY = y + (i+10);
            currentY = Math.max(currentY, 0);
            currentY = Math.min(currentY, bitmap.getHeight() - 1);
            colors[i] = bitmap.getPixel(x, currentY);
        }
        return getColorAverage(colors);
    }
    private void determineCutPath(int strokeWidth){
        cutPath.reset();
        double m;
        Point cutPoint, prev, current, next;
        for (int i = 1; i < cutPathPoints.size() - 1; i++){
            prev = cutPathPoints.get(i - 1);
            if (i == 1) // Assume the first point in the path is good
                cutPath.moveTo(prev.x, prev.y);
            current = cutPathPoints.get(i);
            next = cutPathPoints.get(i + 1);
            if (current.x != prev.x && current.y != prev.y) {
                double distance = Math.sqrt(Math.pow(current.x - prev.x, 2) + Math.pow(current.y - prev.y, 2));
                if (current.x == prev.x) {
                    cutPoint = determineCutPointVert(current.x, current.y, strokeWidth);
                } else if (distance > 5){
                    m = ((double)(current.y - prev.y)) / (current.x - prev.x);
                    m += ((double)(next.y - prev.y)) / (next.x - prev.x);
                    m += ((double)(next.y - current.y)) / (next.x - current.x);
                    m /= 3;
                    /*int xN, yN, x1 = prev.x, y1 = prev.y, a, b;
                    for (int d = 0; d < distance; d++){
                        a = (int)Math.copySign(Math.round((double) d / Math.sqrt(m * m + 1)), y1 - current.y);
                        b = (int)Math.copySign(Math.round((double) d / Math.sqrt(1 / (m * m) + 1)), x1 - current.x);
                        xN = Math.min(x1 + a, backgroundBitmap.getWidth() - 1);
                        xN = Math.max(xN, 0);
                        yN = Math.min(y1 + b, backgroundBitmap.getHeight() - 1);
                        yN = Math.max(yN, 0);
                        cutPoint = determineCutPoint(xN, yN, m, strokeWidth);
                        cutPath.lineTo(cutPoint.x, cutPoint.y);
                    }*/
                    cutPoint = determineCutPoint(current.x, current.y, m, strokeWidth);
                    cutPath.lineTo(cutPoint.x, cutPoint.y);
                }
                else {
                    m = ((double)(current.y - prev.y)) / (current.x - prev.x);
                    m += ((double)(next.y - prev.y)) / (next.x - prev.x);
                    m += ((double)(next.y - current.y)) / (next.x - current.x);
                    m /= 3;
                    cutPoint = determineCutPoint(current.x, current.y, m, strokeWidth);
                }
                cutPath.lineTo(cutPoint.x, cutPoint.y);
            }
        }
    }
    private Point determineCutPoint(int x, int y, double tanSlope, int strokeWidth){
        // slope is negative means inside is left
        // slope is positive means inside is right
        y = Math.max(0, y);
        y = Math.min(y, backgroundBitmap.getHeight() - 1);
        x = Math.max(0, x);
        x = Math.min(x, backgroundBitmap.getWidth() - 1);
        int r = strokeWidth / 2;
        int leftAvg;
        double leftColorThreshold;
        int rightAvg;
        double rightColorThreshold;
        // Determine color average left of the point
        if (tanSlope < 0.1) {
            leftAvg = getLeftColorSliceAvg(backgroundBitmap, x, y, tanSlope, r);
            leftColorThreshold = colorSimilaritySensitivity * colorSimilarity;
            rightAvg = getRightColorSliceAvg(backgroundBitmap, x, y, tanSlope, r);
            rightColorThreshold = colorSimilaritySensitivity * colorSimilarity;
        }
        else if (tanSlope > 0.1){ // Positive slope -> left right are switched
            rightAvg = getLeftColorSliceAvg(backgroundBitmap, x, y, tanSlope, r);
            leftColorThreshold = colorSimilaritySensitivity * colorSimilarity;
            leftAvg = getRightColorSliceAvg(backgroundBitmap, x, y, tanSlope, r);
            rightColorThreshold = colorSimilaritySensitivity * colorSimilarity;
        }
        else{ // Slope is approximately zero
            leftAvg = getBottomColorSliceAvg(backgroundBitmap, x, y, r);
            leftColorThreshold = colorSimilaritySensitivity * colorSimilarity;
            rightAvg = getTopColorSliceAvg(backgroundBitmap, x, y, r);
            rightColorThreshold = colorSimilaritySensitivity * colorSimilarity;
            Point topPoint, bottomPoint;
            int stopValue = y - r;
            int yN = y;
            do { // Start at y and work up (negative)
                yN--;
            } while (yN > stopValue && yN >= 0 && areColorsSimilar(rightAvg, backgroundBitmap.getPixel(x, yN), leftColorThreshold));
            topPoint = new Point(x, yN);
            stopValue = y + r;
            yN = y;
            do { // Start at y and work down (positive)
                yN++;
            } while (yN < stopValue && yN < backgroundBitmap.getHeight() && areColorsSimilar(leftAvg, backgroundBitmap.getPixel(x, yN), leftColorThreshold));
            bottomPoint = new Point(x, yN);
            return new Point(x, (topPoint.y + bottomPoint.y) / 2);
        }
        Point leftPoint = new Point(x, y);
        Point rightPoint = new Point(x, y);
        // Determine when color changes from the left
        double m = -1 / tanSlope; // Perpendicular slope
        int rN = 0; // start at the middle
        int stopValue = r/2   ; // stop at the right
        int currentX, currentY;
        int leftWeight = 1, rightWeight = 1;
        int a, b;
        do {
            a = (int)Math.round(((double) rN) / Math.sqrt(m * m + 1));
            b = (int)Math.round(((double) rN) / Math.sqrt(1 / (m * m) + 1));
            currentX = x - a;
            if (currentX < 0) currentX = 0;
            else if(currentX >= backgroundBitmap.getWidth()) currentX = backgroundBitmap.getWidth() - 1;
            currentY = y - b;
            if (currentY < 0) currentY = 0;
            else if(currentY >= backgroundBitmap.getHeight()) currentY = backgroundBitmap.getHeight() - 1;
            rN++; // move out
            //backgroundBitmap.setPixel(currentX, currentY, Color.GREEN);
        } while (areColorsSimilar(leftAvg, backgroundBitmap.getPixel(currentX, currentY), leftColorThreshold) && rN < stopValue);
        if (m < 0) {
            leftPoint = new Point(x - a, y - b);
            leftWeight = stopValue - rN;
        }
        else {
            rightPoint = new Point(x - a, y - b);
            rightWeight = stopValue - rN;
        }
        // Determine when color changes from the right
        rN = 0; // start at the middle
        stopValue = -r;
        do {
            a = (int)Math.round(((double) rN) / Math.sqrt(m * m + 1));
            b = (int)Math.round(((double) rN) / Math.sqrt(1 / (m * m) + 1));
            currentX = x + a;
            if (currentX < 0) currentX = 0;
            else if(currentX >= backgroundBitmap.getWidth()) currentX = backgroundBitmap.getWidth() - 1;
            currentY = y + b;
            if (currentY < 0) currentY = 0;
            else if(currentY >= backgroundBitmap.getHeight()) currentY = backgroundBitmap.getHeight() - 1;
            //backgroundBitmap.setPixel(currentX, currentY, Color.YELLOW);
            rN--;
        } while (areColorsSimilar(rightAvg, backgroundBitmap.getPixel(currentX, currentY), rightColorThreshold) && rN > stopValue);
        if (m < 0) {
            leftPoint = new Point(x + a, y + b);
            leftWeight = Math.abs(stopValue - rN);
        }
        else {
            rightPoint = new Point(x + a, y + b);
            rightWeight = Math.abs(stopValue - rN);
        }
        // Return weigthed average
        int newX = (leftWeight * leftPoint.x + x + rightWeight * rightPoint.x) / (leftWeight + 1 + rightWeight);
        int newY = (leftWeight * leftPoint.y + y + rightWeight * rightPoint.y) / (leftWeight + 1 + rightWeight);
        return new Point(newX, newY);
    }
    private Point determineCutPointVert(int x, int y, int strokeWidth){
        int r = strokeWidth / 2;

        // Determine color average left of the point
        int leftAvg = getLeftColorSliceAvgVert(backgroundBitmap, x, y, r);
        double leftColorThreshold = colorSimilaritySensitivity * colorSimilarity;
        // Determine color average right of the point
        int rightAvg = getRightColorSliceAvgVert(backgroundBitmap, x, y, r);
        double rightColorThreshold = colorSimilaritySensitivity * colorSimilarity;

        // Determine when color changes from the left
        Point leftPoint;
        int stopValue = -r;
        int rN = r;
        do { // Start at the end and work to the middle
            rN--;
        } while (areColorsSimilar(leftAvg, backgroundBitmap.getPixel(x - rN, y), leftColorThreshold) && rN > stopValue);
        leftPoint = new Point(x - rN, y);

        // Determine when color change from the right
        Point rightPoint;
        rN = r;
        do { // Start at the end and work to the middle
            rN--;
        } while (areColorsSimilar(leftAvg, backgroundBitmap.getPixel(x + rN, y), rightColorThreshold) && rN > stopValue);
        rightPoint = new Point(x + rN, y);

        // Return midpoint of left and right
        return new Point((leftPoint.x + rightPoint.x) / 2, (leftPoint.y + rightPoint.y) / 2);
    }
    private int getLeftColorSliceAvgVert(Bitmap bitmap, int x, int y, int lengthOver10){
        int[] colors = new int[Math.abs(x - (lengthOver10 - 8))];
        int currentX;
        if (y < 0) y = 0;
        else if (y >= bitmap.getHeight()) y = bitmap.getHeight() - 1;
        for (int i = 0; i < colors.length; i++){
            currentX = x - (i+10);
            if (currentX < 0) currentX = 0;
            else if (currentX >= bitmap.getWidth()) currentX = bitmap.getWidth() - 1;
            colors[i] = bitmap.getPixel(currentX, y);
        }
        return getColorAverage(colors);
    }
    private int getRightColorSliceAvgVert(Bitmap bitmap, int x, int y, int lengthOver10){
        int[] colors = new int[Math.abs(x - (lengthOver10 - 8))];
        int currentX;
        if (y < 0) y = 0;
        else if (y >= bitmap.getHeight()) y = bitmap.getHeight() - 1;
        for (int i = 0; i < colors.length; i++){
            currentX = x + (i+10);
            if (currentX < 0) currentX = 0;
            else if (currentX >= bitmap.getWidth()) currentX = bitmap.getWidth() - 1;
            colors[i] = bitmap.getPixel(currentX, y);
        }
        return getColorAverage(colors);
    }

    // Magic erase methods
    public void setMagicEraseMode(boolean isMagicEraseMode){
        designerMode = MODE_MAGIC_ERASE;
    }
    private boolean handleMagicEraseTouch(MotionEvent event){
        if (backgroundBitmap != null) {
            float [] point = {event.getX(), event.getY()};
            Matrix unZoom = new Matrix(zoomMatrix);
            unZoom.invert(unZoom);
            unZoom.mapPoints(point);
            float x = point[0], y = point[1];
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    addBackgroundUndo();
                    magicErase((int) x, (int) y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    magicErase((int) x, (int) y);
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }
    private void magicErase(int x, int y) {
        isSaved = false;
        x = Math.min(x, backgroundBitmap.getWidth() - 1);
        x = Math.max(0, x);
        y = Math.min(y, backgroundBitmap.getHeight() - 1);
        y = Math.max(0, y);
        boolean keepSearchingy = true;
        boolean keepSearchingx = true;
        int rgbInit = getColorAverage(backgroundBitmap, x, y, 2);
        double threshold = colorSimilarity * colorSimilaritySensitivity;
        //Log.d(LOG_TAG, "Magic Erase Threshold = " + threshold);
        int rgbCurrent, rgbPrevX;
        int xN, yN, prevX = x;
        for (xN = x; xN < backgroundBitmap.getWidth() && keepSearchingx; xN++) { // Search to the right
            for (yN = y; yN < backgroundBitmap.getHeight() && keepSearchingy; yN++) { // Search down
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                if (rgbCurrent != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbCurrent, threshold)){
                    backgroundBitmap.setPixel(xN, yN, Color.TRANSPARENT);
                    prevX = Utils.getInBounds(xN - 1, x, backgroundBitmap.getWidth() - 1); // Work backwards to check for missed pixels
                    do {
                        rgbPrevX = backgroundBitmap.getPixel(prevX, yN);
                        if (rgbPrevX != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbPrevX, threshold)){
                            backgroundBitmap.setPixel(prevX, yN, Color.TRANSPARENT);
                        } else keepSearchingx = false;
                        prevX--;
                    } while (keepSearchingx && prevX > x);
                    keepSearchingx = true;
                }
                else keepSearchingy = false;
            }

            keepSearchingy = true;
            for (yN = y - 1; yN >= 0 && keepSearchingy; yN--) { // Search up, start at y - 1 since y has been checked
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                if (rgbCurrent != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbCurrent, threshold)){
                    backgroundBitmap.setPixel(xN, yN, Color.TRANSPARENT);
                    prevX = Utils.getInBounds(xN - 1, x, backgroundBitmap.getWidth() - 1);
                    do {
                        rgbPrevX = backgroundBitmap.getPixel(prevX, yN);
                        if (rgbPrevX!= Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbPrevX, threshold)){
                            backgroundBitmap.setPixel(prevX, yN, Color.TRANSPARENT);
                        } else keepSearchingx = false;
                        prevX--;
                    } while (keepSearchingx && prevX > x);
                    keepSearchingx = true;
                }
                else keepSearchingy = false;
                if (!keepSearchingy && yN == y - 1){
                    keepSearchingx = false;
                }
            }
            keepSearchingy = true;
            invalidate();
        }
        keepSearchingy = true;
        keepSearchingx = true;
        prevX = x - 1;
        for (xN = x - 1; xN >= 0 && keepSearchingx; xN--) { // Search left, start at x - 1 since x has been checked
            for (yN = y; yN < backgroundBitmap.getHeight() && keepSearchingy; yN++) {
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                if (rgbCurrent != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbCurrent, threshold)){
                    backgroundBitmap.setPixel(xN, yN, Color.TRANSPARENT);
                    rgbPrevX = backgroundBitmap.getPixel(prevX, yN);
                    if (rgbPrevX != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbPrevX, threshold))
                        backgroundBitmap.setPixel(prevX, yN, Color.TRANSPARENT);
                }
                else keepSearchingy = false;
            }
            keepSearchingy = true;
            for (yN = y - 1; yN >= 0 && keepSearchingy; yN--) {
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                if (rgbCurrent != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbCurrent, threshold)){
                    backgroundBitmap.setPixel(xN, yN, Color.TRANSPARENT);
                    rgbPrevX = backgroundBitmap.getPixel(prevX, yN);
                    if (rgbPrevX != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbPrevX, threshold))
                        backgroundBitmap.setPixel(prevX, yN, Color.TRANSPARENT);
                }
                else keepSearchingy = false;
                if (!keepSearchingy && yN == y - 1) {
                    keepSearchingx = false;
                }
            }
            prevX = xN;
            keepSearchingy = true;
            invalidate();
        }
    } // Best working version
    private void magicEraseOld3(float x, float y){
        // Variables for drawing the erase
        float strokeWidth = 4;
        Paint erasePaint = new Paint(Color.TRANSPARENT);
        erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        erasePaint.setAntiAlias(true);
        erasePaint.setStrokeWidth(strokeWidth);
        erasePaint.setStyle(Paint.Style.STROKE);
        erasePaint.setStrokeJoin(Paint.Join.ROUND);
        erasePaint.setStrokeCap(Paint.Cap.ROUND);
        erasePaint.setMaskFilter(new BlurMaskFilter(1, BlurMaskFilter.Blur.NORMAL));
        erasePaint.setStyle(Paint.Style.FILL_AND_STROKE);

        Path erasePath = new Path();

        // Variables for loops
        boolean keepSearchingy = true;
        boolean keepSearchingx = true;
        int accuracy = (int)(strokeWidth / 2);
        int rgbInit = getColorAverage(backgroundBitmap, (int)x, (int)y, 8);
        int rgbCurrent;
        ArrayList<Point> upperRightpoints = new ArrayList<>();
        ArrayList<Point> lowerRightPoints = new ArrayList<>();
        ArrayList<Point> lowerLeftPoints = new ArrayList<>();
        ArrayList<Point> upperLeftPoints = new ArrayList<>();

        // Search clockwise
        // 12 o'clock to 3 o'clock
        // Right loop
        int xN = (int)x, yN = (int)y;
        for (xN = (int)x; xN < backgroundBitmap.getWidth() && xN > 0 && keepSearchingx; xN++) { // Starts at 12 o'clock, positive is right
            // Up loop
            keepSearchingy = true;
            for (yN = (int)y; yN >= 0 && yN < backgroundBitmap.getHeight() && keepSearchingy; yN--) { // Negative is up
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                keepSearchingy = areColorsSimilar(rgbInit, rgbCurrent, colorSimilarity) && rgbCurrent != Color.TRANSPARENT;
                if (!keepSearchingy && (Math.abs(yN - y) < accuracy)) keepSearchingx = false;
            }
            if (upperRightpoints.size() == 0 || yN < upperRightpoints.get(upperRightpoints.size() - 1).y || yN == 0) { // The first point at 12 o'clock
                if (Math.abs(yN - y) > accuracy || yN == 0)
                    upperRightpoints.add(new Point(xN, yN));
            }
        }
        if (yN != y)
            upperRightpoints.add(new Point(xN, yN)); // 3 o'clock
        // 3 o'clock to 6 o'clock
        // Left loop
        keepSearchingy = true;
        for (xN = xN; xN < backgroundBitmap.getWidth() && xN > 0 && keepSearchingx; xN--) { // Start at 3 o'clock, negative is left
            // Down loop
            for (yN = (int) y + 1; yN < backgroundBitmap.getHeight() && keepSearchingy; yN++) {  // Start at the next row down, Positive is down
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                keepSearchingy = areColorsSimilar(rgbInit, rgbCurrent, colorSimilarity) && rgbCurrent != Color.TRANSPARENT;
                if (!keepSearchingy && Math.abs(yN - y) < accuracy) keepSearchingx = false;
            }
            if (lowerRightPoints.size() == 0 || yN > lowerRightPoints.get(lowerRightPoints.size() - 1).y || yN == backgroundBitmap.getHeight() - 1) {
                if (Math.abs(yN - (y + 1)) > accuracy || yN == backgroundBitmap.getHeight() - 1)
                    lowerRightPoints.add(new Point(xN, yN)); //
            }
        }
        if (yN != y + 1)
            lowerRightPoints.add(new Point((int)x, yN)); // 6 o'clock
        // 6 o'clock to 9 o'clock
        // Left loop
        keepSearchingy = true;
        for (xN = (int) x; xN >= 0 && xN < backgroundBitmap.getWidth() && keepSearchingx; xN--) { // Starts at 6 o'clock, negative is left
            // Down loop
            for (yN = (int)y + 1; yN < backgroundBitmap.getHeight() && keepSearchingy; yN++) { // Still next row down, Positive is down
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                keepSearchingy = areColorsSimilar(rgbInit, rgbCurrent, colorSimilarity) && rgbCurrent != Color.TRANSPARENT;
                if (!keepSearchingy && Math.abs(yN - y) < accuracy) keepSearchingx = false;
            }
            if(lowerLeftPoints.size() == 0 || yN > lowerLeftPoints.get(lowerLeftPoints.size() - 1).y || yN == backgroundBitmap.getHeight() - 1) {
                if (Math.abs(yN - (y + 1)) > accuracy || yN == backgroundBitmap.getHeight() - 1)
                    lowerLeftPoints.add(new Point(xN, yN));
            }
        }
        if (yN != y + 1)
            lowerLeftPoints.add(new Point(xN, (int)y)); // 9 o'clock
        // 9 o'clock to 12 o'clock
        // Right loop
        if (xN < 0) xN = 0;
        for (xN = xN; xN < x && keepSearchingx; xN++) { // Starts at xN, positive is right
            // Up loop
            keepSearchingy = true;
            for (yN = (int)y; yN >= 0 && yN < backgroundBitmap.getHeight() && keepSearchingy; yN--) { // Back to y, negative is up
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                keepSearchingy = areColorsSimilar(rgbInit, rgbCurrent, colorSimilarity) && rgbCurrent != Color.TRANSPARENT;
                if (!keepSearchingy && Math.abs(yN - y) < accuracy) keepSearchingx = false;
            }
            if (upperLeftPoints.size() == 0 || yN < upperLeftPoints.get(upperLeftPoints.size() - 1).y || yN == 0) {
                if (Math.abs(yN - y) > accuracy || yN == 0)
                    upperLeftPoints.add(new Point(xN, yN));
            }

        }

        // Erase area defined by points
        boolean movedToFirst = false,
                upperRightDrawn = false, lowerRightDrawn = false,
                lowerLeftDrawn = false, upperLeftDrawn = false;
        if (upperRightpoints.size() > 0) {
            erasePath.moveTo(upperRightpoints.get(0).x, upperRightpoints.get(0).y);
            upperRightDrawn = true;
            for (int i = 0; i < upperRightpoints.size(); i++) {
                erasePath.lineTo(upperRightpoints.get(i).x, upperRightpoints.get(i).y);
            }
        }
        if (lowerRightPoints.size() > 0) {
            if (!upperRightDrawn){
                erasePath.moveTo(lowerRightPoints.get(0).x, lowerRightPoints.get(0).y);
            }
            lowerRightDrawn = true;
            for (int i = 0; i < lowerRightPoints.size(); i++) {
                erasePath.lineTo(lowerRightPoints.get(i).x, lowerRightPoints.get(i).y);
            }
        }
        if (lowerLeftPoints.size() > 0) {
            if (!lowerRightDrawn){
                if (upperRightDrawn)
                    erasePath.lineTo(x, y);
                else
                    erasePath.moveTo(x, y);
            }
            lowerLeftDrawn = true;
            for (int i = 0; i < lowerLeftPoints.size(); i++) {
                erasePath.lineTo(lowerLeftPoints.get(i).x, lowerLeftPoints.get(i).y);
            }
        }
        if (upperLeftPoints.size() > 0) {
            if (!lowerLeftDrawn){
                if (upperRightDrawn || lowerRightDrawn)
                    erasePath.lineTo(x, y);
                else
                    erasePath.moveTo(upperLeftPoints.get(0).x, upperLeftPoints.get(0).y);
            }
            upperLeftDrawn = true;
            for (int i = 0; i < upperLeftPoints.size() - 1; i++) {
                erasePath.lineTo(upperLeftPoints.get(i).x, upperLeftPoints.get(i).y);
            }
            erasePath.setLastPoint(upperLeftPoints.get(upperLeftPoints.size() - 1).x, upperLeftPoints.get(upperLeftPoints.size() - 1).y);
        }
        backgroundCanvas.drawPath(erasePath, erasePaint);
        backgroundCanvas.drawPath(erasePath, erasePaint);
        invalidate();

    }

    // Color similarity methods
    public void setColorSimilaritySensitivity(double level){
        colorSimilaritySensitivity = level;
    }
    private boolean areColorsSimilar(int color1, int color2, double threshold){
        int r0, g0, b0, r1, g1, b1;
        r0 = (color1 >> 16) & 0xFF;
        g0 = (color1 >> 8) & 0xFF;
        b0 = color1 & 0xFF;
        r1 = (color2 >> 16) & 0xFF;
        g1 = (color2 >> 8) & 0xFF;
        b1 = color2 & 0xFF;

        return Math.sqrt((r1 - r0) * (r1 - r0) + (g1 - g0) * (g1 - g0) + (b1 - b0) * (b1 - b0)) <= threshold;
    }
    private double getColorDiff(int color1, int color2){
        int r0, g0, b0, r1, g1, b1;
        r0 = (color1 >> 16) & 0xFF;
        g0 = (color1 >> 8) & 0xFF;
        b0 = color1 & 0xFF;
        r1 = (color2 >> 16) & 0xFF;
        g1 = (color2 >> 8) & 0xFF;
        b1 = color2 & 0xFF;
        return Math.sqrt((r1 - r0) * (r1 - r0) + (g1 - g0) * (g1 - g0) + (b1 - b0) * (b1 - b0));
    }
    private int getColorAverage(int[] pixels){
        int sum = 0;
        double difference = 0;
        int trueSize = 0;
        int average;
        for (int i : pixels) {
            if (i != Color.TRANSPARENT) {
                sum += i;
                trueSize++;
            }
        }
        if (trueSize > 0)
             average = sum / trueSize;
        else
            return 0;

        for (int i : pixels) {
            if (i != Color.TRANSPARENT)
                difference = Math.max(difference, getColorDiff(average, i));
        }
        colorSimilarity = difference;
        //Log.d(LOG_TAG, "Color similarity: " + colorSimilarity);
        return average;
    }
    private int getColorAverage(Bitmap b, int x, int y, int radius){
        if (radius == 0) return b.getPixel(x, y);

        int width = radius * 2 + 1;
        int height = radius * 2 + 1;
        int xStart = x - radius;
        if (xStart + width > b.getWidth()) width = b.getWidth() - xStart;
        if (xStart < 0) xStart = 0;
        int yStart = y - radius;
        if (yStart + height > b.getHeight()) height = b.getHeight() - yStart;
        if (yStart < 0) yStart = 0;
        if (width < 0 || height < 0)
            return -1;
        int[] colors = new int[width * height];
        int index = 0;
        for (int i = xStart; i < xStart + width; i++){
            for (int j = yStart; j < yStart + height; j++){
                colors[index++] = b.getPixel(i, j);
            }
        }
        int size = 0;
        return getColorAverage(colors);
    }

    // Draw mode methods
    public void setIsDrawMode(boolean isDrawMode) {
        designerMode = MODE_DRAW;
        drawPaint = new Paint();
        drawPaint.setColor(drawColor);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(18);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
    }
    public boolean isDrawMode() {
        return isDrawMode;
    }
    private boolean handleDrawTouch(MotionEvent event){
        float [] point = {event.getX(), event.getY()};
        Matrix unZoom = new Matrix(zoomMatrix);
        unZoom.invert(unZoom);
        unZoom.mapPoints(point);
        float x = point[0], y = point[1];
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                addDrawUndo();
                isSaved = false;
                prevX = x;
                prevY = y;
                drawPath.moveTo(x, y);
                drawPath.lineTo(x + 1, y + 1);
                drawCanvas.drawPath(drawPath, drawPaint);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (prevX < 0 || prevY < 0){
                    addDrawUndo();
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
                if (prevX < 0 || prevY < 0){
                    prevX = x;
                    prevY = y;
                    drawPath.moveTo(x, y);
                }
                else {
                    prevX = -1;
                    prevY = -1;
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
    public void setColor(int color){
        drawPaint.setXfermode(null);
        drawPaint.setColor(color);
        drawColor = color;
    }
    public void setStrokeWidth(float width){
        drawPaint.setStrokeWidth(width);
    }

    public void setEraseMode(boolean erase){
        designerMode = MODE_DRAW_ERASE;
        drawPaint.setColor(Color.TRANSPARENT);
        drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }
    public boolean isEraseMode(){return isEraseMode;}

    // Background erase methods
    public void setBackgroundErase(){
        if (backgroundBitmap != null) {
            designerMode = MODE_BACKGROUND_ERASE;
            drawPaint.setColor(Color.TRANSPARENT);
            drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

    }
    public void cancelBackgroundErase(){
        designerMode = MODE_SELECT;
        drawPaint.setXfermode(null);
    }
    private boolean handleBackgroundEraseTouch(MotionEvent event){
        float [] point = {event.getX(), event.getY()};
        Matrix unZoom = new Matrix(zoomMatrix);
        unZoom.invert(unZoom);
        unZoom.mapPoints(point);
        float x = point[0], y = point[1];
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                prevX = x;
                prevY = y;
                addBackgroundUndo();
                drawPath.moveTo(x, y);
                drawPath.lineTo(x + 1, y + 1);
                backgroundCanvas.drawPath(drawPath, drawPaint);
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

    // Image position methods
    public void flipHorz(){
        if (backgroundBitmap != null) {
            Matrix m = new Matrix();
            m.preScale(-1, 1);
            backgroundBitmap = Bitmap.createBitmap(backgroundBitmap, 0, 0, backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), m, false);
            backgroundCanvas = new Canvas(backgroundBitmap);
            backgroundOriginal = Bitmap.createBitmap(backgroundOriginal, 0, 0, backgroundOriginal.getWidth(), backgroundOriginal.getHeight(), m, false);
            invalidate();
            requestLayout();
            //dst.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        }
    }
    public void rotateRight(){
        if (backgroundBitmap != null){
            Matrix m = new Matrix();
            m.setRotate(90);
            // Rotate background and draw bitmaps
            backgroundBitmap = Bitmap.createBitmap(backgroundBitmap, 0, 0, backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), m, false);
            backgroundCanvas = new Canvas(backgroundBitmap);
            backgroundOriginal = Bitmap.createBitmap(backgroundOriginal, 0, 0, backgroundOriginal.getWidth(), backgroundOriginal.getHeight(), m, false);
            drawBitmap = Bitmap.createBitmap(drawBitmap, 0, 0, drawBitmap.getWidth(), drawBitmap.getHeight(), m, false);
            drawCanvas = new Canvas(drawBitmap);

            // Re-position the crop boxes
            int padding = (int)(Math.min(backgroundBitmap.getHeight() * 0.1, backgroundBitmap.getWidth()) * 0.1);
            upperJawBox = new RectF(padding, padding, backgroundBitmap.getWidth() - padding, backgroundBitmap.getHeight() / 2);
            upperJawPivotPoint = new Point(backgroundBitmap.getWidth() / 2, (int)upperJawBox.bottom);
            lowerJawBox = new RectF(padding, backgroundBitmap.getHeight() / 2, backgroundBitmap.getWidth() - padding, backgroundBitmap.getHeight() - padding);
            //lowerJawPivotPoint = new Point(lowerJawBox.left + lowerJawBox.width() / 2, lowerJawBox.top);
            lowerJawPivotPoint = upperJawPivotPoint;

            invalidate();
            requestLayout();
        }
    }
    public void rotateLeft(){
        if (backgroundBitmap != null){
            Matrix m = new Matrix();
            m.setRotate(-90);
            // Rotate background and draw bitmaps
            backgroundBitmap = Bitmap.createBitmap(backgroundBitmap, 0, 0, backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), m, false);
            backgroundCanvas = new Canvas(backgroundBitmap);
            backgroundOriginal = Bitmap.createBitmap(backgroundOriginal, 0, 0, backgroundOriginal.getWidth(), backgroundOriginal.getHeight(), m, false);
            drawBitmap = Bitmap.createBitmap(drawBitmap, 0, 0, drawBitmap.getWidth(), drawBitmap.getHeight(), m, false);
            drawCanvas = new Canvas(drawBitmap);

            // Re-position the crop boxes
            int padding = (int)(Math.min(backgroundBitmap.getHeight() * 0.1, backgroundBitmap.getWidth()) * 0.1);
            upperJawBox = new RectF(padding, padding, backgroundBitmap.getWidth() - padding, backgroundBitmap.getHeight() / 2);
            upperJawPivotPoint = new Point(backgroundBitmap.getWidth() / 2, (int)upperJawBox.bottom);
            lowerJawBox = new RectF(padding, backgroundBitmap.getHeight() / 2, backgroundBitmap.getWidth() - padding, backgroundBitmap.getHeight() - padding);
            //lowerJawPivotPoint = new Point(lowerJawBox.left + lowerJawBox.width() / 2, lowerJawBox.top);
            lowerJawPivotPoint = upperJawPivotPoint;

            invalidate();
            requestLayout();
        }
    }

    // Methods for getting and setting images and puppet
    public Puppet getPuppet(){
        if (backgroundUndoStack != null) {
            for (Bitmap b : backgroundUndoStack)
                b.recycle();
        }
        if (drawUndoStack != null) {
            for (Bitmap b : drawUndoStack)
                b.recycle();
        }
        Puppet puppet = new Puppet(context, null);
        puppet.setOrientation(getOrientation());
        puppet.setRotation(getRotation());
        puppet.setImages(getUpperJaw(), getLowerJaw(), getUpperJawPivotPoint(), getLowerJawPivotPoint());

        return puppet;
    }
    public Point getUpperJawPivotPoint(){ // (0, h) is bottom left of box
        if (pivotsSnapped){
            upperJawPivotPoint = new Point(lowerJawPivotPoint.x, lowerJawPivotPoint.y);
        }
        //Log.d(LOG_TAG, "Upper jaw pivot x = " + String.valueOf(upperJawPivotPoint.x - upperJawBox.left));
        return new Point(upperJawPivotPoint.x - (int)upperJawBox.left, (int)upperJawBox.height());
    }
    public Point getLowerJawPivotPoint() { // (0, 0) is top left of box
        //Log.d(LOG_TAG, "Lower jaw pivot x = " + String.valueOf(lowerJawPivotPoint.x - lowerJawBox.left));
        return new Point(lowerJawPivotPoint.x - (int)lowerJawBox.left, 0);
    }
    public Bitmap getUpperJaw(){
        int size = (int)Math.sqrt(drawBitmap.getWidth() * drawBitmap.getWidth() + drawBitmap.getHeight() * drawBitmap.getHeight());
        int widthPadding = (size - drawBitmap.getWidth()) / 2;
        int heightPadding = (size - drawBitmap.getHeight()) / 2;
        Bitmap bmOverlay = Bitmap.createBitmap(size, size, drawBitmap.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.save();
        canvas.rotate(-rotation, widthPadding + upperJawPivotPoint.x, heightPadding + upperJawPivotPoint.y);
        if (backgroundBitmap != null)
            canvas.drawBitmap(backgroundBitmap, widthPadding, heightPadding, null);
        canvas.drawBitmap(drawBitmap, widthPadding, heightPadding, null);
        canvas.restore();
        Bitmap upperJaw = null;
        int left = (int)upperJawBox.left + widthPadding, top = (int)upperJawBox.top + heightPadding;
        int width = (int)upperJawBox.width(), height = (int)upperJawBox.height();
        if (left < 0) {
            width = width + left;
            left = 0;
        }
        if (top < 0) {
            height = height + top;
            top = 0;
        }

        if (width + left > bmOverlay.getWidth()) width = bmOverlay.getWidth() - left;
        if (height + top > bmOverlay.getHeight()) height = bmOverlay.getHeight() - top;
        upperJaw = Bitmap.createBitmap(bmOverlay, left, top, width, height);
        return upperJaw.copy(Bitmap.Config.ARGB_8888, true);
    }
    public Bitmap getLowerJaw(){
        int size = (int)Math.sqrt(drawBitmap.getWidth() * drawBitmap.getWidth() + drawBitmap.getHeight() * drawBitmap.getHeight());
        int widthPadding = (size - drawBitmap.getWidth()) / 2;
        int heightPadding = (size - drawBitmap.getHeight()) / 2;
        Bitmap bmOverlay = Bitmap.createBitmap(size, size, drawBitmap.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.save();
        canvas.rotate(-rotation, widthPadding + upperJawPivotPoint.x, heightPadding + upperJawPivotPoint.y);
        if (backgroundBitmap != null)
            canvas.drawBitmap(backgroundBitmap, widthPadding, heightPadding, null);
        canvas.drawBitmap(drawBitmap, widthPadding, heightPadding, null);
        canvas.restore();
        Bitmap lowerJaw = null;
        int left = (int)lowerJawBox.left + widthPadding, top = (int)lowerJawBox.top + heightPadding;
        int width = (int)lowerJawBox.width(), height = (int)lowerJawBox.height();
        if (left < 0) {
            width = width + left;
            left = 0;
        }
        if (top < 0) {
            height = height + top;
            top = 0;
        }
        if (width + left > bmOverlay.getWidth()) width = bmOverlay.getWidth() - left;
        if (height + top > bmOverlay.getHeight()) height = bmOverlay.getHeight() - top;
        lowerJaw = Bitmap.createBitmap(bmOverlay, left, top, width, height);
        return lowerJaw.copy(Bitmap.Config.ARGB_8888, true);
    }
    public void SetNewImage(Bitmap image) {
        release();
        backgroundBitmap = image.copy(Bitmap.Config.ARGB_8888, true);
        backgroundBitmap.setHasAlpha(true);
        backgroundCanvas = new Canvas(backgroundBitmap);
        backgroundOriginal = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true);
        drawBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        drawBitmap.setHasAlpha(true);
        drawCanvas = new Canvas(drawBitmap);

        // Set initial positions of UI elements
        int padding = (int)(Math.min(image.getHeight() / 4, image.getWidth()) / 4);
        upperJawBox = new RectF(padding, padding, image.getWidth() - padding, image.getHeight() / 2);
        upperJawPivotPoint = new Point(image.getWidth() / 2, (int)upperJawBox.bottom);
        lowerJawBox = new RectF(padding, image.getHeight() / 2, image.getWidth() - padding, image.getHeight() - padding);
        upperProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.upper_profile_right, (int)upperJawBox.width(), (int)upperJawBox.height());
        lowerProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.lower_profile_right, (int)lowerJawBox.width(), (int)lowerJawBox.height());
        //lowerJawPivotPoint = new Point(lowerJawBox.left + lowerJawBox.width() / 2, lowerJawBox.top);
        lowerJawPivotPoint = upperJawPivotPoint;
        rotateHandle = new Point(upperJawPivotPoint.x + 150, upperJawPivotPoint.y);
        rotation = 0;
        zoomPoint = new Point(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomFactor = 1f;
        zoomMatrix.setScale(zoomFactor, zoomFactor, drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomMatrix.postTranslate((getWidth() - drawBitmap.getWidth()) / 2, (getHeight() - drawBitmap.getHeight()) / 2);
        isSaved = false;

        invalidate();
        requestLayout();
    }
    public void CreatBlankImage(int w, int h){
        release();

        // Setup blank drawing canvas
        drawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawBitmap.setHasAlpha(true);
        drawCanvas = new Canvas(drawBitmap);
        drawPath = new Path();
        backgroundBitmap = null;

        // Set initial positions of UI elements
        int padding = (int)(Math.min(drawBitmap.getHeight() * 0.1, drawBitmap.getWidth()) * 0.1);
        upperJawBox = new RectF(padding, padding, drawBitmap.getWidth() - padding, drawBitmap.getHeight() / 2);
        upperJawPivotPoint = new Point(drawBitmap.getWidth() / 2, (int)upperJawBox.bottom);
        lowerJawBox = new RectF(padding, drawBitmap.getHeight() / 2, drawBitmap.getWidth() - padding, drawBitmap.getHeight() - padding);
        upperProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.upper_profile_right, (int)upperJawBox.width(), (int)upperJawBox.height());
        lowerProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.lower_profile_right, (int)lowerJawBox.width(), (int)lowerJawBox.height());
        lowerJawPivotPoint = upperJawPivotPoint;
        rotateHandle = new Point(upperJawPivotPoint.x + 150, upperJawPivotPoint.y);
        rotation = 0;
        zoomPoint = new Point(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomFactor = 1f;
        zoomMatrix.setScale(zoomFactor, zoomFactor, drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomMatrix.postTranslate((getWidth() - drawBitmap.getWidth()) / 2, (getHeight() - drawBitmap.getHeight()) / 2);

        invalidate();
        requestLayout();
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
        overlay.recycle();
        upperJawBox = new RectF(puppet.getUpperLeftPadding(), 0, puppet.getUpperLeftPadding() + upperJawBitmap.getWidth(), upperJawBitmap.getHeight());
        lowerJawBox = new RectF(puppet.getLowerLeftPadding(), upperJawBitmap.getHeight(), puppet.getLowerLeftPadding() + lowerJawBitmap.getWidth(), overlay.getHeight());
        upperProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.upper_profile_right, (int)upperJawBox.width(), (int)upperJawBox.height());
        lowerProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.lower_profile_right, (int)lowerJawBox.width(), (int)lowerJawBox.height());
        lowerJawPivotPoint = new Point(puppet.getLowerLeftPadding() + puppet.getLowerPivotPoint().x, upperJawBitmap.getHeight());
        upperJawPivotPoint = lowerJawPivotPoint;
        this.orientation = puppet.getOrientation();
        if (orientation == Puppet.PROFILE_RIGHT) {
            rotateHandle = new Point(upperJawPivotPoint.x + 150, upperJawPivotPoint.y);
        } else {
            Matrix m = new Matrix();
            m.setScale(-1, 1);
            upperProfileBitmap = Bitmap.createBitmap(upperProfileBitmap, 0, 0, upperProfileBitmap.getWidth(), upperProfileBitmap.getHeight(), m, false);
            lowerProfileBitmap = Bitmap.createBitmap(lowerProfileBitmap, 0, 0, lowerProfileBitmap.getWidth(), lowerProfileBitmap.getHeight(), m, false);
            rotateHandle = new Point(upperJawPivotPoint.x - 150, upperJawPivotPoint.y);
        }
        rotation = 0;
        zoomPoint = new Point(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomFactor = 1f;
        zoomMatrix.setScale(zoomFactor, zoomFactor, drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomMatrix.postTranslate((getWidth() - drawBitmap.getWidth()) / 2, (getHeight() - drawBitmap.getHeight()) / 2);
        isSaved = true;
        invalidate();
        requestLayout();
    }

    // Selection mode methods
    public void setSelectionMode(){
        designerMode = MODE_SELECT;
        if (upperProfileBitmap == null || lowerProfileBitmap == null) {
            upperProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.upper_profile_right, (int)upperJawBox.width(), (int)upperJawBox.height());
            lowerProfileBitmap = Utils.loadScaledBitmap(context.getResources(), R.drawable.lower_profile_right, (int)lowerJawBox.width(), (int)lowerJawBox.height());
        }
        showLowerJawBox = true;
        showUpperJawBox = true;
        zoomPoint = new Point(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2);
        zoomFactor = 1f;
        zoomMatrix.setScale(zoomFactor, zoomFactor, zoomPoint.x, zoomPoint.y);
        zoomMatrix.postTranslate((getWidth() - drawBitmap.getWidth()) / 2, (getHeight() - drawBitmap.getHeight()) / 2);
        invalidate();
        requestLayout();
    }
    private boolean handleSelectionTouch(MotionEvent event){
        float[] point = {event.getX(), event.getY()};
        Matrix reverse = new Matrix(zoomMatrix);
        float[] pivotPoint = {upperJawPivotPoint.x, upperJawPivotPoint.y};
        reverse.mapPoints(pivotPoint);
        reverse = new Matrix();
        reverse.setRotate(-rotation, pivotPoint[0], pivotPoint[1]);
        reverse.mapPoints(point);
        reverse = new Matrix(zoomMatrix);
        reverse.invert(reverse);
        reverse.mapPoints(point);

        float x = point[0], y = point[1];
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                prevX = x;
                prevY = y;
                float a = event.getRawX() - upperJawPivotPoint.x;
                float b = event.getRawY() - upperJawPivotPoint.y;
                double rads = Math.atan(b / a);
                float angle = ((float)Math.toDegrees(rads)) % 360;
                if (angle < -180.f) angle += 360.0f;
                if (angle > 180.f) angle -= 360.0f;
                prevAngle = angle;
                prevRotation = rotation;
                setSelectionId(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (selectionId == NO_SELECTION) {
                    prevX = x;
                    prevY = y;
                    setSelectionId(x, y);
                } else {
                    isSaved = false;
                    if (selectionId == ROTATE_HANDLE){
                        moveSelection((int) prevX, (int) prevY, (int) event.getRawX(), (int) event.getRawY());
                    } else {
                        moveSelection((int) prevX, (int) prevY, (int) x, (int) y);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                checkForSnap();
                selectionId = NO_SELECTION;
                return true;
            default:
                return true;
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
            //selectionId = UPPER_JAW;
            if (Math.abs(y - upperJawBox.top) < edgeThresh) selectionId = UPPER_JAW_TOP;
            if (Math.abs(y - upperJawBox.bottom) < edgeThresh) selectionId = UPPER_JAW_BOTTOM;
            if (Math.abs(x - upperJawBox.left) < edgeThresh) selectionId = UPPER_JAW_LEFT;
            if (Math.abs(x - upperJawBox.right) < edgeThresh) selectionId = UPPER_JAW_RIGHT;
        }
        if (lowerJawBox.contains((int) x, (int) y)) {
            //selectionId = LOWER_JAW;
            if (Math.abs(y - lowerJawBox.top) < edgeThresh) selectionId = LOWER_JAW_TOP;
            if (Math.abs(y - lowerJawBox.bottom) < edgeThresh) selectionId = LOWER_JAW_BOTTOM;
            if (Math.abs(x - lowerJawBox.left) < edgeThresh) selectionId = LOWER_JAW_LEFT;
            if (Math.abs(x - lowerJawBox.right) < edgeThresh) selectionId = LOWER_JAW_RIGHT;
        }
        if (Math.abs(x - upperJawPivotPoint.x) < pointThresh && Math.abs(y - upperJawPivotPoint.y) < pointThresh)
            selectionId = UPPER_JAW_PIVOT;
        // Rotation handle coordinates haven't been rotated, so compensate
        float[] point = {x, y};
        Matrix rotate = new Matrix();
        rotate.setRotate(rotation, upperJawPivotPoint.x, upperJawPivotPoint.y);
        rotate.mapPoints(point);
        if (Math.abs(point[0] - rotateHandle.x) < pointThresh && Math.abs(point[1] - rotateHandle.y) < pointThresh)
            selectionId = ROTATE_HANDLE;


        //if (Math.abs(x - lowerJawPivotPoint.x) < pointThresh && Math.abs(y - lowerJawPivotPoint.y) < pointThresh)
          //  selectionId = LOWER_JAW_PIVOT;
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
                /*upperJawBox.bottom = upperJawBox.bottom + dy;
                upperJawPivotPoint.y = upperJawPivotPoint.y + dy;
                rotateHandle.y = rotateHandle.y + dy;
                if (pivotsSnapped){
                    lowerJawBox.top = upperJawBox.bottom;
                    //lowerJawPivotPoint.y = upperJawPivotPoint.y;
                }*/
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
                upperJawPivotPoint.offset(x2 - x1, y2 - y1);
                upperJawBox.offset(dx, dy);
                lowerJawBox.offset(dx, dy);
                rotateHandle.offset(dx, dy);
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
                /*lowerJawBox.top = lowerJawBox.top + dy;
                lowerJawPivotPoint.y = lowerJawPivotPoint.y + dy;
                rotateHandle.y = rotateHandle.y + dy;
                if (pivotsSnapped){
                    upperJawBox.bottom = lowerJawBox.top;
                    //upperJawPivotPoint.y = lowerJawPivotPoint.y;
                }*/
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
                upperJawPivotPoint.offset(x2 - x1, y2 - y1);
                upperJawBox.bottom = upperJawPivotPoint.y;
                lowerJawBox.top = upperJawPivotPoint.y;
                lowerJawPivotPoint = upperJawPivotPoint;
                /*lowerJawPivotPoint.offset(dx, 0);
                /*
                if (pivotsSnapped){
                    upperJawPivotPoint.offset(dx, 0);
                }*/
                break;
            case ROTATE_HANDLE:
                float a = x2 - upperJawPivotPoint.x;
                float b = y2 - upperJawPivotPoint.y;
                double rads = Math.atan(b / a);
                float angle = ((float)Math.toDegrees(rads)) % 360;
                if (angle < -180.f) angle += 180.0f;
                if (angle > 180.f) angle -= 180.0f;
                rotation = Math.max(-90, Math.min(prevRotation + angle - prevAngle, 90));
                float[] point;
                if (orientation == Puppet.PROFILE_RIGHT)
                    point = new float[]{upperJawPivotPoint.x + 150, upperJawPivotPoint.y};
                else
                    point = new float[]{upperJawPivotPoint.x - 150, upperJawPivotPoint.y};
                Matrix rotate = new Matrix();
                rotate.setRotate(rotation, upperJawPivotPoint.x, upperJawPivotPoint.y);
                rotate.mapPoints(point);
                rotateHandle.set((int)point[0], (int)point[1]);
                break;

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

    // Pan/Zoom methods
    private void setPanZoomMode(){
        designerMode = MODE_PAN_ZOOM;
    }
    private boolean handlePanZoomTouch(MotionEvent motionEvent){
        scaleGestureDetector.onTouchEvent(motionEvent);
        int pointerCount = motionEvent.getPointerCount();
        float [] point = {motionEvent.getX(), motionEvent.getY()};
        Matrix unZoom = new Matrix(zoomMatrix);
        unZoom.invert(unZoom);
        unZoom.mapPoints(point);
        float x = point[0], y = point[1];
        switch (motionEvent.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                x1Start = x;
                y1Start = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pointerCount > 1) {
                    int dx = (int) (x - x1Start);
                    int dy = (int) (y - y1Start);
                    zoomMatrix.preTranslate(dx, dy);
                } else {
                    zoomMatrix.postTranslate((int) (x - x1Start), (int) (y - y1Start));
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                invalidate();
                return true;
        }
        return true;
    }
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            zoomFactor *= detector.getScaleFactor();
            // Don't let the object get too small or too large.
            zoomFactor = Math.max(1f, Math.min(zoomFactor, 6.0f));
            zoomMatrix.setScale(zoomFactor, zoomFactor, zoomPoint.x, zoomPoint.y);
            invalidate();
            requestLayout();
            return true;
        }
    }

    // Class overrides
    @Override
    public boolean onTouchEvent(MotionEvent event){
        //super.onTouchEvent(event);
        switch (designerMode){
            case MODE_SELECT:
                return handleSelectionTouch(event);
            case MODE_DRAW:
                return handleDrawTouch(event);
            case MODE_DRAW_ERASE:
                return handleDrawTouch(event);
            case MODE_BACKGROUND_ERASE:
                return handleBackgroundEraseTouch(event);
            case MODE_MAGIC_ERASE:
                return handleMagicEraseTouch(event);
            case MODE_CUT_PATH:
                return handleCutPathTouch(event);
            case MODE_HEAL:
                return handleHealTouch(event);
            case MODE_PAN_ZOOM:
                return handlePanZoomTouch(event);
            default:
                return false;
        }
    }

    @Override
    protected void onMeasure(int reqWidth, int reqHeight){
        //setMeasuredDimension((int) Math.min(drawBitmap.getWidth() * zoomFactor, MeasureSpec.getSize(reqWidth)),
        //        (int) Math.min(drawBitmap.getHeight() * zoomFactor, MeasureSpec.getSize(reqHeight)));
        setMeasuredDimension(MeasureSpec.getSize(reqWidth), MeasureSpec.getSize(reqHeight));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, zoomMatrix, null);
        }
        canvas.drawBitmap(drawBitmap, zoomMatrix, null);
        if (showLowerJawBox || showUpperJawBox) {
            zoomMatrix.mapRect(upperBoxDrawRect, upperJawBox);
            zoomMatrix.mapRect(lowerBoxDrawRect, lowerJawBox);
            upperPivotDrawPoint[0] = upperJawPivotPoint.x;
            upperPivotDrawPoint[1] = upperJawPivotPoint.y;
            zoomMatrix.mapPoints(upperPivotDrawPoint);
            rotateHandleDrawPoint[0] = rotateHandle.x;
            rotateHandleDrawPoint[1] = rotateHandle.y;
            zoomMatrix.mapPoints(rotateHandleDrawPoint);
            canvas.save();
            canvas.rotate(rotation, upperPivotDrawPoint[0], upperPivotDrawPoint[1]);
            canvas.drawBitmap(upperProfileBitmap, null, upperBoxDrawRect, null);
            canvas.drawBitmap(lowerProfileBitmap, null, lowerBoxDrawRect, null);
            canvas.drawRect(lowerBoxDrawRect, lowerJawPaint);
            canvas.drawRect(upperBoxDrawRect, upperJawPaint);
            canvas.restore();
            // Draw pivot point
            canvas.drawCircle(upperPivotDrawPoint[0], upperPivotDrawPoint[1], 16, pivotPaint1);
            canvas.drawCircle(upperPivotDrawPoint[0], upperPivotDrawPoint[1], 12, pivotPaint2);
            canvas.drawCircle(upperPivotDrawPoint[0], upperPivotDrawPoint[1], 8, pivotPaint1);
            // Draw pivot handle
            canvas.drawCircle(rotateHandleDrawPoint[0], rotateHandleDrawPoint[1], 16, pivotPaint1);
            canvas.drawCircle(rotateHandleDrawPoint[0], rotateHandleDrawPoint[1], 12, pivotPaint2);
            canvas.drawCircle(rotateHandleDrawPoint[0], rotateHandleDrawPoint[1], 8, pivotPaint1);
            canvas.drawLine(upperPivotDrawPoint[0], upperPivotDrawPoint[1], rotateHandleDrawPoint[0], rotateHandleDrawPoint[1], pivotPaint1);

        }
        if (designerMode.equals(MODE_CUT_PATH)){
            canvas.drawPath(cutPath, cutPathPaint);
        }
    }

}
