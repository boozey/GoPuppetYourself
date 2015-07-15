package com.nakedape.gopuppetyourself;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;


public class MainActivity extends Activity {

    private static String LOG_TAG = "GoPuppetYourself";

    public static final String PUPPET_INDEX = "com.nakedape.gopuppetyourself.PUPPET_INDEX";
    public static final String PUPPET_PATH = "com.nakedape.gopuppetyourself.PUPPET_PATH";

    private static final int REQUEST_PUPPET_GET = 4001;
    private static final int REQUEST_IMAGE_GET = 4002;
    private static final int REQUEST_EDIT = 4003;
    private static final int REQUEST_IMAGE_CAPTURE = 4004;
    private static final String PUPPETS_ON_STAGE = "com.nakedape.gopuppetyourself.PUPPETS_ON_STAGE";
    private static final String BACKGROUND_PATH = "com.nakedape.gopuppetyourself.BACKGROUND_PATH";
    private static final String FIRST_RUN = "com.nakedape.gopuppetyourself.FIRST_RUN";

    private Context context;
    private RelativeLayout stage;
    private int dx;
    private int dy;
    private float prevX, prevY, x1Start, y1Start, x2Start, y2Start;
    private Puppet selectedPuppet;
    private boolean isBackstage = false;
    private File storageDir, showDir, backgroundDir;
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
                    mainControlButton.setBackground(getResources().getDrawable(R.drawable.ic_av_play_arrow_plain));
                    Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_pause1000_fade_out);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            progressBar.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    });
                    progressBar.startAnimation(fadeOut);
                    SwitchToRecordStage();
                    mainControlFadeOut(1000);
                    isPlaying = false;
            }
        }
    };
    private SeekBar progressBar;
    private int nextPuppetId = 0;
    private MainActivityDataFrag savedData;
    private ImageButton mainControlButton, recordButton, playButton, libraryButton, backgroundLibraryButton, menuButton;
    private PopupMenu puppetMenu;
    private RelativeLayout rootLayout;
    private boolean isControlPressed = false;
    private boolean isSecondControlShowing = false;
    private boolean isLibraryOpen = false;
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private boolean isBackgroundLibraryOpen = false;
    private boolean isScaling = false;
    private float lastScaleFactor = 1;
    private int lastRotation = 0;
    private boolean scaleUp, scaleDown;
    private DisplayMetrics metrics;
    private String cameraCapturePath;
    private PuppetListAdapter puppetListAdapter;
    private BitmapFileListAdapter backgroundListAdapter;
    private RelativeLayout showStage;
    private boolean isFirstRun;
    private RotationGestureDetector mRotationDetector;
    private ActionMode puppetActionMode;

    // Gesture fields
    private GestureDetector gestureDetector;
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
        showStage = (RelativeLayout)findViewById(R.id.show_stage);
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
        backgroundLibraryButton = (ImageButton)findViewById(R.id.background_library_button);
        backgroundLibraryButton.setVisibility(View.GONE);
        menuButton = (ImageButton)findViewById(R.id.main_nav_menu_button);
        menuButton.setVisibility(View.GONE);
        gestureDetector = new GestureDetector(context, new MyGestureListener());

        // Hid the Action Bar if present
        ActionBar actionBar = getActionBar();
        if (actionBar != null){
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.hide();
        }

        // Prepare show recorder
        showRecorder = new PuppetShowRecorder(context, stage);
        showRecorder.setHandler(mHandler);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Prepare shared preferences
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);

        // Prepare puppet storage directory for access
        if (Utils.isExternalStorageWritable()){
            storageDir = new File(getExternalFilesDir(null), getResources().getString(R.string.puppet_directory));
            if (!storageDir.exists())
                if (!storageDir.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
            showDir = new File(getExternalFilesDir(null), "puppet shows");
            if (!showDir.exists())
                if (!showDir.mkdir()) Log.e(LOG_TAG, "error creating puppet show directory");
            backgroundDir = new File(getExternalFilesDir(null), "backgrounds");
            if (!backgroundDir.exists())
                if (!backgroundDir.mkdir()) Log.e(LOG_TAG, "error creating background directory");
        }
        else {
            storageDir = new File(getFilesDir(), getResources().getString(R.string.puppet_directory));
            if (!storageDir.exists())
                if (!storageDir.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
            showDir = new File(getFilesDir(), "puppet shows");
            if (!showDir.exists())
                if (!showDir.mkdir()) Log.e(LOG_TAG, "error creating puppet show directory");
            backgroundDir = new File(getFilesDir(), "backgrounds");
            if (!backgroundDir.exists())
                if (!backgroundDir.mkdir()) Log.e(LOG_TAG, "error creating background directory");
        }

        // Check for a data fragment retained after activity restart
        FragmentManager fm = getFragmentManager();
        savedData = (MainActivityDataFrag) fm.findFragmentByTag("data");
        if (savedData != null){ // Load the data
            cameraCapturePath = savedData.cameraCapturePath;
            if (savedData.currentBackground != null) {
                stage.setBackground(new BitmapDrawable(getResources(), savedData.currentBackground));
                stage.setTag(savedData.currentBackgroundPath);
            }
            else {
                stage.setBackground(new ColorDrawable(Color.parseColor("#8B37AF")));
                stage.setTag("default");
            }
            if (savedData.puppetShow != null)
                showRecorder.setShow(savedData.puppetShow);
        }
        else { // Create a new data fragment instance to save the data and perform
            // other tasks that should only happen on initial app starts
            savedData = new MainActivityDataFrag();
            fm.beginTransaction().add(savedData, "data").commit();
        }

        // Set background
        String backgroundPath = preferences.getString(BACKGROUND_PATH, null);
        if (backgroundPath != null){ // Should only be null if user has never set a background
            setBackground(backgroundPath);
        }

        // Check if this is the first run of the app
        isFirstRun = preferences.getBoolean(FIRST_RUN, true);
        if (!isFirstRun) {
            // Load puppets that are on stage
            puppetsOnStage = (HashSet<String>) preferences.getStringSet(PUPPETS_ON_STAGE, null);
            if (puppetsOnStage != null) {
                File file;
                Object[] puppetPaths = puppetsOnStage.toArray();
                for (Object o : puppetPaths) {
                    String path = (String) o;
                    file = new File(path);
                    Puppet p = new Puppet(context, null);
                    Utils.ReadPuppetFromFile(p, file);
                    if (p.getLowerJawBitmap() != null) {
                        p.setId(nextPuppetId++);
                        p.setOnTouchListener(headTouchListener);
                        p.setTag(p.getName());
                        p.setPath(path);
                        if (!p.isOnStage()) p.setVisibility(View.GONE);
                        if (savedData.layoutParamses.size() > 0) {
                            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)savedData.layoutParamses.get(p.getId());
                            params.leftMargin = Utils.getInBounds(params.leftMargin, 0, metrics.widthPixels - p.getScaledWidth());
                            params.topMargin = Utils.getInBounds(params.topMargin, 0, metrics.heightPixels - p.getScaledHeight());
                            p.setLayoutParams(params);
                        }
                        stage.addView(p);
                    } else {
                        puppetsOnStage.remove(o);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
                        editor.apply();
                    }
                }
            } else {
                puppetsOnStage = new HashSet<>();
            }
        } else {
            firstOnCreate();
        }

    }
    @Override
    protected void onResume(){
        super.onResume();
        mainControlFadeOut(1000);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isFinishing()){ // Save instance data for activity restart
            // Save puppet positions
            for (int i = 0; i < stage.getChildCount(); i++){
                savedData.layoutParamses.add(stage.getChildAt(i).getLayoutParams());
            }
            savedData.cameraCapturePath = cameraCapturePath;
            if (showRecorder != null) {
                PuppetShow show = showRecorder.getShow();
                if (show != null) {
                    show.ReleaseContext();
                    savedData.puppetShow = showRecorder.getShow();
                }
            }
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id){
            case R.id.action_test_show_save:
                if (showRecorder != null ){
                    File saveFile = new File(showDir, "puppet_show_test.show");
                    //showRecorder.WriteShowToFile(saveFile);
                    showRecorder.WriteShowToZipFile(saveFile);
                }
                break;
            case R.id.action_test_show_load:
                showRecorder = new PuppetShowRecorder(context, stage);
                showRecorder.setHandler(mHandler);
                File file = new File(showDir, "puppet_show_test.show");
                Log.d(LOG_TAG, "Show size: " + file.length());
                //showRecorder.LoadShow(file);
                showRecorder.LoadShowFromZipFile(file);
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
            CloseBGPopup();
        }
        else if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK){
            SetupNewPuppet(data);
        }
        else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK){
            setBackground(cameraCapturePath, stage.getWidth(), stage.getHeight());
            CloseBGPopup();
        }
    }
    @Override
    public boolean onKeyDown(int keycode, KeyEvent e){
        switch (keycode){
            case KeyEvent.KEYCODE_BACK:
                View v = findViewById(R.id.new_image_popup);
                if (isLibraryOpen) {
                    ClosePuppetLibrary();
                    return true;
                } else if(isBackgroundLibraryOpen){
                    if (v != null) CloseGetNewImagePopup(null);
                    else CloseBGPopup();
                    return true;
                } else {
                    return super.onKeyDown(keycode, e);
                }
            default:
                return super.onKeyDown(keycode, e);
        }
    }

    // First run methods
    private String setupBananaMan(){
        Puppet bananaMan = new Puppet(context, null);
        bananaMan.setUpperJawImage(Utils.decodeSampledBitmapFromResource(getResources(), R.drawable.banana_man_top, 100, 100));
        bananaMan.setLowerJawImage(Utils.decodeSampledBitmapFromResource(getResources(), R.drawable.banana_man_bottom, 100, 100));
        bananaMan.setUpperPivotPoint(new Point(bananaMan.getUpperJawBitmap().getWidth() / 3, bananaMan.getUpperJawBitmap().getHeight()));
        bananaMan.setLowerPivotPoint(new Point(bananaMan.getUpperJawBitmap().getWidth() / 3, 0));
        bananaMan.setName("Banana Man");

        // Save puppet to storage directory
        File saveFile = new File(storageDir, bananaMan.getName() + getResources().getString(R.string.puppet_extension));
        return Utils.WritePuppetToFile(bananaMan, saveFile);
    }
    private void firstOnCreate(){
        stage.setTag("default");
        String path = setupBananaMan();
        Puppet p = new Puppet(context, null);
        Utils.ReadPuppetFromFile(p, new File(path));
        p.setOnTouchListener(headTouchListener);
        p.setTag(p.getName());
        p.setPath(path);
        stage.addView(p);
        puppetsOnStage = new HashSet<>();
        puppetsOnStage.add(path);
        SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
        prefEditor.putBoolean(FIRST_RUN, false);
        prefEditor.apply();
    }

    // Main menu methods
    public void MenuClick(View v){
        PopupMenu menu = new PopupMenu(context, menuButton);
        menu.inflate(R.menu.menu_main);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                return false;
            }
        });
        menu.show();
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
                if (event.getPointerCount() < 2) {
                    puppet.OpenMouth(0);
                    if (showRecorder.isRecording())
                        showRecorder.RecordFrame(puppet.getName(), KeyFrame.CLOSE_MOUTH);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() > 1) {
                    moveMouth(puppet, event.getY(0), event.getY(1));
                } else {
                    puppet.OpenMouth(0);
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
        if (width < metrics.densityDpi * 0.8){
            puppet.OpenMouth(20);
            if (showRecorder.isRecording()) showRecorder.RecordFrame(showRecorder.getOpenMouthFrame(puppet.getName(), 20));
        }
        else if (width < metrics.densityDpi * 1.2){
            puppet.OpenMouth(30);
            if (showRecorder.isRecording()) showRecorder.RecordFrame(showRecorder.getOpenMouthFrame(puppet.getName(), 30));
        }
        else if (width < metrics.densityDpi * 1.4) {
            puppet.OpenMouth(40);
            if (showRecorder.isRecording()) showRecorder.RecordFrame(showRecorder.getOpenMouthFrame(puppet.getName(), 40));
        }
        else if (width < metrics.densityDpi * 1.8) {
            puppet.OpenMouth(50);
            if (showRecorder.isRecording()) showRecorder.RecordFrame(showRecorder.getOpenMouthFrame(puppet.getName(), 50));
        } else {
            puppet.OpenMouth(60);
            if (showRecorder.isRecording()) showRecorder.RecordFrame(showRecorder.getOpenMouthFrame(puppet.getName(), 60));

        }
    }
    private void moveView(Puppet puppet, int X, int Y){
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) puppet
                .getLayoutParams();
        if (puppet.isMouthOpen()){ // Compensate for clip padding
            layoutParams.leftMargin = X - dx - (int)(puppet.getLeftClipPadding() * puppet.getScaleX());
            layoutParams.topMargin = Y - dy - (int)(puppet.getTopClipPadding() * puppet.getScaleY());
            layoutParams.rightMargin = -250;
            layoutParams.bottomMargin = -250;
        } else {
            layoutParams.leftMargin = X - dx;
            layoutParams.topMargin = Y - dy;
            layoutParams.rightMargin = -250;
            layoutParams.bottomMargin = -250;
        }
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
        Rect hitRect = new Rect();
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                GoBackstage(view);
                isControlPressed = true;
                mainControlFadeIn();
                return true;
            case MotionEvent.ACTION_MOVE:
                secondControlsFadeIn();
                return true;
            case MotionEvent.ACTION_UP:
                if (isPlaying) {
                    StopPlay();
                    secondControlsFadeOut(1000);
                    mainControlFadeOut(1000);
                    isControlPressed = false;
                    return true;
                }
                playButton.getGlobalVisibleRect(hitRect);
                if (hitRect.contains((int) motionEvent.getRawX(), (int) motionEvent.getRawY())) {
                    secondControlsFadeOut(1000);
                    PlayClick(view);
                    GoToPerformance(view);
                    mainControlFadeOut(1000);
                    isControlPressed = false;
                    return true;
                }

                recordButton.getGlobalVisibleRect(hitRect);
                if (hitRect.contains((int) motionEvent.getRawX(), (int) motionEvent.getRawY())) {
                    secondControlsFadeOut(1000);
                    GoToPerformance(view);
                    RecordClick(view);
                    mainControlFadeOut(1000);
                    isControlPressed = false;
                    return true;
                }

                libraryButton.getGlobalVisibleRect(hitRect);
                if (hitRect.contains((int) motionEvent.getRawX(), (int) motionEvent.getRawY())) {
                    secondControlsFadeOut(1000);
                    mainControlFadeOut(1000);
                    isControlPressed = false;
                    ShowPuppetLibrary(view);
                    return true;
                }

                backgroundLibraryButton.getGlobalVisibleRect(hitRect);
                if (hitRect.contains((int) motionEvent.getRawX(), (int) motionEvent.getRawY())) {
                    secondControlsFadeOut(1000);
                    mainControlFadeOut(1000);
                    isControlPressed = false;
                    ShowBackgroundPopup();
                    return true;
                }
                if (puppetActionMode == null) {
                    secondControlsFadeOut(1000);
                    GoToPerformance(view);
                    mainControlFadeOut(1000);
                    isControlPressed = false;
                }
                return true;
        }
        return false;
    }
    private void secondControlsFadeIn(){
        if (!isSecondControlShowing) {
            //mainControlButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));

            AnimatorSet set1 = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_scale_in);
            set1.setTarget(recordButton);
            set1.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    recordButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    recordButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });

            AnimatorSet set2 = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_scale_in);
            set2.setTarget(playButton);
            set2.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    playButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    playButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });

            AnimatorSet set3 = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_scale_in);
            set3.setTarget(libraryButton);
            set3.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    libraryButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    libraryButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });

            AnimatorSet set4 = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_scale_in);
            set4.setTarget(backgroundLibraryButton);
            set4.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    backgroundLibraryButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    backgroundLibraryButton.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });

            set1.start();
            set2.start();
            set3.start();
            set4.start();

            isSecondControlShowing = true;
        }
    }
    private void secondControlsFadeOut(int pause){
        Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_grow_fade_out);
        fadeOut.setStartOffset(pause);
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

        fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_grow_fade_out);
        fadeOut.setStartOffset(pause);
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

        fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_grow_fade_out);
        fadeOut.setStartOffset(pause);
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

        fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_grow_fade_out);
        fadeOut.setStartOffset(pause);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                backgroundLibraryButton.setVisibility(View.GONE);
                isSecondControlShowing = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        backgroundLibraryButton.startAnimation(fadeOut);
    }
    private void mainControlFadeIn(){
        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
        if (isRecording)
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.record_background));
        else
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));
        mainControlButton.startAnimation(fadeIn);
    }
    private void mainControlFadeOut(int pause){
        Animation fade_out = AnimationUtils.loadAnimation(this, R.anim.anim_grow_fade_out);
        fade_out.setStartOffset(pause);
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

    // Record/play show methods
    public void RecordClick(View v){
        if (!isRecording) {
            showRecorder = new PuppetShowRecorder(context, stage);
            showRecorder.setHandler(mHandler);
            showRecorder.prepareToRecord();
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
            showRecorder.FinalizeRecording();
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.control_button_background));
            recordButton.setBackground(getResources().getDrawable(R.drawable.ic_action_rec));
            mainControlFadeOut(1000);
            secondControlsFadeOut(1000);
            isRecording = false;
        }
    }
    public void PlayClick(View v){
        if (showRecorder != null){
            if (showRecorder.isRecording())
                showRecorder.RecordStop();
            SwitchToShowStage(); // Calls PlayShow
        }
    }
    private void PlayShow(){
        if (showRecorder.prepareToPlay()) {
            progressBar.setMax((int)showRecorder.getLength());
            progressBar.setVisibility(View.VISIBLE);
            Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            progressBar.startAnimation(fadeIn);
            fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
            mainControlButton.setBackground(getResources().getDrawable(R.drawable.ic_av_stop_plain));
            mainControlButton.startAnimation(fadeIn);
            showRecorder.Play();
            isPlaying = true;
        } else {
            SwitchToRecordStage();
            isPlaying = false;
            Toast.makeText(context, "Error playing show", Toast.LENGTH_SHORT).show();
        }
    }// Called from stage animation listener
    private void SwitchToShowStage(){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams((int)(rootLayout.getWidth() * 0.75), (int)(rootLayout.getHeight() * 0.75));
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        showStage.setLayoutParams(params);
        //showStage.setLayoutParams(stage.getLayoutParams());
        showStage.setAlpha(0f);
        showStage.setVisibility(View.VISIBLE);
        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_out);
        set.setTarget(stage);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                //rootLayout.removeView(stage);
                //rootLayout.addView(showStage, 0);
                stage.setVisibility(View.GONE);
                showRecorder.setStage(showStage);
                PlayShow();
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        set.start();
    }
    private void SwitchToRecordStage(){
        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_out);
        set.setTarget(showStage);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                showStage.setVisibility(View.GONE);
                stage.setVisibility(View.VISIBLE);
                AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_in);
                set.setTarget(stage);
                set.start();
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        set.start();
    } // Called from mHandler when show ends
    public void StopPlay(){
        if (isPlaying){
            showRecorder.Stop();
            SwitchToRecordStage();
            Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_pause1000_fade_out);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            progressBar.startAnimation(fadeOut);
            mainControlFadeOut(1000);
            secondControlsFadeOut(1000);
            isPlaying = false;
        }
    }

    // Control pressed methods
    public void GoBackstage(View v){
        isBackstage = true;
        savedData.isBackstage = isBackstage;
        selectedPuppet = null;
        for (int i = 0; i < stage.getChildCount(); i++){
            stage.getChildAt(i).setOnTouchListener(backstageListener);
            if (stage.getChildAt(i).getVisibility() == View.GONE) {
                // Animate the appearance// Start the animation and add the popup
                AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_in);
                set.setTarget(stage.getChildAt(i));
                stage.getChildAt(i).setVisibility(View.VISIBLE);
                set.start();
            }
        }
    } // Called on main control action down
    public void GoToPerformance(View v){
        if (!isLibraryOpen && puppetActionMode == null) {
            //if (puppetMenu != null) puppetMenu.dismiss();
            if (selectedPuppet != null) {
                selectedPuppet.setBackground(null);
            }
            isBackstage = false;
            if (!isPlaying) {
                for (int i = 0; i < stage.getChildCount(); i++) {
                    final Puppet p = (Puppet) stage.getChildAt(i);
                    if (p.isOnStage()) {
                        p.setOnClickListener(null);
                        p.setOnLongClickListener(null);
                        p.setOnTouchListener(headTouchListener);
                    } else {
                        // Animate the disappearance
                        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_out);
                        set.addListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animator) {

                            }

                            @Override
                            public void onAnimationEnd(Animator animator) {
                                p.setVisibility(View.GONE);
                            }

                            @Override
                            public void onAnimationCancel(Animator animator) {

                            }

                            @Override
                            public void onAnimationRepeat(Animator animator) {

                            }
                        });
                        set.setTarget(p);
                        set.start();
                    }
                }
            }
        }
    } // Called on main control action up

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
                selectedPuppet = view;
                selectedPuppet.setBackground(getResources().getDrawable(R.drawable.selected_puppet));
                RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
                dx = X - lParams.leftMargin;
                dy = Y - lParams.topMargin;
                lastScaleFactor = view.getScaleX();
                if (puppetActionMode == null){
                    puppetActionMode = startActionMode(puppetActionModeCallback);
                } else if (!selectedPuppet.equals(view)){
                    puppetActionMode.finish();
                    puppetActionMode = startActionMode(puppetActionModeCallback);
                }
                break;
            case MotionEvent.ACTION_UP:
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
    // Puppet ActionMode methods
    private ActionMode.Callback puppetActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            // Inflate a menu resource providing context menu items
            actionMode.getMenuInflater().inflate(R.menu.menu_puppet_edit, menu);
            stage.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (puppetActionMode != null)
                        puppetActionMode.finish();
                    return true;
                }
            });
            ActionBar actionBar = getActionBar();
            if (actionBar != null){
                actionBar.show();
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            if (!selectedPuppet.isOnStage()) {
                MenuItem visibilityItem = menu.findItem(R.id.action_puppet_visible);
                visibilityItem.setIcon(getResources().getDrawable(R.drawable.ic_visibility_off_white_24dp));
            }
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            switch (menuItem.getItemId()){
                case R.id.action_edit_puppet:
                    EditPuppet(selectedPuppet);
                    actionMode.finish();
                    return true;
                case R.id.action_remove_puppet:
                    RemovePuppetFromStage(selectedPuppet);
                    selectedPuppet = null;
                    actionMode.finish();
                    return true;
                case R.id.action_puppet_visible:
                    if (selectedPuppet.isOnStage()){
                        if (isRecording)
                            showRecorder.RecordFrame(showRecorder.getVisiblilityFrame(selectedPuppet.getName(), false));
                        selectedPuppet.setOnStage(false);
                    }
                    else {
                        if (isRecording)
                            showRecorder.RecordFrame(showRecorder.getVisiblilityFrame(selectedPuppet.getName(), true));
                        selectedPuppet.setOnStage(true);
                    }
                    actionMode.finish();
                    return true;
                case R.id.action_puppet_scale:
                    stage.setOnTouchListener(scaleListener);
                    selectedPuppet.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View view, MotionEvent motionEvent) {
                            return false;
                        }
                    });
                    lastScaleFactor = selectedPuppet.getScaleX();
                    isScaling = true;
                    Toast.makeText(context, getString(R.string.toast_scale), Toast.LENGTH_SHORT).show();
                    return true;
                case R.id.action_puppet_flip_horz:
                    if (isRecording)
                        showRecorder.RecordFrame(showRecorder.getScaleFrame(selectedPuppet.getName(), -selectedPuppet.getScaleX(), selectedPuppet.getScaleY()));
                    selectedPuppet.setScaleX(-selectedPuppet.getScaleX());
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.WritePuppetToFile(selectedPuppet, new File(selectedPuppet.getPath()));
                        }
                    }).start();
                    actionMode.finish();
                    return true;
                case R.id.action_bring_to_front:
                    RemovePuppetFromStage(selectedPuppet);
                    addPuppetToStage(selectedPuppet, selectedPuppet.getLeft(), selectedPuppet.getTop());
                    if (!isControlPressed) selectedPuppet.setOnTouchListener(headTouchListener);
                    actionMode.finish();
                    return true;
                case R.id.action_rotate:
                    mRotationDetector = new RotationGestureDetector(rotationGestureListener, selectedPuppet);
                    selectedPuppet.setOnTouchListener(rotateListener);
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            puppetActionMode = null;
            if (selectedPuppet != null)
                selectedPuppet.setBackground(null);
            stage.setOnClickListener(null);
            GoToPerformance(null);
            ActionBar actionBar = getActionBar();
            if (actionBar != null){
                actionBar.hide();
            }
            secondControlsFadeOut(0);
            mainControlFadeOut(0);
            isControlPressed = false;
        }
    };
    public void EditPuppet(Puppet p){
        Intent intent = new Intent(this, DesignerActivity.class);
        for (int i = 0; i < stage.getChildCount(); i++){
            if (stage.getChildAt(i).equals(p)) {
                intent.putExtra(PUPPET_INDEX, i);
            }
        }
        intent.putExtra(PUPPET_PATH, p.getPath());
        startActivityForResult(intent, REQUEST_EDIT);
    }

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
                    if (isRecording)
                        showRecorder.RecordFrame(showRecorder.getScaleFrame(selectedPuppet.getName(), selectedPuppet.getScaleX(), selectedPuppet.getScaleY()));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (isRecording)
                    showRecorder.RecordFrame(showRecorder.getScaleFrame(selectedPuppet.getName(), selectedPuppet.getScaleX(), selectedPuppet.getScaleY()));
                Utils.WritePuppetToFile(selectedPuppet, new File(selectedPuppet.getPath()));
                if (!isControlPressed) {
                    stage.setOnTouchListener(null);
                    GoToPerformance(null);
                }
                isScaling = false;
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

    // Rotate puppet listener and methods
    private View.OnTouchListener rotateListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mRotationDetector.onTouchEvent(motionEvent);
            switch (motionEvent.getAction()){
                case MotionEvent.ACTION_UP:
                    if (!isControlPressed) {
                        GoToPerformance(null);
                    }
                    isScaling = false;
                    return true;
            }
            return false;
        }
    };
    private RotationGestureDetector.OnRotationGestureListener rotationGestureListener = new RotationGestureDetector.OnRotationGestureListener() {
        @Override
        public void onRotation(RotationGestureDetector rotationDetector) {
            selectedPuppet.setRotation(rotationDetector.getAngle());
            //Log.d(LOG_TAG, "Rotation angle: " + rotationDetector.getAngle());
        }
    };

    // Start background gallery flow
    public void BackgroundGalleryButtonClick(View v){
        ShowBackgroundPopup();
    }
    private void ShowBackgroundPopup(){
        if (!isBackgroundLibraryOpen){
            if (isLibraryOpen) ClosePuppetLibrary();
            if (puppetActionMode != null) puppetActionMode.finish();
            isBackgroundLibraryOpen = true;

            // Set position of the popup
            final View layout = getLayoutInflater().inflate(R.layout.background_library, null);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            layout.setLayoutParams(params);

            // Configure listview to show thumbnails
            final ListView listView = (ListView)layout.findViewById(R.id.background_list_view);
            backgroundListAdapter = new BitmapFileListAdapter(context, R.layout.puppet_list_item);
            listView.setAdapter(backgroundListAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    setBackground(backgroundListAdapter.getItem(i).getPath());
                }
            });
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    // Prevent library from receiving the drop
                    View library = findViewById(R.id.background_library_popup);
                    library.setOnDragListener(new NoDropDragListener());
                    View v = view.findViewById(R.id.list_item_puppet_thumb);
                    StartLibraryDrag(v, backgroundListAdapter.getItem(i).getPath(), i);
                    return false;
                }
            });

            //Search for files an a separate thread and add to list adapter as they are found
            Runnable loadThumbsThread = new Runnable() {
                @Override
                public void run() {
                    File[] files = backgroundDir.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File file) {
                            return file.getPath().endsWith(".png");
                        }
                    });
                    if (files.length > 0) {
                        for (final File f : files) {
                            final Bitmap b = Utils.decodedSampledBitmapFromFile(f, 256, 256);
                            listView.post(new Runnable() {
                                @Override
                                public void run() {
                                    backgroundListAdapter.add(b, f);

                                }
                            });

                        }
                    }
                }
            };
            new Thread(loadThumbsThread).start();

            // Touch listener to close popup window when the stage is touched
            stage.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            CloseBGPopup();
                            return true;
                    }
                    return false;
                }
            });

            // Stage drag listener to receive drops
            stage.setOnDragListener(new StageBGDragEventListener());

            // Trash drag listener
            View trashView = layout.findViewById(R.id.background_library_trash);
            trashView.setOnDragListener(new BGTrashDragEventListener());

            // Add button click listener
            ImageButton addButton = (ImageButton)layout.findViewById(R.id.add_button);
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ShowGetNewImagePopup();
                }
            });

            // Start the animation and show the popup
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.flip_in_from_right);
            rootLayout.addView(layout);
            layout.setPivotX(500);
            layout.setPivotY(rootLayout.getHeight() / 2);
            set.setTarget(layout);
            set.start();
        }
    }
    private void CloseBGPopup(){
        View v = findViewById(R.id.new_image_popup);
        if (v != null) CloseGetNewImagePopup(null);
        final View layout = findViewById(R.id.background_library_popup);
        if (layout != null){
            if (!isControlPressed)
                GoToPerformance(null);
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.flip_out_to_right);
            layout.setPivotX(500);
            layout.setPivotY(rootLayout.getHeight() / 2);
            set.setTarget(layout);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    rootLayout.removeView(layout);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            set.start();
            isBackgroundLibraryOpen = false;
        }
    }
    public void TrashClick(View v){
        Toast.makeText(context, getString(R.string.trash_click_message), Toast.LENGTH_SHORT).show();
    }
    protected class StageBGDragEventListener implements View.OnDragListener{
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
                    // Remove item from library
                    //item = event.getClipData().getItemAt(1);
                    //int index = Integer.parseInt(item.getText().toString());
                    //backgroundListAdapter.removeItem(index);
                    setBackground(path);
                    return true;
                default:
                    return true;
            }
        }
    }
    protected class BGTrashDragEventListener implements View.OnDragListener{
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // Animate view to indicate it can receive the drop
                    AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.grow);
                    set.setTarget(v);
                    set.start();
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    AnimatorSet shrink = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.shrink_to_1x);
                    shrink.setTarget(v);
                    shrink.start();
                    return true;
                case DragEvent.ACTION_DROP:
                    v.setScaleX(1f);
                    v.setScaleY(1f);
                    ClipData.Item item = event.getClipData().getItemAt(0);
                    String data = item.getText().toString();
                    // Remove item from library
                    item = event.getClipData().getItemAt(1);
                    int index = Integer.parseInt(item.getText().toString());
                    backgroundListAdapter.removeItem(index);
                    File file = new File(data);
                    if (file.isFile())
                        if (!file.delete()) Log.e(LOG_TAG, "error deleting file");

                    return true;
                default:
                    return false;
            }
        }
    }
    private void setBackGround(Uri imageUri, float reqWidth, float reqHeight){
        Bitmap bitmap = null;
        try {
                    bitmap = Utils.decodeSampledBitmapFromContentResolver(getContentResolver(), imageUri, (int)reqWidth, (int)reqHeight);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Unable to load image", Toast.LENGTH_LONG).show();
        }
        if (bitmap != null){
            if (bitmap.getWidth() > reqWidth || bitmap.getHeight() > reqHeight){
                Point dimens = Utils.getScaledDimension(bitmap.getWidth(), bitmap.getHeight(),(int)reqWidth, (int)reqHeight);
                float scale = (float)dimens.x / bitmap.getWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, false);
            }
            stage.setBackground(new BitmapDrawable(getResources(), bitmap));
            String path = getNextBackgroundPath();
            stage.setTag(path);
            Utils.WriteImage(bitmap, path);
            if (isRecording)
                showRecorder.RecordFrame(showRecorder.getBackgroundFrame(path));
            savedData.currentBackground = bitmap;
            SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
            prefEditor.putString(BACKGROUND_PATH, path);
            prefEditor.apply();
        }
    } // Called from activity result
    private void setBackground(String path){
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap != null) {
            if (isRecording)
                showRecorder.RecordFrame(showRecorder.getBackgroundFrame(path));
            stage.setBackground(new BitmapDrawable(getResources(), bitmap));
            stage.setTag(path);

            // Save so that background will persist
            savedData.currentBackground = bitmap;
            SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
            prefEditor.putString(BACKGROUND_PATH, path);
            prefEditor.apply();
        }
    }
    private void setBackground(final String path, int reqWidth, int reqHeight){
        Bitmap bitmap = Utils.decodedSampledBitmapFromFile(new File(path), reqWidth, reqHeight);
        if (bitmap != null) {
            if (isRecording)
                showRecorder.RecordFrame(showRecorder.getBackgroundFrame(path));
            if (bitmap.getWidth() > reqWidth || bitmap.getHeight() > reqHeight){
                Point dimens = Utils.getScaledDimension(bitmap.getWidth(), bitmap.getHeight(),(int)reqWidth, (int)reqHeight);
                float scale = (float)dimens.x / bitmap.getWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, false);
                final Bitmap scaledBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Utils.WriteImage(scaledBitmap, path);
                    }
                }).start();
            }
            stage.setBackground(new BitmapDrawable(getResources(), bitmap));
            stage.setTag(path);

            // Save so that background will persist
            savedData.currentBackground = bitmap;
            SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
            prefEditor.putString(BACKGROUND_PATH, path);
            prefEditor.apply();
        }
    }
    private String getNextBackgroundPath(){
        Calendar c = Calendar.getInstance();
        String fileName = "background_" + c.getTime().toString() + ".png";
        fileName = fileName.replaceAll("[|?*<\":>+\\[\\]/']", "_");
        File file = new File(backgroundDir, fileName);
        Log.d(LOG_TAG, file.getAbsolutePath());
        return file.getAbsolutePath();
    }
    private void ShowGetNewImagePopup(){
        View layout = findViewById(R.id.new_image_popup);
        if (layout == null) {
            layout = getLayoutInflater().inflate(R.layout.new_image_popup, null);
            int width = 200;
            layout.setMinimumWidth(width);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layout.getLayoutParams();
            if (params == null) {
                params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.CENTER_IN_PARENT, rootLayout.getId());
            }
            layout.setLayoutParams(params);
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                Button cameraButton = (Button) layout.findViewById(R.id.camera_button);
                cameraButton.setVisibility(View.GONE);
            }
            // Hide blank image option
            View blankImageView = layout.findViewById(R.id.blank_image_layout);
            blankImageView.setVisibility(View.GONE);

            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.pop_in);
            if (rootLayout.getWidth() == 0)
                set.setStartDelay(200);
            rootLayout.addView(layout);
            set.setTarget(layout);
            set.start();
        }

    }
    public void CloseGetNewImagePopup(View v){
        final View layout = findViewById(R.id.new_image_popup);
        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.pop_out);
        set.setTarget(layout);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                rootLayout.removeView(layout);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        set.start();
    }
    public void NewGalleryImageClick(View v){
        launchGetPicIntent();
    }
    private void launchGetPicIntent(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_GET);
        }
    }
    public void NewCameraImageClick(View v){
        launchCameraIntent();
    }
    private void launchCameraIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            cameraCapturePath = getNextBackgroundPath();
            File photoFile = new File(cameraCapturePath);
            // Continue only if the File was successfully created
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                    Uri.fromFile(photoFile));
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

        }
    }


    // Puppet Library methods
    public void ShowPuppetLibrary(View v){
        if (!isLibraryOpen) {
            if (isBackgroundLibraryOpen) CloseBGPopup();
            if (puppetActionMode != null) puppetActionMode.finish();
            isLibraryOpen = true;
            // Prepare library views
            final View layout = getLayoutInflater().inflate(R.layout.puppet_library, null);
            ImageButton closeButton = (ImageButton) layout.findViewById(R.id.puppet_library_close_button);
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ClosePuppetLibrary();
                }
            });
            ImageButton trashButton = (ImageButton) layout.findViewById(R.id.puppet_trash);
            trashButton.setOnDragListener(new TrashDropListener());
            puppetListAdapter = new PuppetListAdapter(context, R.layout.puppet_list_item);
            ListView listView = (ListView)layout.findViewById(R.id.puppet_list_view);
            listView.setAdapter(puppetListAdapter);
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    // Prevent library from receiving the drop
                    View library = findViewById(R.id.puppet_library_popup);
                    library.setOnDragListener(new NoDropDragListener());
                    View v = view.findViewById(R.id.list_item_puppet_thumb);
                    StartLibraryDrag(v, getPathFromName((String) v.getTag()), i);
                    return false;
                }
            });
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    View v = view.findViewById(R.id.list_item_puppet_thumb);
                    File f = new File(getPathFromName((String) v.getTag()));
                    Puppet p = new Puppet(context, null);
                    Utils.ReadPuppetFromFile(p, f);
                    addPuppetToStage(p, stage.getWidth() / 2 - p.getTotalWidth() / 2, stage.getHeight() / 2 - p.getTotalHeight() / 2);
                    // Animate disappearance of item
                    AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.pop_out);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            puppetListAdapter.removeItem(i);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
                    set.setTarget(view);
                    set.start();

                }
            });

            // Setup size and posistion of popup window
            int width = Math.min(250, rootLayout.getWidth());
            int height = 400;
            layout.setMinimumHeight(height);
            layout.setMinimumWidth(width);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            //params.setMargins(rootLayout.getWidth() / 2 - width / 2, rootLayout.getHeight() / 2 - height / 2, 0, 0);
            layout.setLayoutParams(params);

            // Drag listener to catch drops inside the popup window
            layout.setOnDragListener(new LibraryPuppetDragEventListener());

            // Drag listener to receive puppets dropped on the stage
            stage.setOnDragListener(new StagePuppetDragEventListener());

            // Touch listeners to start drag events for puppets on stage
            for (int i = 0; i < stage.getChildCount(); i++) {
                stage.getChildAt(i).setOnTouchListener(new View.OnTouchListener() {
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
                        for (final File f : files) {
                            final Puppet p = new Puppet(context, null);
                            Utils.ReadPuppetFromFile(p, f);
                            if (stage.findViewWithTag(p.getName()) == null) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        puppetListAdapter.add(f);

                                    }
                                });
                            }
                        }
                    }
                }
            };
            new Thread(loadPuppetsThread).start();

            // Start the animation and show the popup
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.flip_in_from_right);
            rootLayout.addView(layout);
            layout.setPivotX(500);
            layout.setPivotY(rootLayout.getHeight() / 2);
            set.setTarget(layout);
            set.start();
        }
    }
    private void ClosePuppetLibrary(){
        final View layout = findViewById(R.id.puppet_library_popup);
        if (layout != null && isLibraryOpen) {
            isLibraryOpen = false;
            if (!isControlPressed){
                GoToPerformance(null);
            }
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.flip_out_to_right);
            layout.setPivotX(500);
            layout.setPivotY(rootLayout.getHeight() / 2);
            set.setTarget(layout);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    rootLayout.removeView(layout);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            set.start();
        }
    }
    public void StartLibraryDrag(View v, String path, int index){
        ClipData.Item pathItem = new ClipData.Item(path);
        String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData dragData = new ClipData("PUPPET_PATH", mime_type, pathItem);
        dragData.addItem(new ClipData.Item(String.valueOf(index)));
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
                    // Remove puppet from library
                    item = event.getClipData().getItemAt(1);
                    int index = Integer.parseInt(item.getText().toString());
                    puppetListAdapter.removeItem(index);
                    // Load pupppet and added to stage
                    File f = new File(path);
                    Puppet p = new Puppet(context, null);
                    Utils.ReadPuppetFromFile(p, f);
                    addPuppetToStage(p, (int) event.getX() - p.getTotalWidth() / 2, (int) event.getY() - p.getTotalHeight() / 2);
                    // Re-attach drag listener so the library receive drops again
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
                    int index = Integer.parseInt(item.getText().toString());
                    Puppet p = (Puppet)stage.getChildAt(index);
                    ListView flipper = (ListView)findViewById(R.id.puppet_list_view);
                    puppetListAdapter.add(p);
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
                    if (isLibraryOpen) {
                        View library = findViewById(R.id.puppet_library_popup);
                        if (library != null)
                            library.setOnDragListener(new LibraryPuppetDragEventListener());
                        stage.setOnDragListener(new StagePuppetDragEventListener());
                    }
                    return false;
                default:
                    return false;
            }
        }
    }
    protected class TrashDropListener implements View.OnDragListener{
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // Animate view to indicate it can receive the drop
                    AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.grow);
                    set.setTarget(v);
                    set.start();
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    AnimatorSet shrink = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.shrink_to_1x);
                    shrink.setTarget(v);
                    shrink.start();
                    return true;
                case DragEvent.ACTION_DROP:
                    v.setScaleX(1f);
                    v.setScaleY(1f);
                    ClipData.Item item = event.getClipData().getItemAt(0);
                    String data = item.getText().toString();
                    if (event.getClipData().getItemCount() == 1){
                        int index = Integer.parseInt(data);
                        Puppet p = (Puppet)stage.getChildAt(index);
                        File file = new File(getPathFromName(p.getName()));
                        RemovePuppetFromStage(p);
                        if (file.isFile())
                            if (!file.delete()) Log.e(LOG_TAG, "error deleting file");
                    } else {
                        // Remove puppet from library
                        item = event.getClipData().getItemAt(1);
                        int index = Integer.parseInt(item.getText().toString());
                        puppetListAdapter.removeItem(index);
                        File file = new File(data);
                        if (file.isFile())
                            if (!file.delete()) Log.e(LOG_TAG, "error deleting file");
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
    private void addPuppetToStage(final Puppet p, int x, int y){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(x, y, -250, -250);
        p.setLayoutParams(params);
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
        String path = getPathFromName(p.getName());
        p.setPath(path);
        if (isRecording) showRecorder.addPuppetToShow(p);
        p.setOnStage(true);
        p.setVisibility(View.GONE);
        stage.addView(p);
        Animation popIn = AnimationUtils.loadAnimation(context, R.anim.anim_pop_in);
        popIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                p.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                p.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        p.startAnimation(popIn);
        puppetsOnStage.add(path);
        SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
        prefEditor.apply();
    }
    private void RemovePuppetFromStage(final Puppet p){
        puppetsOnStage.remove(p.getPath());
        SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
        prefEditor.apply();
        // Animate the puppet being removed
        Animation popOut = AnimationUtils.loadAnimation(context, R.anim.anim_pop_out);
        p.startAnimation(popOut);
        stage.removeView(p);
    }
    private String getPathFromName(String name){
        return storageDir.getPath() + "/" + name + getResources().getString(R.string.puppet_extension);
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
        Puppet puppet = new Puppet(context, null);
        Utils.ReadPuppetFromFile(puppet, new File(filePath));
        puppet.setOnTouchListener(backstageListener);
        puppet.setTag(puppet.getName());
        puppetsOnStage.add(puppet.getPath());
        SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
        prefEditor.apply();
        stage.addView(puppet);
        GoToPerformance(null);
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
                ListView flipper = (ListView)findViewById(R.id.puppet_list_view);
                View selection = flipper.getSelectedView();
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
    private class PuppetListAdapter extends BaseAdapter {
        private ArrayList<Puppet> puppets;
        private LayoutInflater mInflater;
        private Context context;
        private int resource_id;
        public PuppetListAdapter(Context context, int resource_id) {
            this.context = context;
            this.resource_id = resource_id;
            puppets = new ArrayList<>();
        }

        public void addAll(File[] files) {
            for (File f : files) {
                final Puppet p = new Puppet(context, null);
                Utils.ReadPuppetFromFile(p, f);
                puppets.add(p);
            }
            notifyDataSetChanged();
        }

        public void add(File file){
            final Puppet p = new Puppet(context, null);
            Utils.ReadPuppetFromFile(p, file);
            puppets.add(p);
            notifyDataSetChanged();
        }
        public void add(Puppet p){
            puppets.add(p);
            notifyDataSetChanged();
        }
        @Override
        public int getCount() {
            return puppets.size();
        }

        @Override
        public Puppet getItem(int position) {
            return puppets.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public void removeItem(int position) {
            puppets.remove(position);
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }
                final ImageView thumbnail = (ImageView) convertView.findViewById(R.id.list_item_puppet_thumb);
                thumbnail.setBackground(new BitmapDrawable(context.getResources(), puppets.get(position).getThumbnail()));
                thumbnail.setTag(puppets.get(position).getName());

            // Animate addition of view
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.pop_in);
            set.setTarget(convertView);
            set.setStartDelay(position * 50);
            set.start();
            return convertView;
        }
    }
    private class BitmapFileListAdapter extends BaseAdapter {
        private ArrayList<File> files;
        private ArrayList<Bitmap> thumbs;
        private LayoutInflater mInflater;
        private Context context;
        private int resource_id;
        public BitmapFileListAdapter(Context context, int resource_id) {
            this.context = context;
            this.resource_id = resource_id;
            files = new ArrayList<>();
            thumbs = new ArrayList<>();
        }

        public void addAll(File[] files) {
            for (File f: files){
                add(f);
                notifyDataSetChanged();
            }
        }

        public void add(File file){
            files.add(file);
            Bitmap bitmap = Utils.decodedSampledBitmapFromFile(file, 320, 320);
            if (bitmap.getWidth() > 320 || bitmap.getHeight() > 320) {
                Point dimens = Utils.getScaledDimension(bitmap.getWidth(), bitmap.getHeight(), 320, 320);
                float scale = (float) dimens.x / bitmap.getWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, false);
            }
            Log.d(LOG_TAG, "thumbnail size: " + bitmap.getWidth() + ", " + bitmap.getHeight());
            thumbs.add(bitmap);
            notifyDataSetChanged();
        }
        public void add(Bitmap b, File f){
            if (b.getWidth() > 320 || b.getHeight() > 320) {
                Point dimens = Utils.getScaledDimension(b.getWidth(), b.getHeight(), 320, 320);
                float scale = (float) dimens.x / b.getWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
            }
            Log.d(LOG_TAG, "thumbnail size: " + b.getWidth() + ", " + b.getHeight());
            thumbs.add(b);
            files.add(f);
            notifyDataSetChanged();
        }
        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public File getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public void removeItem(int position) {
            thumbs.remove(position);
            files.remove(position);
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }
            View view = convertView.findViewById(R.id.list_item_puppet_thumb);
            view.setBackground(new BitmapDrawable(getResources(), thumbs.get(position)));
            // Animate addition of view
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.pop_in);
            set.setTarget(convertView);
            set.setStartDelay(position * 50);
            set.start();
            return convertView;
        }
    }
}
