package com.nakedape.gopuppetyourself;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;


public class MainActivity extends ActionBarActivity {

    private static String LOG_TAG = "GoPuppetYourself";
    View head, upperJaw, lowerJaw;
    private ViewGroup stage;
    private int _xDelta;
    private int _yDelta;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        stage = (ViewGroup)findViewById(R.id.stage);
        head = findViewById(R.id.head);
        upperJaw = findViewById(R.id.upper_jaw);
        lowerJaw = findViewById(R.id.lower_jaw);
        head.setOnTouchListener(headTouchListener);
        //upperJaw.setOnTouchListener(headTouchListener);
        //lowerJaw.setOnTouchListener(headTouchListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onStart(){
        super.onStart();
        onResume();
    }
    @Override
    public void onResume(){
        super.onResume();
        upperJaw.setPivotX(upperJaw.getLeft() + 50);
        upperJaw.setPivotY(upperJaw.getBottom());
        //int padding = (upperJaw.getWidth() + upperJaw.getHeight()) / 4;
        //head.setMinimumHeight(upperJaw.getHeight() + padding);
        //head.setMinimumWidth(upperJaw.getWidth() + padding);
        //Log.d(LOG_TAG, "Padding = " + String.valueOf(padding));
        //head.setMinimumWidth(upperJaw.getWidth() * 3 / 2);
        //head.setMinimumHeight(upperJaw.getHeight() + upperJaw.getWidth() / 2 + lowerJaw.getHeight());
    }

    private View.OnTouchListener headTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            final int X = (int) event.getRawX();
            final int Y = (int) event.getRawY();
            switch (event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) head.getLayoutParams();
                    _xDelta = X - lParams.leftMargin;
                    _yDelta = Y - lParams.topMargin;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() > 1) {
                        moveMouth(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                        moveHead(X, Y);
                    }
                    else {
                        moveHead(X, Y);
                        upperJaw.setRotation(0);
                        Log.d(LOG_TAG, "Raw x, y = " + String.valueOf(event.getRawX()) + ", " + String.valueOf(event.getRawY()));
                        Log.d(LOG_TAG, "x, y = " + String.valueOf(event.getX()) + ", " + String.valueOf(event.getY()));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    upperJaw.setRotation(0);
                    return true;
                default:
                    return false;
            }
        }
    };
    private void moveMouth(float X0, float Y0, float X1, float Y1){
        double width = Math.abs(Y1 - Y0);
        if (width < 300)
            upperJaw.setRotation(-15);
        else
            upperJaw.setRotation(-30);
        stage.invalidate();
        Log.d("Rotate", String.valueOf(width));
    }
    private void moveHead(float X, float Y){
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) head
                .getLayoutParams();
        layoutParams.leftMargin = (int)(X - _xDelta);
        layoutParams.topMargin = (int)(Y - _yDelta);
        layoutParams.rightMargin = -250;
        layoutParams.bottomMargin = -250;
        head.setLayoutParams(layoutParams);
        stage.invalidate();
    }
}
