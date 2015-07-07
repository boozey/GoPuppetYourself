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
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.Utils;

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
    public static final String MODE_NO_TOUCH = "no_touch";
    public String designerMode = MODE_SELECT;

    // Undo id strings
    private static final String UNDO_BACKGROUND = "background";
    private static final String UNDO_DRAW = "draw";

    private Context context;
    private Bitmap backgroundBitmap, backgroundOriginal, drawBitmap, viewBitmap;
    private File cacheDir;
    private ArrayList<Bitmap> backgroundUndoStack;
    private ArrayList<Bitmap> drawUndoStack;
    private ArrayList<String> undoStack;
    private Rect upperJawBox, lowerJawBox;
    private Point upperJawPivotPoint, lowerJawPivotPoint;
    private Paint upperJawPaint, upperTextPaint, lowerJawPaint, lowerTextPaint, pivotPaint1, pivotPaint2, drawPaint;
    private int drawColor;
    private Path drawPath;
    private Canvas drawCanvas, backgroundCanvas, viewCanvas;
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


    private int orientation = Puppet.PROFILE_RIGHT;

    public PuppetDesigner(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        // Setup initial paint properties
        lowerJawPaint = new Paint();
        lowerJawPaint.setARGB(64, 0, 128, 0);
        upperJawPaint = new Paint();
        upperJawPaint.setARGB(64, 128, 0, 0);
        upperTextPaint = new Paint();
        upperTextPaint.setColor(Color.RED);
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

        // Setup view with tiled background
        viewBitmap = Bitmap.createBitmap(SMALL_WIDTH, SMALL_HEIGHT, Bitmap.Config.ARGB_8888);
        viewCanvas = new Canvas(viewBitmap);
        viewCanvas.drawColor(Color.WHITE, PorterDuff.Mode.ADD);
        Bitmap tileBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.transparency_tile_16x16);
        BitmapDrawable tile = new BitmapDrawable(getResources(), tileBitmap);
        tile.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        tile.setBounds(viewCanvas.getClipBounds());
        tile.draw(viewCanvas);

        // Setup blank drawing canvas
        drawBitmap = Bitmap.createBitmap(SMALL_WIDTH, SMALL_HEIGHT, Bitmap.Config.ARGB_8888);
        drawBitmap.setHasAlpha(true);
        drawCanvas = new Canvas(drawBitmap);
        drawPath = new Path();

        // Set initial positions of jaw boxes
        upperJawBox = new Rect(40, 40, viewBitmap.getWidth() - 40, viewBitmap.getHeight() / 2);
        lowerJawBox = new Rect(40, viewBitmap.getHeight() / 2, viewBitmap.getWidth() - 40, viewBitmap.getHeight() - 40);
        upperJawPivotPoint= new Point(viewBitmap.getWidth() / 2, viewBitmap.getHeight() / 2);
        lowerJawPivotPoint = upperJawPivotPoint;

        // Prepare cache directory for access
        if (Utils.isExternalStorageWritable()){
            cacheDir = context.getExternalCacheDir();
        }
        else {
            cacheDir = context.getCacheDir();
        }
        backgroundUndoStack = new ArrayList<>(5);
        drawUndoStack = new ArrayList<>();
        undoStack = new ArrayList<>();
    }
    public void release(){
        viewBitmap = null;
        backgroundOriginal = null;
        backgroundBitmap = null;
        backgroundUndoStack = null;
        drawUndoStack = null;
    }

    public String getMode(){
        return designerMode;
    }
    public void setMode(String mode){
        designerMode = mode;
    }
    public boolean isSaved(){
        return isSaved;
    }

    public int getOrientation() {
        return orientation;
    }
    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }
    public void setShowUpperJawBox(boolean showUpperJawBox){
        this.showUpperJawBox = showUpperJawBox;
        invalidate();
    }
    public void setShowLowerJawBox(boolean showLowerJawBox){
        this.showLowerJawBox = showLowerJawBox;
        invalidate();
    }

    // Undo methods
    private void addBackgroundUndo(){
        if (backgroundBitmap != null) {
            undoStack.add(UNDO_BACKGROUND);
            backgroundUndoStack.add(backgroundBitmap.copy(backgroundBitmap.getConfig(), true));
            Runtime runtime = Runtime.getRuntime();
            if (3 * backgroundBitmap.getByteCount() + runtime.totalMemory() >= runtime.maxMemory()) {
                backgroundUndoStack.remove(0);
                Log.d(LOG_TAG, "Memory low, removing undo level");
            }
            Log.d(LOG_TAG, "add undo, undo stack size: " + backgroundUndoStack.size());
        }
    }
    private void addDrawUndo(){
        undoStack.add(UNDO_DRAW);
        drawUndoStack.add(drawBitmap.copy(drawBitmap.getConfig(), true));
        Runtime runtime = Runtime.getRuntime();
        if (3 * drawBitmap.getByteCount() + runtime.totalMemory() >= runtime.maxMemory()) {
            drawUndoStack.remove(0);
            Log.d(LOG_TAG, "Memory low, removing undo level");
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
        int x = (int)event.getX(), y = (int)event.getY();
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
        float x = event.getX(), y = event.getY();
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                startCutPathFlow();
                cutPath.moveTo(x, y);
                cutPathPoints.add(new Point((int)x, (int)y));
                return true;
            case MotionEvent.ACTION_MOVE:
                cutPath.lineTo(x, y);
                cutPathPoints.add(new Point((int) x, (int) y));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                isSaved = false;
                cutPath.lineTo(x, y);
                cutPathPoints.add(new Point((int) x, (int) y));
                cutPath.lineTo(cutPathPoints.get(0).x, cutPathPoints.get(0).y);
                cutPath.setLastPoint(cutPathPoints.get(0).x, cutPathPoints.get(0).y);
                invalidate();
                addBackgroundUndo();
                Log.d(LOG_TAG, "Number of cut path points = " + cutPathPoints.size());
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
        Log.d(LOG_TAG, "Left loop rN = " + rN);
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
        Log.d(LOG_TAG, "Right loop rN = " + rN);
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
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                addBackgroundUndo();
                magicErase((int)event.getX(), (int)event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                magicErase((int)event.getX(),(int)event.getY());
                return true;
            default:
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
        Log.d(LOG_TAG, "Magic Erase Threshold = " + threshold);
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
                if (!keepSearchingy && yN == y) {
                    keepSearchingx = false;
                }
            }

            keepSearchingy = true;
            for (yN = y - 1; yN >= 0 && keepSearchingy && keepSearchingx; yN--) { // Search up, start at y - 1 since y has been checked
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
                    rgbPrevX = backgroundBitmap.getPixel(prevX, yN);
                    backgroundBitmap.setPixel(xN, yN, Color.TRANSPARENT);
                    if (rgbPrevX != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbPrevX, threshold))
                        backgroundBitmap.setPixel(prevX, yN, Color.TRANSPARENT);
                }
                else keepSearchingy = false;
                if (!keepSearchingy && yN == y){
                    keepSearchingx = false;
                }
            }
            keepSearchingy = true;
            for (yN = y - 1; yN >= 0 && keepSearchingy && keepSearchingx; yN--) {
                rgbCurrent = backgroundBitmap.getPixel(xN, yN);
                if (rgbCurrent != Color.TRANSPARENT && areColorsSimilar(rgbInit, rgbCurrent, threshold)){
                    rgbPrevX = backgroundBitmap.getPixel(prevX, yN);
                    backgroundBitmap.setPixel(xN, yN, Color.TRANSPARENT);
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
        Log.d(LOG_TAG, "Color similarity: " + colorSimilarity);
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
    }
    public boolean isDrawMode() {
        return isDrawMode;
    }
    private boolean handleDrawTouch(MotionEvent event){
        float x = event.getX(), y = event.getY();
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
        float x = event.getX(), y = event.getY();
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

    public void flipHorz(){
        if (backgroundBitmap != null) {
            Matrix m = new Matrix();
            m.preScale(-1, 1);
            Bitmap dst = Bitmap.createBitmap(backgroundBitmap, 0, 0, backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), m, false);
            backgroundBitmap = dst;
            backgroundCanvas = new Canvas(backgroundBitmap);
            invalidate();
            //dst.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        }
    }

    // Methods for getting and setting the puppet
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
    public Bitmap getUpperJaw(){
        Bitmap bmOverlay = Bitmap.createBitmap(drawBitmap.getWidth(), drawBitmap.getHeight(), drawBitmap.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        if (backgroundBitmap != null)
            canvas.drawBitmap(backgroundBitmap, new Matrix(), null);
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

        if (width + left > drawBitmap.getWidth()) width = drawBitmap.getWidth() - left;
        if (height + top > drawBitmap.getHeight()) height = drawBitmap.getHeight() - top;
        upperJaw = Bitmap.createBitmap(bmOverlay, left, top, width, height);
        return upperJaw.copy(Bitmap.Config.ARGB_8888, true);
    }
    public Bitmap getLowerJaw(){
        Bitmap bmOverlay = Bitmap.createBitmap(drawBitmap.getWidth(), drawBitmap.getHeight(), drawBitmap.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        if (backgroundBitmap != null)
            canvas.drawBitmap(backgroundBitmap, new Matrix(), null);
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
        if (width + left > drawBitmap.getWidth()) width = drawBitmap.getWidth() - left;
        if (height + top > drawBitmap.getHeight()) height = drawBitmap.getHeight() - top;
        lowerJaw = Bitmap.createBitmap(bmOverlay, left, top, width, height);
        return lowerJaw.copy(Bitmap.Config.ARGB_8888, true);
    }
    public void SetNewImage(Bitmap image) {
        backgroundBitmap = image.copy(Bitmap.Config.ARGB_8888, true);
        backgroundBitmap.setHasAlpha(true);
        backgroundCanvas = new Canvas(backgroundBitmap);
        backgroundOriginal = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true);
        drawBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        drawBitmap.setHasAlpha(true);
        drawCanvas = new Canvas(drawBitmap);
        viewBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        viewCanvas = new Canvas(viewBitmap);
        viewCanvas.drawColor(Color.WHITE, PorterDuff.Mode.ADD);
        Bitmap tileBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.transparency_tile_16x16);
        BitmapDrawable tile = new BitmapDrawable(getResources(), tileBitmap);
        tile.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        tile.setBounds(viewCanvas.getClipBounds());
        tile.draw(viewCanvas);
        Log.d(LOG_TAG, "Image width, height = " + String.valueOf(image.getWidth()) + ", " + String.valueOf(image.getHeight()));
        Log.d(LOG_TAG, "View width, height = " + String.valueOf(getWidth()) + ", " + String.valueOf(getHeight()));
        int padding = (int)(Math.min(image.getHeight(), image.getWidth()) * 0.1);
        upperJawBox = new Rect(padding, padding, image.getWidth() - padding, image.getHeight() / 2);
        upperJawPivotPoint = new Point(image.getWidth() / 2, upperJawBox.bottom);
        lowerJawBox = new Rect(padding, image.getHeight() / 2, image.getWidth() - padding, image.getHeight() - padding);
        //lowerJawPivotPoint = new Point(lowerJawBox.left + lowerJawBox.width() / 2, lowerJawBox.top);
        lowerJawPivotPoint = upperJawPivotPoint;
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

    // Selection mode methods
    public void setSelectionMode(boolean isSelectionMode){
        designerMode = MODE_SELECT;
    }
    private boolean handleSelectionTouch(MotionEvent event){
        float x = event.getX(), y = event.getY();
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
                } else {
                    isSaved = false;
                    moveSelection((int) prevX, (int) prevY, (int) event.getX(), (int) event.getY());
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
        if (Math.abs(x - upperJawPivotPoint.x) < pointThresh && Math.abs(y - upperJawPivotPoint.y) < pointThresh)
            selectionId = UPPER_JAW_PIVOT;
        if (lowerJawBox.contains((int) x, (int) y)) {
            //selectionId = LOWER_JAW;
            if (Math.abs(y - lowerJawBox.top) < edgeThresh) selectionId = LOWER_JAW_TOP;
            if (Math.abs(y - lowerJawBox.bottom) < edgeThresh) selectionId = LOWER_JAW_BOTTOM;
            if (Math.abs(x - lowerJawBox.left) < edgeThresh) selectionId = LOWER_JAW_LEFT;
            if (Math.abs(x - lowerJawBox.right) < edgeThresh) selectionId = LOWER_JAW_RIGHT;
        }
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
                upperJawPivotPoint.offset(x2 - x1, y2 - y1);
                upperJawBox.bottom = upperJawPivotPoint.y;
                lowerJawBox.top = upperJawPivotPoint.y;
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
                upperJawPivotPoint.offset(x2 - x1, y2 - y1);
                upperJawBox.bottom = upperJawPivotPoint.y;
                lowerJawBox.top = upperJawPivotPoint.y;
                lowerJawPivotPoint = upperJawPivotPoint;
                /*lowerJawPivotPoint.offset(dx, 0);
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
            default:
                return false;
        }
    }

    @Override
    protected void onMeasure(int reqWidth, int reqHeight){

        setMeasuredDimension(viewBitmap.getWidth(), viewBitmap.getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(viewBitmap, 0, 0, null);
        if (backgroundBitmap != null)
            canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        canvas.drawBitmap(drawBitmap, 0, 0, null);
        if (showLowerJawBox) {
            canvas.drawRect(lowerJawBox, lowerJawPaint);
            //canvas.drawCircle(lowerJawPivotPoint.x, lowerJawPivotPoint.y, 16, pivotPaint1);
            //canvas.drawCircle(lowerJawPivotPoint.x, lowerJawPivotPoint.y, 12, pivotPaint2);
            //canvas.drawCircle(lowerJawPivotPoint.x, lowerJawPivotPoint.y, 8, pivotPaint1);
        }
        if (showUpperJawBox) {
            canvas.drawRect(upperJawBox, upperJawPaint);
            canvas.drawCircle(upperJawPivotPoint.x, upperJawPivotPoint.y, 16, pivotPaint1);
            canvas.drawCircle(upperJawPivotPoint.x, upperJawPivotPoint.y, 12, pivotPaint2);
            canvas.drawCircle(upperJawPivotPoint.x, upperJawPivotPoint.y, 8, pivotPaint1);
        }
        if (designerMode.equals(MODE_CUT_PATH)){
            canvas.drawPath(cutPath, cutPathPaint);
        }
    }
}
