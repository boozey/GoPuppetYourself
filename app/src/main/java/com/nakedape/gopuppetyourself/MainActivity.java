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

    private View.OnTouchListener headTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() > 1) {
                        moveMouth(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                    }
                    else{
                        moveHead(event.getRawX(), event.getRawY());
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
        upperJaw.setPivotX(upperJaw.getLeft());
        upperJaw.setPivotY(upperJaw.getBottom());
        double width = Math.abs(Y1 - Y0);
        if (width < 200)
            upperJaw.setRotation(0);
        else if (width < 300)
            upperJaw.setRotation(-15);
        else
            upperJaw.setRotation(-30);
        stage.invalidate();
        Log.d("Rotate", String.valueOf(width));
    }
    private void moveHead(float X, float Y){
        head.setX(X - head.getWidth() / 2);
        head.setY(Y - (head.getHeight()));
    }
    /*private View.OnTouchListener headTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            final int X = (int) event.getRawX();
            final int Y = (int) event.getRawY();
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                    _xDelta = X - lParams.leftMargin;
                    _yDelta = Y - lParams.topMargin;
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    break;
                case MotionEvent.ACTION_MOVE:
                    RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view
                            .getLayoutParams();
                    layoutParams.leftMargin = X - _xDelta;
                    layoutParams.topMargin = Y - _yDelta;
                    layoutParams.rightMargin = -250;
                    layoutParams.bottomMargin = -250;
                    view.setLayoutParams(layoutParams);
                    break;
            }
            stage.invalidate();
            return true;
        }
    };*/
}
