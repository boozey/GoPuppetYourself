package com.nakedape.gopuppetyourself;

import java.io.Serializable;

/**
 * Created by Nathan on 6/12/2015.
 */
public class PuppetData implements Serializable{

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public String getUpperJawBitmapPath() {
        return upperJawBitmapPath;
    }

    public void setUpperJawBitmapPath(String upperJawBitmapPath) {
        this.upperJawBitmapPath = upperJawBitmapPath;
    }

    public String getLowerJawBitmapPath() {
        return lowerJawBitmapPath;
    }

    public void setLowerJawBitmapPath(String lowerJawBitmapPath) {
        this.lowerJawBitmapPath = lowerJawBitmapPath;
    }

    public int getUpperLeftPadding() {
        return upperLeftPadding;
    }

    public void setUpperLeftPadding(int upperLeftPadding) {
        this.upperLeftPadding = upperLeftPadding;
    }

    public int getUpperRightPadding() {
        return upperRightPadding;
    }

    public void setUpperRightPadding(int upperRightPadding) {
        this.upperRightPadding = upperRightPadding;
    }

    public int getLowerLeftPadding() {
        return lowerLeftPadding;
    }

    public void setLowerLeftPadding(int lowerLeftPadding) {
        this.lowerLeftPadding = lowerLeftPadding;
    }

    public int getLowerRightPadding() {
        return lowerRightPadding;
    }

    public void setLowerRightPadding(int lowerRightPadding) {
        this.lowerRightPadding = lowerRightPadding;
    }

    private int upperLeftPadding = 0, upperRightPadding = 0, lowerLeftPadding = 0, lowerRightPadding = 0;

    public int getUpperPivotPointx() {
        return upperPivotPointx;
    }

    public void setUpperPivotPointx(int upperPivotPointx) {
        this.upperPivotPointx = upperPivotPointx;
    }

    public int getUpperPivotPointy() {
        return upperPivotPointy;
    }

    public void setUpperPivotPointy(int upperPivotPointy) {
        this.upperPivotPointy = upperPivotPointy;
    }

    public int getLowerPivotPointx() {
        return lowerPivotPointx;
    }

    public void setLowerPivotPointx(int lowerPivotPointx) {
        this.lowerPivotPointx = lowerPivotPointx;
    }

    public int getLowerPivotPointy() {
        return lowerPivotPointy;
    }

    public void setLowerPivotPointy(int lowerPivotPointy) {
        this.lowerPivotPointy = lowerPivotPointy;
    }

    private int upperPivotPointx, upperPivotPointy, lowerPivotPointx, lowerPivotPointy;
    private int orientation = 0;
    private String upperJawBitmapPath, lowerJawBitmapPath;

    public PuppetData(){}
}
