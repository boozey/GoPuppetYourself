package com.nakedape.gopuppetyourself;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;

import java.util.ArrayList;

/**
 * Created by Nathan on 8/9/2015.
 */
public class DesignerActivityDataFrag extends Fragment {

    Bitmap backgroundBitmap, backgroundOriginal, drawBitmap;
    ArrayList<Bitmap> backgroundUndoStack;
    ArrayList<Bitmap> drawUndoStack;
    ArrayList<String> undoStack;
    RectF upperJawBox, lowerJawBox;
    Point upperJawPivotPoint, lowerJawPivotPoint;
    int drawColor;
    float rotation;
    int orientation;
    Point zoomPoint;
    Matrix zoomMatrix;
    float zoomFactor;
    boolean isSaved;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }
}
