package com.nakedape.gopuppetyourself;

import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.Utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;


public class MainActivity extends ActionBarActivity {

    private static String LOG_TAG = "GoPuppetYourself";

    public static final String PUPPET_INDEX = "com.nakedape.gopuppetyourself.PUPPET_INDEX";
    public static final String PUPPET_PATH = "com.nakedape.gopuppetyourself.PUPPET_PATH";

    private static final int REQUEST_PUPPET_GET = 4001;
    private static final int REQUEST_IMAGE_GET = 4002;
    private static final int REQUEST_EDIT = 4003;
    private static final String PUPPETS_ON_STAGE = "com.nakedape.gopuppetyourself.PUPPETS_ON_STAGE";
    private static final String BACKGROUND_PATH = "com.nakedape.gopuppetyourself.BACKGROUND_PATH";
    private static final String BACKGROUND_WIDTH = "com.nakedape.gopuppetyourself.BACKGROUND_WIDTH";
    private static final String BACKGROUND_HEIGHT = "com.nakedape.gopuppetyourself.BACKGROUND_HEIGHT";

    private Context context;
    private ViewGroup stage;
    private int dx;
    private int dy;
    private float prevX, prevY, x1Start, y1Start, x2Start, y2Start;
    private Puppet selectedPuppet;
    private ArrayList<Puppet> puppets;
    private boolean isBackstage = false;
    private File storageDir;
    private HashSet<String> puppetsOnStage;
    private PuppetShowRecorder showRecorder;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case PuppetShowRecorder.COUNTER_UPDATE:
                    progressBar.setProgress((int)showRecorder.getTimeFromStartMillis());
                    break;
                case PuppetShowRecorder.COUNTER_END:
                    mainControlButton.setBackground(getResources().getDrawable(R.drawable.ic_action_av_play_arrow));
                    Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_pause1000_fade_out);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            setViewGone(progressBar);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    });
                    progressBar.startAnimation(fadeOut);
                    mainControlFadeOut();
                    isPlaying = false;
            }
        }
    };
    private SeekBar progressBar;
    private int nextPuppetId = 0;
    private MainActivityDataFrag savedData;
    private ImageButton mainControlButton, recordButton, playButton, libraryButton;
    private PopupMenu puppetMenu;
    private RelativeLayout rootLayout;
    private boolean isControlPressed = false;
    private boolean isSecondControlShowing = false;
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private float lastScaleFactor = 1;
    private boolean scaleUp, scaleDown;
    private DisplayMetrics metrics;

    // Gesture fields
    private GestureDetectorCompat gestureDetector;
    private boolean flingRight, flingLeft, flingUp, flingDown, longPress, scrollLeft, scrollRight, scrollUp, scrollDown;
    private float scrollAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Initialize fields and get handles for UI elements
        context = this;
        metrics = getResources().getDisplayMetrics();
        rootLayout = (RelativeLayout)findViewById(R.id.root_layout);
        stage = (RelativeLayout)findViewById(R.id.stage);
        progressBar = (SeekBar)findViewById(R.id.progress_bar);
        progressBar.setVisibility(View.GONE);
        mainControlButton = (ImageButton)findViewById(R.id.bottom_left_button);
        mainControlButton.setOnTouchListener(mainControlTouchListener);
        recordButton = (ImageButton)findViewById(R.id.record_button);
        recordButton.setVisibility(View.GONE);
        playButton = (ImageButton)findViewById(R.id.play_button);
        playButton.setVisibility(View.GONE);
        libraryButton = (ImageButton)findViewById(R.id.puppet_library_button);
        libraryButton.setVisibility(View.GONE);
        gestureDetector = new GestureDetectorCompat(context, new MyGestureListener());

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
            if (savedData.currentBackground != null)
                stage.setBackground(new BitmapDrawable(getResources(), savedData.currentBackground));
            else
                stage.setBackground(new ColorDrawable(getResources().getColor(R.color.dark_grey)));
        }
        else { // Create a new data fragment instance to save the data
            savedData = new MainActivityDataFrag();
            fm.beginTransaction().add(savedData, "data").commit();
        }

        // Load puppets that are on stage
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        puppetsOnStage = (HashSet<String>)preferences.getStringSet(PUPPETS_ON_STAGE, null);
        if(puppetsOnStage != null) {
            File file;
            Object[] puppetPaths = puppetsOnStage.toArray();
            for (Object o : puppetPaths){
                String path = (String)o;
                file = new File(path);
                Puppet p = new Puppet(context, null);
                Utils.ReadPuppetFromFile(p, file);
                p.setId(nextPuppetId++);
                p.setOnTouchListener(headTouchListener);
                p.setTag(p.getName());
                p.setPath(path);
                if (!p.isOnStage()) p.setVisibility(View.GONE);
                if (savedData.layoutParamses.size() > 0){
                    p.setLayoutParams(savedData.layoutParamses.get(p.getId()));
                }
                stage.addView(p);
            }
        }
        else {
            puppetsOnStage = new HashSet<>();
        }

        // Set background
        String uriPath = preferences.getString(BACKGROUND_PATH, null);
        if (uriPath != null){
            int width = preferences.getInt(BACKGROUND_WIDTH, 600);
            int height = preferences.getInt(BACKGROUND_HEIGHT, 400);
            setBackGround(Uri.parse(uriPath), (float)width, (float)height);
            Log.d(LOG_TAG, "background set, path: " + Uri.parse(uriPath));
        }
    }
    @Override
    protected void onResume(){
        super.onResume();
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
            setBackGround(imageUri, stage.getWidth(), stage.getHeight());
        }
        else if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK){
            SetupNewPuppet(data);
        }
    }

    // Performance Methods
    private View.OnTouchListener headTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            return HandlePerformanceTouch((Puppet) view, event);
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
                    if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getName(), KeyFrame.CLOSE_MOUTH);
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
            if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getName(), KeyFrame.OPEN_MOUTH_NARROW);
        }
        else {
            puppet.upperJaw.setRotation(30 * puppet.getPivotDirection());
            if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getName(), KeyFrame.OPEN_MOUTH_MED);
        }
    }
    private void moveView(Puppet puppet, int X, int Y){
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) puppet
                .getLayoutParams();
        layoutParams.leftMargin = X - dx;
        layoutParams.topMargin = Y - dy;
        layoutParams.rightMargin = -250;
        layoutParams.bottomMargin = -250;
        if (showRecorder.isRecording()) showRecorder.RecordFrame(puppet.getName(), KeyFrame.MOVEMENT, layoutParams.leftMargin, layoutParams.topMargin);
        puppet.setLayoutParams(layoutParams);
    }

    // Control Methods
    private View.OnTouchListener mainControlTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            return handleMainControlTouch(view, motionEvent);
        }
    };
    private boolean handleMainControlTouch(View view, MotionEvent motionEvent){
        switch (motionEvent.getAction()){
            case MotionEvent.ACTION_DOWN:
                GoBackstage(view);
                isControlPressed = true;
                mainControlFadeIn();
                return true;
            case MotionEvent.ACTION_MOVE:
                secondControlsFadeIn();
                return true;
            case MotionEvent.ACTION_UP:
                GoToPerformance(view);
                if (isPlaying){
                    StopPlay();
                    return true;
                }
                Rect hitRect = new Rect();
                playButton.getGlobalVisibleRect(hitRect);
                if (hitRect.contains((int)motionEvent.getRawX(), (int)motionEvent.getRawY())) {
                    Log.d(LOG_TAG, "Play button pressed");
                    secondControlsFadeOut();
                    PlayClick(view);
                }
                else {
                    recordButton.getGlobalVisibleRect(hitRect);
                    if (hitRect.contains((int) motionEvent.getRawX(), (int) motionEvent.getRawY())) {
                        Log.d(LOG_TAG, "Record button pressed");
                        secondControlsFadeOut();
                        RecordClick(view);
                    }
                    else {
                        libraryButton.getGlobalVisibleRect(hitRect);
                        if (hitRect.contains((int)motionEvent.getRawX(), (int)motionEvent.getRawY())){
                            Log.d(LOG_TAG, "Library button pressed");
                            secondControlsFadeOut();
                            ShowPuppetLibrary(view);
                        }
                        else {
                            secondControlsFadeOut();
                        }
                    }
                }
                mainControlFadeOut();
                isControlPressed = false;
                return true;
        }
        return false;
    }
    private void secondControlsFadeIn(){
        if (!isSecondControlShowing) {
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));

            recordButton.setVisibility(View.VISIBLE);
            Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            recordButton.startAnimation(fadeIn);

            playButton.setVisibility(View.VISIBLE);
            fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            playButton.startAnimation(fadeIn);

            libraryButton.setVisibility(View.VISIBLE);
            fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            libraryButton.startAnimation(fadeIn);

            isSecondControlShowing = true;
        }
    }
    private void secondControlsFadeOut(){
        Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_pause1000_fade_out);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                recordButton.setVisibility(View.GONE);
                isSecondControlShowing = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        recordButton.startAnimation(fadeOut);

        fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_pause1000_fade_out);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                playButton.setVisibility(View.GONE);
                isSecondControlShowing = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        playButton.startAnimation(fadeOut);

        fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_pause1000_fade_out);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                libraryButton.setVisibility(View.GONE);
                isSecondControlShowing = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        libraryButton.startAnimation(fadeOut);
    }
    private void mainControlFadeIn(){
        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
        mainControlButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));
        mainControlButton.startAnimation(fadeIn);
    }
    private void mainControlFadeOut(){
        Animation fade_out = AnimationUtils.loadAnimation(this, R.anim.anim_pause1000_grow_fade_out);
        fade_out.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mainControlButton.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        mainControlButton.startAnimation(fade_out);
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
    private void setViewGone(View v){
        v.setVisibility(View.GONE);
    }
    public void RecordClick(View v){
        if (!isRecording) {
            if (showRecorder == null) {
                showRecorder = new PuppetShowRecorder(stage);
            }
            showRecorder.RecordStart();
            progressBar.setProgress(progressBar.getMax());
            Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.record_background));
            mainControlButton.startAnimation(fadeIn);
            recordButton.setBackground(getResources().getDrawable(R.drawable.ic_action_av_stop));
            isRecording = true;
        }
        else {
            showRecorder.RecordStop();
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));
            recordButton.setBackground(getResources().getDrawable(R.drawable.ic_action_rec));
            mainControlFadeOut();
            secondControlsFadeOut();
            isRecording = false;
        }
    }
    public void PlayClick(View v){
        if (showRecorder != null){
            showRecorder.RecordStop();
            progressBar.setMax(showRecorder.getLength());
            progressBar.setVisibility(View.VISIBLE);
            Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            progressBar.startAnimation(fadeIn);
            fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.ic_action_av_pause));
            mainControlButton.startAnimation(fadeIn);
            showRecorder.Play();
            isPlaying = true;
        }
    }
    public void StopPlay(){
        if (isPlaying){
            showRecorder.Stop();
            Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_pause1000_fade_out);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    setViewGone(progressBar);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            progressBar.startAnimation(fadeOut);
            mainControlFadeOut();
            secondControlsFadeOut();
            isPlaying = false;
        }
    }

    // Control pressed methods
    public void GoBackstage(View v){
        stage.setOnClickListener(backgroundClickListener);
        isBackstage = true;
        savedData.isBackstage = isBackstage;
        for (int i = 0; i < stage.getChildCount(); i++){
            stage.getChildAt(i).setOnTouchListener(backstageListener);
            stage.getChildAt(i).setVisibility(View.VISIBLE);
        }
    }
    public void GoToPerformance(View v){
        stage.setOnClickListener(null);
        if (puppetMenu != null) puppetMenu.dismiss();
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

    private View.OnTouchListener backstageListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            return HandleBackstageTouch((Puppet)view, event);
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
                lastScaleFactor = view.getScaleX();
                break;
            case MotionEvent.ACTION_UP:
                if (selectedPuppet != null) selectedPuppet.setBackground(null);
                selectedPuppet = (Puppet)view;
                selectedPuppet.setBackground(getResources().getDrawable(R.drawable.selected_puppet));
                puppetMenu = new PopupMenu(context, selectedPuppet);
                puppetMenu.inflate(R.menu.menu_puppet_edit);
                puppetMenu.setOnMenuItemClickListener(popupMenuListener);
                if (!selectedPuppet.isOnStage()){
                    MenuItem item = puppetMenu.getMenu().findItem(R.id.action_puppet_visible);
                    item.setChecked(false);
                }
                puppetMenu.show();
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

    private View.OnClickListener backgroundClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (selectedPuppet != null) selectedPuppet.setBackground(null);
            selectedPuppet = null;
            BackGroundButtonClick(view);
        }
    };
    public void BackGroundButtonClick(View v){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_GET);
        }
    } // Called from background click listener in backstage mode
    private void setBackGround(Uri imageUri, float reqWidth, float reqHeight){
        Log.d(LOG_TAG, "set background called");
        Bitmap bitmap = null;
        try {
            final String[] columns = {MediaStore.Images.ImageColumns.WIDTH, MediaStore.Images.ImageColumns.HEIGHT};
            Cursor cursor = MediaStore.Images.Media.query(getContentResolver(), imageUri, columns);
            cursor.moveToFirst();
            int width = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.WIDTH));
            Log.d(LOG_TAG, "image width = " + width);
            int height = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.HEIGHT));
            Log.d(LOG_TAG, "image width = " + height);
            if (width > stage.getWidth() || height > stage.getHeight()){
                double scale = Math.min(reqWidth / width, reqHeight / height);
                bitmap = Utils.decodeSampledBitmapFromContentResolver(getContentResolver(), imageUri, (int)(reqWidth * scale), (int)(reqHeight * scale));
                Log.d(LOG_TAG, "Scaled factor = " + String.valueOf(scale));
                Log.d(LOG_TAG, "Scaled image width = " + String.valueOf(stage.getWidth() * scale));
            }
            else {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            }
        } catch (IOException e){Toast.makeText(this, "Error loading background", Toast.LENGTH_SHORT).show();}
        if (bitmap != null){
            stage.setBackground(new BitmapDrawable(getResources(), bitmap));
            savedData.currentBackground = bitmap;
            SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
            prefEditor.putString(BACKGROUND_PATH, imageUri.toString());
            prefEditor.putInt(BACKGROUND_WIDTH, bitmap.getWidth());
            prefEditor.putInt(BACKGROUND_HEIGHT, bitmap.getHeight());
            prefEditor.apply();
            Log.d(LOG_TAG, "background changed, path: " + imageUri.toString());
        }
    } // Called from activity result

    // Scale puppet listener and methods
    private View.OnTouchListener scaleListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            return handleScaleTouch(view, motionEvent);
        }
    };
    private boolean handleScaleTouch(View view, MotionEvent motionEvent){
        int pointerCount = motionEvent.getPointerCount();
        switch (motionEvent.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                x1Start = motionEvent.getX();
                y1Start = motionEvent.getY();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                    x1Start = motionEvent.getX(pointerCount - 1);
                    x2Start = motionEvent.getX(pointerCount - 2);
                    y1Start = motionEvent.getY(pointerCount - 1);
                    y2Start = motionEvent.getY(pointerCount - 2);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pointerCount > 1) {
                    selectedPuppet.setScaleX(getScaleFactor(motionEvent.getX(pointerCount - 1), motionEvent.getY(pointerCount - 1),
                            motionEvent.getX(pointerCount - 2), motionEvent.getY(pointerCount - 2)));
                    selectedPuppet.setScaleY(Math.copySign(selectedPuppet.getScaleX(), selectedPuppet.getScaleY()));
                }
                return true;
            case MotionEvent.ACTION_UP:
                Utils.WritePuppetToFile(selectedPuppet, new File(selectedPuppet.getPath()));
                return true;
        }
        return true;
    }
    private float getScaleFactor(float x1, float y1, float x2, float y2){
        float dXInit = x2Start - x1Start;
        float dYInit = y2Start - y1Start;
        float startDistance = (float)Math.sqrt(dXInit*dXInit + dYInit*dYInit); // Distance between two pointers to start
        float dX = x2 - x1;
        float dY = y2 - y1;
        float currentDistance = (float)Math.sqrt(dX*dX + dY*dY); // Current distance between two pointers
        float scaleAmount = currentDistance - startDistance; // Neg = shrink, pos = grow
        float scaleFactor =  Math.abs(lastScaleFactor) + scaleAmount / metrics.densityDpi;
        // Make sure scale factor is reasonable between 1/5 and 5
        scaleFactor = Math.max(scaleFactor, 0.2f);
        scaleFactor = Math.min(scaleFactor, 5);
        // Set the sign to what it was originally to keep left/right orientation the same
        scaleFactor = Math.copySign(scaleFactor, lastScaleFactor);
        return scaleFactor;
    }

    // Popup menu methods
    private PopupMenu.OnMenuItemClickListener popupMenuListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            return handlePopupClick(menuItem);
        }
    };
    private boolean handlePopupClick(MenuItem menuItem){
        switch (menuItem.getItemId()){
            case R.id.action_edit_puppet:
                EditPuppet(selectedPuppet);
                return true;
            case R.id.action_remove_puppet:
                RemovePuppetFromStage(selectedPuppet);
                selectedPuppet = null;
                return true;
            case R.id.action_puppet_visible:
                if (menuItem.isChecked()){
                    selectedPuppet.setOnStage(false);
                    menuItem.setChecked(false);
                }
                else {
                    selectedPuppet.setOnStage(true);
                    menuItem.setChecked(true);
                }
                return true;
            case R.id.action_puppet_scale:
                selectedPuppet.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        return false;
                    }
                });
                lastScaleFactor = selectedPuppet.getScaleX();
                stage.setOnTouchListener(scaleListener);
                return true;
            case R.id.action_puppet_flip_horz:
                selectedPuppet.setScaleX(-selectedPuppet.getScaleX());
                Utils.WritePuppetToFile(selectedPuppet, new File(selectedPuppet.getPath()));
                return true;
        }
        return false;
    }
    private void RemovePuppetFromStage(Puppet p){
        stage.removeView(p);
        puppetsOnStage.remove(p.getPath());
        SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
        prefEditor.apply();
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

    // Puppet Library methods
    public void ShowPuppetLibrary(View v){
        final View layout = getLayoutInflater().inflate(R.layout.puppet_library, null);
        ImageButton closeButton = (ImageButton)layout.findViewById(R.id.puppet_library_close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ClosePuppetLibrary();
            }
        });

        // Touch Listener to handle touch events in the view flipper
        final ViewFlipper flipper = (ViewFlipper)layout.findViewById(R.id.puppet_flipper);
        flipper.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                gestureDetector.onTouchEvent(motionEvent);
                if (scrollUp || scrollDown) {
                    StartLibraryDrag(flipper.getCurrentView(), (String) flipper.getCurrentView().getTag());
                    return true;
                }
                if (flingRight) {
                    flipper.setOutAnimation(context, R.anim.anim_scale_small_right);
                    flipper.setInAnimation(context, R.anim.anim_scale_up_right);
                    flipper.showPrevious();
                    TextView nameText = (TextView)findViewById(R.id.puppet_name_textview);
                    nameText.setText((String)flipper.getCurrentView().getTag());
                    return true;
                }
                if (flingLeft) {
                    flipper.setOutAnimation(context, R.anim.anim_scale_small_left);
                    flipper.setInAnimation(context, R.anim.anim_scale_up_left);
                    flipper.showNext();
                    TextView nameText = (TextView)findViewById(R.id.puppet_name_textview);
                    nameText.setText((String) flipper.getCurrentView().getTag());
                    return true;
                }
                if (longPress) {
                    longPress = false;
                    StartLibraryDrag(flipper.getCurrentView(), (String) flipper.getCurrentView().getTag());
                    return true;
                }
                return true;
            }
        });

        // Setup size and posistion of popup window
        int width = Math.min(500, rootLayout.getWidth());
        int height = 400;
        layout.setMinimumHeight(height);
        layout.setMinimumWidth(width);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(rootLayout.getWidth() / 2 - width / 2, rootLayout.getHeight() / 2 - height / 2, 0, 0);
        layout.setLayoutParams(params);

        // Drag listener to catch drops inside the popup window
        layout.setOnDragListener(new LibraryPuppetDragEventListener());

        // Touch listner to allow popup window to be moved
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                final int X = (int) event.getRawX();
                final int Y = (int) event.getRawY();
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                        dx = X - lParams.leftMargin;
                        dy = Y - lParams.topMargin;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        moveView(view, X, Y);
                        break;
                }
                return true;
            }
        });
        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
        layout.startAnimation(fadeIn);
        rootLayout.addView(layout);

        // Drag listener to receive puppets dropped on the stage
        stage.setOnDragListener(new StagePuppetDragEventListener());

        // Touch listeners to start drag events for puppets on stage
        for (int i = 0; i < stage.getChildCount(); i++){
            stage.getChildAt(i).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            StartStageDrag((Puppet)view);
                            break;
                    }
                    return true;
                }
            });
        }

        // Touch listener to close popup window when the stage is touched
        stage.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ClosePuppetLibrary();
                        return true;
                }
                return false;
            }
        });
        Log.d(LOG_TAG, "popup should be visible");

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
                        if (stage.findViewWithTag(p.getName()) == null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    ImageView image = new ImageView(context);
                                    image.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                    image.setBackground(new BitmapDrawable(getResources(), p.getThumbnail()));
                                    image.setTag(p.getName());
                                    flipper.addView(image);
                                    if (flipper.getChildCount() == 1){
                                        TextView nameText = (TextView)findViewById(R.id.puppet_name_textview);
                                        nameText.setText(p.getName());
                                    }
                                    Log.d(LOG_TAG, "added puppet to library popup");

                                }
                            });
                        }
                    }
                }
            }
        };
        new Thread(loadPuppetsThread).start();

    }
    private void ClosePuppetLibrary(){
        View layout = findViewById(R.id.puppet_library_popup);
        rootLayout.removeView(layout);
        for (int i = 0; i < stage.getChildCount(); i++){
            stage.getChildAt(i).setOnTouchListener(headTouchListener);
        }
        stage.setOnTouchListener(null);
    }
    public void StartLibraryDrag(View v, String path){
        // Prevent library from receiving the drop
        View library = findViewById(R.id.puppet_library_popup);
        library.setOnDragListener(new NoDropDragListener());

        ClipData.Item pathItem = new ClipData.Item(storageDir.getPath() + "//" + path + getResources().getString(R.string.puppet_extension));
        String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData dragData = new ClipData("PUPPET_PATH", mime_type, pathItem);
        View.DragShadowBuilder myShadow = new PuppetDragShadowBuilder(v);
        v.startDrag(dragData,  // the data to be dragged
                myShadow,  // the drag shadow builder
                null,      // no need to use local data
                0          // flags (not currently used, set to 0)
        );
    }
    public void StartStageDrag(Puppet p){
        // Prevent stage from receiving the drop
        stage.setOnDragListener(new NoDropDragListener());

        ClipData.Item indexItem = new ClipData.Item(String.valueOf(stage.indexOfChild(p)));
        String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData dragData = new ClipData("PUPPET_INDEX", mime_type, indexItem);
        View.DragShadowBuilder myShadow = new PuppetDragShadowBuilder(p);
        p.startDrag(dragData,  // the data to be dragged
                myShadow,  // the drag shadow builder
                null,      // no need to use local data
                0);          // flags (not currently used, set to 0)
    }
    protected class StagePuppetDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    ClipData.Item item = event.getClipData().getItemAt(0);
                    String path = item.getText().toString();
                    File f = new File(path);
                    Puppet p = new Puppet(context, null);
                    Utils.ReadPuppetFromFile(p, f);
                    p.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View view, MotionEvent motionEvent) {
                            switch (motionEvent.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    StartStageDrag((Puppet) view);
                                    break;
                            }
                            return true;
                        }
                    });
                    p.setTag(p.getName());
                    p.setPath(path);
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(p.getTotalWidth(), p.getTotalHeight());
                    params.setMargins((int) event.getX() - p.getTotalWidth() / 2, (int) event.getY() - p.getTotalHeight() / 2, -250, -250);
                    p.setLayoutParams(params);
                    stage.addView(p);
                    puppetsOnStage.add(path);
                    SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
                    prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
                    prefEditor.apply();
                    ViewFlipper flipper = (ViewFlipper)findViewById(R.id.puppet_flipper);
                    flipper.removeView(flipper.getCurrentView());
                    TextView nameText = (TextView) findViewById(R.id.puppet_name_textview);
                    if (flipper.getChildCount() > 0) {
                        nameText.setText((String)flipper.getCurrentView().getTag());
                    } else {
                        nameText.setText("");
                    }
                    View library = findViewById(R.id.puppet_library_popup);
                    library.setOnDragListener(new LibraryPuppetDragEventListener());
                    return true;
                default:
                    return true;
            }

        }
    }
    protected class LibraryPuppetDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    ClipData.Item item = event.getClipData().getItemAt(0);
                    Log.d(LOG_TAG, "Puppet drag index " + item.getText().toString());
                    int index = Integer.parseInt(item.getText().toString());
                    Puppet p = (Puppet)stage.getChildAt(index);
                    ImageView image = new ImageView(context);
                    image.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    image.setBackground(new BitmapDrawable(getResources(), p.getThumbnail()));
                    image.setTag(p.getName());
                    ViewFlipper flipper = (ViewFlipper)findViewById(R.id.puppet_flipper);
                    flipper.addView(image);
                    flipper.setDisplayedChild(flipper.getChildCount() - 1);
                    TextView nameView = (TextView)findViewById(R.id.puppet_name_textview);
                    nameView.setText(p.getName());
                    RemovePuppetFromStage(p);
                    stage.setOnDragListener(new StagePuppetDragEventListener());
                    return true;
                default:
                    return false;
            }
        }
    }
    protected class NoDropDragListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    View library = findViewById(R.id.puppet_library_popup);
                    library.setOnDragListener(new LibraryPuppetDragEventListener());
                    stage.setOnDragListener(new StagePuppetDragEventListener());
                    return false;
                default:
                    return false;
            }
        }
    }
    private void moveView(View v, int X, int Y){
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) v
                .getLayoutParams();
        layoutParams.leftMargin = X - dx;
        layoutParams.topMargin = Y - dy;
        layoutParams.rightMargin = -250;
        layoutParams.bottomMargin = -250;
        v.setLayoutParams(layoutParams);
    }
    public void NewButtonClick(View v){
        Intent intent = new Intent(this, DesignerActivity.class);
        startActivityForResult(intent, REQUEST_PUPPET_GET);
    }
    private void SetupNewPuppet(Intent data){
        ClosePuppetLibrary();
        String filePath = data.getStringExtra(PUPPET_PATH);
        int index = data.getIntExtra(PUPPET_INDEX, -1);
        if (index > -1) stage.removeViewAt(index);
        Log.d(LOG_TAG, "index = " + index);
        Puppet puppet = new Puppet(context, null);
        Utils.ReadPuppetFromFile(puppet, new File(filePath));
        puppet.setOnTouchListener(backstageListener);
        puppetsOnStage.add(puppet.getPath());
        SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
        prefEditor.apply();
        stage.addView(puppet);
        GoToPerformance(null);
        Log.d(LOG_TAG, "new puppet should be visible");
    } // Called from activity result
    public void DeletePuppet(View v){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.alert_dialog_message_delete);
        builder.setTitle(R.string.alert_dialog_title_delete);
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ViewFlipper flipper = (ViewFlipper)findViewById(R.id.puppet_flipper);
                View selection = flipper.getCurrentView();
                flipper.removeView(selection);
                File puppetFile = new File(storageDir.getPath() + "//" + (String)selection.getTag() + getResources().getString(R.string.puppet_extension) );
                if (puppetFile.isFile())
                    if (!puppetFile.delete()) Log.e(LOG_TAG, "error deleting puppet file");
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onDown(MotionEvent event) {
            Log.d(DEBUG_TAG,"onDown: " + event.toString());
            flingRight = false;
            flingLeft = false;
            flingDown = false;
            flingUp = false;
            scrollDown = false;
            scrollUp = false;
            scrollLeft = false;
            scrollRight = false;
            scaleDown = false;
            scaleUp = false;
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
            Log.d(DEBUG_TAG, "onScroll: " + e1.toString()+e2.toString());
            if (Math.abs(distanceX) >= Math.abs(distanceY)) {
                if (distanceX > 0)
                    scrollRight = true;
                else
                    scrollLeft = true;
            } else {
                if (distanceY > 0)
                    scrollDown = true;
                else
                    scrollUp = true;
            }
            float dX = e2.getRawX() - e1.getRawX();
            float dY = e2.getRawY() - e1.getRawY();
            scrollAmount = (float)Math.sqrt(dX*dX + dY*dY);
            if (dX < 0 && dY < 0) {
                scrollAmount = -scrollAmount;
            }
            Log.d(LOG_TAG, "scroll amount = " + scrollAmount);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            Log.d(DEBUG_TAG, "onFling: " + event1.toString()+event2.toString());
            if (Math.abs(velocityX) >= Math.abs(velocityY)) {
                Log.d(LOG_TAG, "horizontal fling");
                if (velocityX > 0)
                    flingRight = true;
                else if (velocityX < 0)
                    flingLeft = true;
            }
            else {
                Log.d(LOG_TAG, "vertical fling");
                if (velocityY > 0)
                    flingUp = true;
                else if (velocityY < 0)
                    flingDown = true;
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            Log.d(DEBUG_TAG, "onLongPress: " + event.toString());
            longPress = true;
        }
    }
}
