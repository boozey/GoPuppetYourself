package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private static String LOG_TAG = "GoPuppetYourself";
    private static final int REQUEST_PUPPET_GET = 4001;
    private static final int REQUEST_IMAGE_GET = 4002;
    private Context context;
    ImageView upperJaw, lowerJaw;
    private ViewGroup stage;
    private int _xDelta;
    private int _yDelta;
    private Puppet puppet1;
    private ArrayList<Puppet> puppets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        stage = (RelativeLayout)findViewById(R.id.stage);
        puppet1 = (Puppet)findViewById(R.id.puppet);
        upperJaw = (ImageView)findViewById(R.id.upper_jaw);
        puppet1.setUpperJawImage(upperJaw);
        lowerJaw = (ImageView)findViewById(R.id.lower_jaw);
        puppet1.setLowerJawImage(lowerJaw);
        puppet1.setOnTouchListener(headTouchListener);

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
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PUPPET_GET && resultCode == RESULT_OK) {
            SetupNewPuppet(data);
        }
        else if (requestCode == REQUEST_IMAGE_GET && resultCode == RESULT_OK){
            Uri imageUri = data.getData();
            setBackGround(imageUri);
        }
    }


    private View.OnTouchListener headTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            Puppet puppet = (Puppet)view;
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
                    if (event.getPointerCount() > 1){
                        moveMouth(puppet, event.getY(0), event.getY(1));
                    }
                    else {
                        puppet.upperJaw.setRotation(0);
                    }
                    moveView(view, X, Y);
                    break;
            }
            stage.invalidate();
            return true;
        }
    };
    private void moveMouth(Puppet puppet, float Y0, float Y1){
        double width = Math.abs(Y1 - Y0);
        if (width < 300) {
            puppet.upperJaw.setRotation(15 * puppet.getPivotDirection());
        }
        else {
            puppet.upperJaw.setRotation(30 * puppet.getPivotDirection());
        }
    }
    private void moveView(View view, int X, int Y){
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view
                .getLayoutParams();
        layoutParams.leftMargin = X - _xDelta;
        layoutParams.topMargin = Y - _yDelta;
        layoutParams.rightMargin = -250;
        layoutParams.bottomMargin = -250;
        view.setLayoutParams(layoutParams);
    }

    public void NewButtonClick(View v){
        Intent intent = new Intent(this, DesignerActivity.class);
        startActivityForResult(intent, REQUEST_PUPPET_GET);
    }
    private void SetupNewPuppet(Intent data){
        Point upperPivot = data.getParcelableExtra(DesignerActivity.UPPER_PIVOT);
        Point lowerPivot = data.getParcelableExtra(DesignerActivity.LOWER_PIVOT);
        String imagePath = data.getStringExtra(DesignerActivity.UPPER_JAW);
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        Bitmap upperJawBitmap = BitmapFactory.decodeFile(imagePath, bmOptions);
        imagePath = data.getStringExtra(DesignerActivity.LOWER_JAW);
        Bitmap lowerJawBitmap = BitmapFactory.decodeFile(imagePath, bmOptions);
        Puppet puppet = new Puppet(context, null);
        puppet.setImages(upperJawBitmap, lowerJawBitmap, upperPivot, lowerPivot);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        puppet.setLayoutParams(params);
        puppet.setOrientation(data.getIntExtra(DesignerActivity.PROFILE_ORIENTATION, 0));
        puppet.setOnTouchListener(headTouchListener);
        stage.addView(puppet);
        Log.d(LOG_TAG, "new puppet should be visible");
    }
    public void BackGroundButtonClick(View v){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_GET);
        }
    }
    private void setBackGround(Uri imageUri){
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
        } catch (IOException e){Toast.makeText(this, "Error loading backgournd", Toast.LENGTH_SHORT).show();}
        if (bitmap != null){
            stage.setBackground(new BitmapDrawable(getResources(), bitmap));
        }
    }
}
