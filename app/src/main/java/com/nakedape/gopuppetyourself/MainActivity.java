package com.nakedape.gopuppetyourself;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
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
    private ViewGroup stage;
    private int dx;
    private int dy;
    private Puppet selectedPuppet;
    private ArrayList<Puppet> puppets;
    private boolean isBackstage = false;
    private File storageDir;
    private PuppetShowRecorder showRecorder;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case PuppetShowRecorder.COUNTER_UPDATE:
                    progressBar.setProgress((int)showRecorder.getTimeFromStartMillis());
            }
        }
    };
    private SeekBar progressBar;
    private int nextPuppetId = 0;
    private MainActivityDataFrag savedData;
    private ImageButton bottomLeftButton;
    private RelativeLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        rootLayout = (RelativeLayout)findViewById(R.id.root_layout);
        stage = (RelativeLayout)findViewById(R.id.stage);
        progressBar = (SeekBar)findViewById(R.id.progress_bar);
        bottomLeftButton = (ImageButton)findViewById(R.id.bottom_left_button);
        bottomLeftButton.setOnClickListener(mainControlClickListener);
        bottomLeftButton.setOnLongClickListener(mainControlLongClickListener);

        // Prepare show recorder
        showRecorder = new PuppetShowRecorder(stage);
        showRecorder.setHandler(mHandler);

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

        // Check for a data fragment retained after activity restart
        FragmentManager fm = getFragmentManager();
        savedData = (MainActivityDataFrag) fm.findFragmentByTag("data");
        if (savedData != null){ // Load the data
            stage.setBackground(new BitmapDrawable(getResources(), savedData.currentBackground));
        }
        else { // Create a new instance to save the data
            savedData = new MainActivityDataFrag();
            fm.beginTransaction().add(savedData, "data").commit();
        }

        // Search for puppet files on a separate thread and update UI as they're loaded
        Runnable loadPuppetsThread = new Runnable() {
            @Override
            public void run() {
                File[] files = storageDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.getPath().endsWith(getString(R.string.puppet_extension));
                    }
                });
                if (files.length > 0) {
                    for (File f : files) {
                        final Puppet p = new Puppet(context, null);
                        Utils.ReadPuppetFromFile(p, f);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.setId(nextPuppetId++);
                                p.setOnTouchListener(headTouchListener);
                                if (!p.isOnStage()) p.setVisibility(View.GONE);
                                if (savedData.layoutParamses.size() > 0){
                                    p.setLayoutParams(savedData.layoutParamses.get(p.getId()));
                                }
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
    protected void onResume(){
        super.onResume();
        stage.setBackground(new ColorDrawable(getResources().getColor(R.color.dark_grey)));
        progressBarFadeOut();
        mainControlFadeOut();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isFinishing()){ // Save instance data for activity restart
            // Save puppet positions
            for (int i = 0; i < stage.getChildCount(); i++){
                savedData.layoutParamses.add(stage.getChildAt(i).getLayoutParams());
            }
        }
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

    // Performance Methods
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
        progressBar.setProgress(progressBar.getMax());
    }
    public void PlayClick(View v){
        if (showRecorder != null){
            showRecorder.RecordStop();
            progressBar.setMax(showRecorder.getLength());
            showRecorder.Play();
        }
    }

    // Control Methods
    private View.OnTouchListener mainControlTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()){
                case MotionEvent.ACTION_DOWN:
                    mainControlFadeIn();
                    Log.d(LOG_TAG, "Main control touch down");
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_UP:
                    mainControlFadeOut();
                    return true;
            }
            return false;
        }
    };
    private View.OnClickListener mainControlClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            fadeIn.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mainControlFadeOut();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            bottomLeftButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));
            bottomLeftButton.startAnimation(fadeIn);
        }
    };
    private View.OnLongClickListener mainControlLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            bottomLeftButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));

            ImageButton button = new ImageButton(context);
            button.setBackground(getResources().getDrawable(R.drawable.control_button_background));
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(50, 50);
            params.setMargins(bottomLeftButton.getLeft(), rootLayout.getHeight() - bottomLeftButton.getHeight() - 50 - 50, 0, 0);
            button.setLayoutParams(params);
            rootLayout.addView(button);
            return true;
        }
    };
    private void mainControlFadeOut(){
        Animation fade_out = AnimationUtils.loadAnimation(this, R.anim.anim_pause1000_grow_fade_out);
        fade_out.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                bottomLeftButton.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        bottomLeftButton.startAnimation(fade_out);
    }
    private void progressBarFadeOut(){
        Animation fade_out = AnimationUtils.loadAnimation(this, R.anim.anim_pause1000_fade_out);
        fade_out.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                //progressBar.setBackground(new ColorDrawable(Color.TRANSPARENT));
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        progressBar.startAnimation(fade_out);
    }
    private void mainControlFadeIn(){
        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
        bottomLeftButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));
        bottomLeftButton.startAnimation(fadeIn);
    }

    // Backstage related methods
    public void GoBackstage(View v){
        findViewById(R.id.perform_button_bar).setVisibility(View.GONE);
        findViewById(R.id.backstage_button_bar).setVisibility(View.VISIBLE);
        stage.setOnClickListener(backgroundClickListener);
        isBackstage = true;
        savedData.isBackstage = isBackstage;
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
            savedData.currentBackground = bitmap;
        }
    }

}
