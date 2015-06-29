package com.nakedape.gopuppetyourself;

import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private Context context;
    private ViewGroup stage;
    private int dx;
    private int dy;
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
    private RelativeLayout rootLayout;
    private boolean isControlPressed = false;
    private boolean isSecondControlShowing = false;
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private GestureDetectorCompat gestureDetector;
    private boolean flingRight, flingLeft, flingUp, flingDown, longPress, scrollLeft, scrollRight, scrollUp, scrollDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
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
        else { // Create a new instance to save the data
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
            switch (motionEvent.getAction()){
                case MotionEvent.ACTION_DOWN:
                    GoBackstage(view);
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
                    return true;
            }
            return false;
        }
    };
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
            BackGroundButtonClick(view);
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
                    MenuItem item = menu.getMenu().findItem(R.id.action_puppet_visible);
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
            }
            return false;
        }
    };
    private void RemovePuppetFromStage(Puppet p){
        stage.removeView(p);
        puppetsOnStage.remove(p.getPath());
        SharedPreferences.Editor prefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        prefEditor.putStringSet(PUPPETS_ON_STAGE, puppetsOnStage);
        prefEditor.apply();
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
                    return true;
                }
                if (flingLeft) {
                    flipper.setOutAnimation(context, R.anim.anim_scale_small_left);
                    flipper.setInAnimation(context, R.anim.anim_scale_up_left);
                    flipper.showNext();
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
                                    image.setTag(p.getPath());
                                    flipper.addView(image);
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

        ClipData.Item pathItem = new ClipData.Item(path);
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
                    image.setTag(p.getPath());
                    ViewFlipper flipper = (ViewFlipper)findViewById(R.id.puppet_flipper);
                    flipper.addView(image);
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

    public void GoToPerformance(View v){
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
        GoToPerformance(null);
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
        Log.d(LOG_TAG, "set background called");
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
        } catch (IOException e){Toast.makeText(this, "Error loading backgournd", Toast.LENGTH_SHORT).show();}
        if (bitmap != null){
            stage.setBackground(new BitmapDrawable(getResources(), bitmap));
            savedData.currentBackground = bitmap;
            Log.d(LOG_TAG, "background changed");
        }
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
