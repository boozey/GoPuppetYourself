package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Handler;
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
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.Utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private static String LOG_TAG = "GoPuppetYourself";
    public static final String PUPPET_INDEX = "com.nakedape.gopuppetyourself.PUPPET_INDEX";
    public static final String PUPPET_PATH = "com.nakedape.gopuppetyourself.PUPPET_PATH";
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
    private File storageDir;
    private PuppetShowRecorder showRecorder;
    private final Handler mHandler = new Handler();
    private int nextPuppetId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        stage = (RelativeLayout)findViewById(R.id.stage);
        showRecorder = new PuppetShowRecorder(stage);
        showRecorder.setHandler(mHandler);
        /*
        selectedPuppet = (Puppet)findViewById(R.id.puppet);
        upperJaw = (ImageView)findViewById(R.id.upper_jaw);
        selectedPuppet.setUpperJawImage(upperJaw);
        lowerJaw = (ImageView)findViewById(R.id.lower_jaw);
        selectedPuppet.setLowerJawImage(lowerJaw);
        selectedPuppet.setOnTouchListener(headTouchListener);
        */
        // Prepare puppet storage directory for access
        if (Utils.isExternalStorageWritable()){
            storageDir = new File(getExternalFilesDir(null), getResources().getString(R.string.puppet_directory));
            if (!storageDir.exists())
                if (!storageDir.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
            Log.d(LOG_TAG, "Using external files directory");
        }
        else {
            storageDir = new File(getFilesDir(), getResources().getString(R.string.puppet_directory));
            if (!storageDir.exists())
                if (!storageDir.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
            Log.d(LOG_TAG, "Using internal files directory");
        }
        // Load any puppet files found on a separate thread and update UI
        Runnable loadPuppetsThread = new Runnable() {
            @Override
            public void run() {
                File[] files = storageDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        if (file.getPath().endsWith(getString(R.string.puppet_extension)))
                            return  true;
                        else
                            return false;
                    }
                });
                if (files.length > 0) {
                    for (File f : files) {
                        final Puppet p = new Puppet(context, null);
                        Utils.ReadPuppetFromFile(p, f);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.setOnTouchListener(headTouchListener);
                                p.setId(nextPuppetId++);
                                stage.addView(p);
                            }
                        });
                    }
                }
            }
        };
        new Thread(loadPuppetsThread).start();
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
        else if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK){
            SetupNewPuppet(data);
        }
    }

    private View.OnTouchListener headTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            return HandlePerformanceTouch((Puppet)view, event);
        }
    };
    private boolean HandlePerformanceTouch(Puppet view, MotionEvent event){
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
                    if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getId(), KeyFrame.CLOSE_MOUTH);
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
            if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getId(), KeyFrame.OPEN_MOUTH_NARROW);
        }
        else {
            puppet.upperJaw.setRotation(30 * puppet.getPivotDirection());
            if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getId(), KeyFrame.OPEN_MOUTH_MED);
        }
    }
    private void moveView(Puppet puppet, int X, int Y){
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) puppet
                .getLayoutParams();
        layoutParams.leftMargin = X - dx;
        layoutParams.topMargin = Y - dy;
        layoutParams.rightMargin = -250;
        layoutParams.bottomMargin = -250;
        if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getId(), KeyFrame.MOVEMENT, layoutParams.leftMargin, layoutParams.topMargin);
        puppet.setLayoutParams(layoutParams);
    }
    public void RecordClick(View v){
        if (showRecorder == null){
            showRecorder = new PuppetShowRecorder(stage);
        }
        showRecorder.RecordStart();
    }
    public void PlayClick(View v){
        if (showRecorder != null){
            showRecorder.RecordStop();
            showRecorder.Play();
        }
    }


    // Backstage related methods
    public void GoBackstage(View v){
        findViewById(R.id.perform_button_bar).setVisibility(View.GONE);
        findViewById(R.id.backstage_button_bar).setVisibility(View.VISIBLE);
        stage.setOnClickListener(backgroundClickListener);
        isBackstage = true;
        for (int i = 0; i < stage.getChildCount(); i++){
            stage.getChildAt(i).setOnTouchListener(backstageListener);
            stage.getChildAt(i).setVisibility(View.VISIBLE);
        }
    }
    private View.OnTouchListener backstageListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            return HandleBackstageTouch((Puppet)view, event);
        }
    };
    private View.OnClickListener backgroundClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (selectedPuppet != null) selectedPuppet.setBackground(null);
            selectedPuppet = null;
        }
    };
    private boolean HandleBackstageTouch(Puppet view, MotionEvent event){
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
                if (selectedPuppet != null) selectedPuppet.setBackground(null);
                selectedPuppet = (Puppet)view;
                selectedPuppet.setBackground(getResources().getDrawable(R.drawable.selected_puppet));
                PopupMenu menu = new PopupMenu(context, selectedPuppet);
                menu.inflate(R.menu.menu_puppet_edit);
                menu.setOnMenuItemClickListener(popupMenuListener);
                if (!selectedPuppet.isOnStage()){
                    MenuItem item = menu.getMenu().findItem(R.id.action_puppet_onstage);
                    item.setChecked(false);
                }
                menu.show();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                moveView(view, X, Y);
                break;
        }
        stage.invalidate();
        return true;
    }
    private PopupMenu.OnMenuItemClickListener popupMenuListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            switch (menuItem.getItemId()){
                case R.id.action_edit_puppet:
                    EditPuppet(selectedPuppet);
                    return true;
                case R.id.action_clone_puppet:
                    return true;
                case R.id.action_delete_puppet:
                    DeletePuppet(selectedPuppet);
                    return true;
                case R.id.action_puppet_onstage:
                    if (menuItem.isChecked()){
                        selectedPuppet.setOnStage(false);
                        menuItem.setChecked(false);
                    }
                    else {
                        selectedPuppet.setOnStage(true);
                        menuItem.setChecked(true);
                    }
            }
            return false;
        }
    };
    public void GoToPerformance(View v){
        findViewById(R.id.backstage_button_bar).setVisibility(View.GONE);
        findViewById(R.id.perform_button_bar).setVisibility(View.VISIBLE);
        stage.setOnClickListener(null);
        if (selectedPuppet != null) selectedPuppet.setBackground(null);
        isBackstage = false;
        Puppet p;
        for (int i = 0; i < stage.getChildCount(); i++){
            p = (Puppet)stage.getChildAt(i);
            if (p.isOnStage()) {
                p.setOnClickListener(null);
                p.setOnLongClickListener(null);
                p.setOnTouchListener(headTouchListener);
            }
            else {
                p.setVisibility(View.GONE);
            }

        }
    }
    public void NewButtonClick(View v){
        Intent intent = new Intent(this, DesignerActivity.class);
        startActivityForResult(intent, REQUEST_PUPPET_GET);
    }
    public void EditPuppet(Puppet p){
        Intent intent = new Intent(this, DesignerActivity.class);
        for (int i = 0; i < stage.getChildCount(); i++){
            if (stage.getChildAt(i).equals(p)) {
                intent.putExtra(PUPPET_INDEX, i);
                Log.d(LOG_TAG, "index = " + i);
            }
        }
        intent.putExtra(PUPPET_PATH, p.getPath());
        startActivityForResult(intent, REQUEST_EDIT);
    }
    private void DeletePuppet(Puppet p){
        stage.removeView(p);
        File puppetFile = new File(p.getPath());
        if (puppetFile.isFile())
            if (!puppetFile.delete()) Log.e(LOG_TAG, "error deleting puppet file");
    }
    private void SetupNewPuppet(Intent data){
        String filePath = data.getStringExtra(PUPPET_PATH);
        int index = data.getIntExtra(PUPPET_INDEX, -1);
        if (index > -1) stage.removeViewAt(index);
        Log.d(LOG_TAG, "index = " + index);
        Puppet puppet = new Puppet(context, null);
        Utils.ReadPuppetFromFile(puppet, new File(filePath));
        puppet.setOnTouchListener(backstageListener);
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
