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

import com.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private static String LOG_TAG = "GoPuppetYourself";
    public static final String PUPPET_INDEX = "com.nakedape.gopuppetyourself.PUPPET_INDEX";
    public static final String PUPPET_CACHE_PATH = "com.nakedape.gopuppetyourself.PUPPET_CACHE_PATH";
    private static final int REQUEST_PUPPET_GET = 4001;
    private static final int REQUEST_IMAGE_GET = 4002;
    private static final int REQUEST_EDIT = 4003;
    private Context context;
    ImageView upperJaw, lowerJaw;
    private ViewGroup stage;
    private int dx;
    private int dy;
    private Puppet selectedPuppet;
    private ArrayList<Puppet> puppets;
    private boolean isBackstage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        stage = (RelativeLayout)findViewById(R.id.stage);
        selectedPuppet = (Puppet)findViewById(R.id.puppet);
        upperJaw = (ImageView)findViewById(R.id.upper_jaw);
        selectedPuppet.setUpperJawImage(upperJaw);
        lowerJaw = (ImageView)findViewById(R.id.lower_jaw);
        selectedPuppet.setLowerJawImage(lowerJaw);
        selectedPuppet.setOnTouchListener(headTouchListener);
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

    private View.OnTouchListener backstageListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            return HandleBackstageTouch(view, event);
        }
    };

    private View.OnTouchListener headTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            return HandlePerformanceTouch(view, event);
        }
    };
    private boolean HandlePerformanceTouch(View view, MotionEvent event){
        Puppet puppet = (Puppet)view;
        final int X = (int) event.getRawX();
        final int Y = (int) event.getRawY();
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                dx = X - lParams.leftMargin;
                dy = Y - lParams.topMargin;
                break;
            case MotionEvent.ACTION_UP:
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() > 1) {
                    moveMouth(puppet, event.getY(0), event.getY(1));
                } else {
                    puppet.upperJaw.setRotation(0);
                }
                moveView(view, X, Y);
                break;
        }
        stage.invalidate();
        return true;
    }
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
        layoutParams.leftMargin = X - dx;
        layoutParams.topMargin = Y - dy;
        layoutParams.rightMargin = -250;
        layoutParams.bottomMargin = -250;
        view.setLayoutParams(layoutParams);
    }

    public void GoBackstage(View v){
        findViewById(R.id.perform_button_bar).setVisibility(View.GONE);
        findViewById(R.id.backstage_button_bar).setVisibility(View.VISIBLE);
        stage.setOnClickListener(backgroundClickListener);
        isBackstage = true;
        for (int i = 0; i < stage.getChildCount(); i++){
            stage.getChildAt(i).setOnTouchListener(null);
            stage.getChildAt(i).setOnClickListener(puppetClickListener);
            stage.getChildAt(i).setOnLongClickListener(puppetLongClickListener);
        }
    }
    private View.OnClickListener puppetClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            selectedPuppet.setBackground(null);
            selectedPuppet = (Puppet)view;
            selectedPuppet.setBackground(getResources().getDrawable(R.drawable.selected_puppet));
        }
    };
    private View.OnLongClickListener puppetLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            selectedPuppet.setBackground(null);
            selectedPuppet = (Puppet)view;
            selectedPuppet.setBackground(getResources().getDrawable(R.drawable.selected_puppet));
            EditPuppet(selectedPuppet);
            return false;
        }
    };
    private View.OnClickListener backgroundClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            selectedPuppet.setBackground(null);
            selectedPuppet = null;
        }
    };
    private boolean HandleBackstageTouch(View view, MotionEvent event){
        Puppet puppet = (Puppet)view;
        final int X = (int) event.getRawX();
        final int Y = (int) event.getRawY();
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_UP:
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                break;
        }
        stage.invalidate();
        return true;
    }
    public void GoToPerformance(View v){
        findViewById(R.id.backstage_button_bar).setVisibility(View.GONE);
        findViewById(R.id.perform_button_bar).setVisibility(View.VISIBLE);
        stage.setOnClickListener(null);
        if (selectedPuppet != null) selectedPuppet.setBackground(null);
        isBackstage = false;
        for (int i = 0; i < stage.getChildCount(); i++){
            stage.getChildAt(i).setOnClickListener(null);
            stage.getChildAt(i).setOnLongClickListener(null);
            stage.getChildAt(i).setOnTouchListener(headTouchListener);
        }
    }
    public void NewButtonClick(View v){
        Intent intent = new Intent(this, DesignerActivity.class);
        startActivityForResult(intent, REQUEST_PUPPET_GET);
    }
    public void EditPuppet(View v){
        Intent intent = new Intent(this, DesignerActivity.class);
        for (int i = 0; i < stage.getChildCount(); i++){
            if (stage.getChildAt(i).equals(v))
                intent.putExtra(PUPPET_INDEX, i);
        }
        intent.putExtra(PUPPET_CACHE_PATH, Utils.WritePuppetToFile(((Puppet) v), getCacheDir().getAbsolutePath()));
        startActivityForResult(intent, REQUEST_EDIT);
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
