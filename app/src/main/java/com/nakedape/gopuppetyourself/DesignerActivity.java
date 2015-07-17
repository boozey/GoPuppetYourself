package com.nakedape.gopuppetyourself;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;


public class DesignerActivity extends Activity {

    private static final String LOG_TAG = "DesignerActivity";
    private static final int REQUEST_IMAGE_GET = 4001;
    private static final int REQUEST_IMAGE_CAPTURE = 4002;
    private static final int BRUSH_SIZE_S = 6;
    private static final int BRUSH_SIZE_M = 12;
    private static final int BRUSH_SIZE_L = 18;
    private static final int MAX_BRUSH_SIZE = 64;

    private PuppetDesigner designer;
    private Context context = this;
    private View popup;
    private RelativeLayout rootLayout;
    private View.OnTouchListener backgroundTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()){
                case MotionEvent.ACTION_DOWN:
                    cancelPopup();
                    break;
            }
            return true;
        }
    };
    private int colorSelection = R.id.black;
    private int paletteBrushSize = BRUSH_SIZE_M;
    private Puppet puppet;
    private File puppetDir;
    private File appDir;
    private int stageIndex = -1;
    private String cameraCapturePath;
    private ActionMode mActionMode;
    private View brushSizeBar;
    private View undoButton;
    private boolean isSecondActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_designer);
        rootLayout = (RelativeLayout)findViewById(R.id.relative_layout);
        designer = (PuppetDesigner)findViewById(R.id.designer);
        undoButton = findViewById(R.id.undo_button);

        // Prepare brush size bar
        brushSizeBar = getLayoutInflater().inflate(R.layout.brush_size_bar, null);
        brushSizeBar.setVisibility(View.INVISIBLE);
        RelativeLayout.LayoutParams barParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        rootLayout.addView(brushSizeBar, barParams);

        ActionBar actionBar = getActionBar();
        if (actionBar != null){
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        }

        // Prepare puppet storage directory for access
        if (Utils.isExternalStorageWritable()){
            puppetDir = new File(getExternalFilesDir(null), getResources().getString(R.string.puppet_directory));
            if (!puppetDir.exists())
                if (!puppetDir.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
            appDir = getExternalFilesDir(null);
        }
        else {
            puppetDir = new File(getFilesDir(), getResources().getString(R.string.puppet_directory));
            if (!puppetDir.exists())
                if (!puppetDir.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
            appDir = getFilesDir();
        }

        // Load data from intent or open file picker
        Intent intent = getIntent();
        if (intent.hasExtra(MainActivity.PUPPET_PATH)){
            puppet = new Puppet(context, null);
            Utils.ReadPuppetFromFile(puppet, new File(intent.getStringExtra(MainActivity.PUPPET_PATH)));
            designer.loadPuppet(puppet);
            stageIndex = intent.getIntExtra(MainActivity.PUPPET_INDEX, -1);
            EditText editText = (EditText)findViewById(R.id.puppet_name);
            editText.setText(puppet.getName());
        }
        else {
            designer.post(new Runnable() {
                @Override
                public void run() {
                    View view = findViewById(R.id.designer_frame_layout);
                    designer.CreatBlankImage(view.getWidth(), view.getHeight());
                    ShowGetNewImagePopup(300);
                }
            });
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()){ // Release resources used by puppet designer
            designer.release();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_designer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id){
            case R.id.action_draw:
                hideNameBar();
                showUndoButton();
                if (mActionMode != null) mActionMode.finish();
                mActionMode = startActionMode(drawActionCallback);
                designer.setIsDrawMode(true);
                break;
            case R.id.action_edit_image:
                if (mActionMode != null) mActionMode.finish();
                mActionMode = startActionMode(imageActionCallback);
                break;
            case R.id.action_edit_orientation:
                if (mActionMode != null) mActionMode.finish();
                mActionMode = startActionMode(protraitActionCallback);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_GET && resultCode == RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            CloseGetNewImagePopup(null);
            NewPuppet(fullPhotoUri);
        }
        else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK){
            CloseGetNewImagePopup(null);
            NewPuppet();
        }
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e){
        switch (keycode){
            case KeyEvent.KEYCODE_BACK:
                View v = findViewById(R.id.new_image_popup);
                if (v != null) {
                    CloseGetNewImagePopup(null);
                    return true;
                } else if (!designer.isSaved()){
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage(R.string.alert_dialog_message_unsaved);
                    builder.setTitle(R.string.alert_dialog_title_unsaved);
                    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            finish();
                        }
                    });
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Save(null);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                else {
                    return super.onKeyDown(keycode, e);
                }
            default:
                return super.onKeyDown(keycode, e);
        }
    }

    private void ShowGetNewImagePopup(int delay){
        View layout = findViewById(R.id.new_image_popup);
        if (layout == null) {
            layout = getLayoutInflater().inflate(R.layout.new_image_popup, null);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layout.getLayoutParams();
            if (params == null) {
                params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.CENTER_IN_PARENT, designer.getId());
            }
            layout.setLayoutParams(params);
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                Button cameraButton = (Button) layout.findViewById(R.id.camera_button);
                cameraButton.setVisibility(View.GONE);
            }

            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.pop_in);
            layout.setAlpha(0f);
            rootLayout.addView(layout);
            set.setTarget(layout);
            set.setStartDelay(delay);
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
            File photoFile = new File(appDir, "capture.jpg");
            cameraCapturePath = photoFile.getPath();
            // Continue only if the File was successfully created
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }
    public void NewBlankImageClick(View v){
        View view = findViewById(R.id.designer_frame_layout);
        designer.CreatBlankImage(view.getWidth(), view.getHeight());
        CloseGetNewImagePopup(v);
    }

    private void NewPuppet(Uri imageUri){
        Bitmap bitmap = null;
        // Release all the memory used by the designer before trying to load a new image
        designer.release();
        View view = findViewById(R.id.designer_frame_layout);
        try {
            try {
                bitmap = Utils.decodeSampledBitmapFromContentResolver(getContentResolver(), imageUri, view.getWidth(), view.getHeight());
            } catch (OutOfMemoryError e) {
                Toast.makeText(context, getString(R.string.toast_oom_scaling), Toast.LENGTH_LONG).show();
                bitmap = Utils.decodeSampledBitmapFromContentResolver(getContentResolver(), imageUri, view.getWidth() / 2, view.getHeight() / 2);
            }
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(context, "File loading error", Toast.LENGTH_LONG).show();
        }
        if (bitmap != null){
            if (bitmap.getWidth() > view.getHeight() || bitmap.getHeight() > view.getHeight()){
                Point dimens = Utils.getScaledDimension(bitmap.getWidth(), bitmap.getHeight(), view.getWidth(), view.getHeight());
                float scale = (float)dimens.x / bitmap.getWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, false);
            }
                designer.SetNewImage(bitmap);
                Log.d(LOG_TAG, "Bitmap width = " + String.valueOf(bitmap.getWidth()));
        }
    }
    private void NewPuppet(){
        // Release all the memory used by the designer before trying to load a new image
        designer.release();
        // Get the dimensions of the View
        View view = findViewById(R.id.designer_frame_layout);
        int targetW = (int)view.getWidth();
        int targetH = (int)view.getHeight();
        Bitmap bitmap = null;
        try {
            bitmap = Utils.decodedSampledBitmapFromFile(new File(cameraCapturePath), targetW, targetH);
        } catch (OutOfMemoryError e){
            Toast.makeText(context, getString(R.string.toast_oom_scaling), Toast.LENGTH_LONG).show();
            bitmap = Utils.decodedSampledBitmapFromFile(new File(cameraCapturePath), targetW / 2, targetH / 2);
        }
        if (bitmap != null) {
            if (bitmap.getWidth() > view.getHeight() || bitmap.getHeight() > view.getHeight()){
                Point dimens = Utils.getScaledDimension(bitmap.getWidth(), bitmap.getHeight(), view.getWidth(), view.getHeight());
                float scale = (float)dimens.x / bitmap.getWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, false);
            }
            designer.SetNewImage(bitmap);
            Log.d(LOG_TAG, "Bitmap width = " + String.valueOf(bitmap.getWidth()));
        }
    }

    public void Save(View v){
        EditText text = (EditText)findViewById(R.id.puppet_name);
        String newName = text.getText().toString();
        // If there is no name set the puppet's name to unnamed
        if (newName.length() <= 0){
            newName = "Unnamed";
        }
        // If this was an edit and the puppet's name hasn't changed, save save and finish
        if (puppet != null && newName.equals(puppet.getName()))
            FinishAndSave(newName);
        // If the puppet name has changed, prompt if there is already a puppet with this name
        final String name = newName;
        File[] files = puppetDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().equals(name + getString(R.string.puppet_extension)) || file.getName().equals(name);
            }
        });
        if (files.length > 0){
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.alert_dialog_message_overwrite);
            builder.setTitle(R.string.alert_dialog_title_overwrite);
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    FinishAndSave(name);
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        else {
            FinishAndSave(newName);
        }

    }
    private void FinishAndSave(String puppetName){
        // Delete the old puppet file if it exists
        if (puppet != null){
            File oldFile = new File(puppetDir, puppet.getName() + getString(R.string.puppet_extension));
            if (oldFile.isFile())
                if (oldFile.delete()) Log.i(LOG_TAG, "removed previous puppet file");
        }
        // Create puppet
        puppet = new Puppet(context, null);
        puppet.setOrientation(designer.getOrientation());
        puppet.setRotation(designer.getRotation());
        puppet.setImages(designer.getUpperJaw(), designer.getLowerJaw(), designer.getUpperJawPivotPoint(), designer.getLowerJawPivotPoint());
        puppet.setName(puppetName);
        // Save puppet to storage directory
        File saveFile = new File(puppetDir, puppet.getName() + getResources().getString(R.string.puppet_extension));
        String filePath = Utils.WritePuppetToFile(puppet, saveFile);
        // Pass file name back to MainActivity
        Intent data = new Intent();
        data.putExtra(MainActivity.PUPPET_PATH, filePath);
        Log.d(LOG_TAG, "Saved to " + filePath);
        // Pass index back in case this was an edit
        data.putExtra(MainActivity.PUPPET_INDEX, stageIndex);

        setResult(MainActivity.RESULT_OK, data);
        finish();
    }

    private void hideNameBar(){
        final View nameBar = findViewById(R.id.puppet_name);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(nameBar, "TranslationY", 0, -nameBar.getHeight());
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(nameBar, "alpha", 1f, 0f);
        set.playTogether(slideUp, fadeOut);
        set.setTarget(nameBar);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                nameBar.setVisibility(View.INVISIBLE);
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
    private void showNameBar(){
        View nameBar = findViewById(R.id.puppet_name);
        nameBar.setVisibility(View.VISIBLE);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator slideDown = ObjectAnimator.ofFloat(nameBar, "TranslationY", -nameBar.getHeight(), 0);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(nameBar, "alpha", 0f, 1f);
        set.playTogether(slideDown, fadeIn);
        set.setTarget(nameBar);
        set.start();
        hideUndoButton();
    }
    private void hideUndoButton(){
        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_out);
        set.setTarget(undoButton);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                undoButton.setVisibility(View.GONE);
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
    private void showUndoButton(){
        undoButton.setVisibility(View.VISIBLE);
        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_in);
        set.setTarget(undoButton);
        set.start();
    }

    // Draw Action Mode methods
    private ActionMode.Callback drawActionCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            actionMode.getMenuInflater().inflate(R.menu.menu_designer_draw, menu);

            // Configure brush bar view
            View brushBar = getLayoutInflater().inflate(R.layout.brush_size_bar, null);
            brushBar.findViewById(R.id.close_button).setVisibility(View.GONE);

            // Set slider to current value
            SeekBar slider = (SeekBar)brushBar.findViewById(R.id.brush_slider);
            slider.setMax(MAX_BRUSH_SIZE);
            slider.setProgress(paletteBrushSize);

            // Set brush view to current value
            final View view = brushBar.findViewById(R.id.brush_size);
            designer.setStrokeWidth((float) paletteBrushSize);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)view.getLayoutParams();
            params.width = paletteBrushSize;
            params.height = paletteBrushSize;
            params.width = paletteBrushSize;
            params.height = paletteBrushSize;
            int margin = (MAX_BRUSH_SIZE - paletteBrushSize) / 2;
            params.setMargins(margin, margin, margin, margin);
            view.setLayoutParams(params);

            // Listener to update brush view and palatteBrushSize
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    paletteBrushSize = i;
                    designer.setStrokeWidth((float) paletteBrushSize);
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
                    params.width = i;
                    params.height = i;
                    params.width = paletteBrushSize;
                    params.height = paletteBrushSize;
                    int margin = (MAX_BRUSH_SIZE - paletteBrushSize) / 2;
                    params.setMargins(margin, margin, margin, margin);
                    view.setLayoutParams(params);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            brushBar.setAlpha(0f);
            actionMode.setCustomView(brushBar);
            AnimatorSet fadeIn = (AnimatorSet)AnimatorInflater.loadAnimator(context, R.animator.fade_in);
            fadeIn.setTarget(brushBar);
            fadeIn.start();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            switch (menuItem.getItemId()){
                case R.id.action_palette:
                    showPalette();
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mActionMode = null;
            cancelPopup();
            designer.setMode(PuppetDesigner.MODE_NO_TOUCH);
            hideUndoButton();
            showNameBar();
        }
    };
    public void showPalette(){
        // Inflate the popup_layout.xml
        if (popup == null) {
            designer.setOnTouchListener(backgroundTouchListener);
            final LinearLayout viewGroup = (LinearLayout) findViewById(R.id.palette_popup);
            LayoutInflater layoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            popup = layoutInflater.inflate(R.layout.palette_popup, viewGroup);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            popup.setLayoutParams(params);
            rootLayout.addView(popup);
            popup.requestFocus();
        }
        else {
            cancelPopup();
        }
    }
    private void cancelPopup(){
        if (popup != null) {
            rootLayout.removeView(popup);
            popup = null;
            designer.setOnTouchListener(null);
        }
    }
    public void ColorSelect(View v){
        View brushSize = findViewById(R.id.brush_size);
        colorSelection = v.getId();
        switch (colorSelection){
            case R.id.black:
                designer.setColor(getResources().getColor(R.color.black));
                brushSize.setBackground(getResources().getDrawable(R.drawable.black_oval));
                break;
            case R.id.blue:
                designer.setColor(getResources().getColor(R.color.blue));
                brushSize.setBackground(getResources().getDrawable(R.drawable.blue_oval));
                break;
            case R.id.brown:
                designer.setColor(getResources().getColor(R.color.brown));
                brushSize.setBackground(getResources().getDrawable(R.drawable.brown_oval));
                break;
            case R.id.green:
                designer.setColor(getResources().getColor(R.color.green));
                brushSize.setBackground(getResources().getDrawable(R.drawable.green_oval));
                break;
            case R.id.light_blue:
                designer.setColor(getResources().getColor(R.color.light_blue));
                brushSize.setBackground(getResources().getDrawable(R.drawable.light_blue_oval));
                break;
            case R.id.light_brown:
                designer.setColor(getResources().getColor(R.color.light_brown));
                brushSize.setBackground(getResources().getDrawable(R.drawable.light_brown_oval));
                break;
            case R.id.light_green:
                designer.setColor(getResources().getColor(R.color.light_green));
                brushSize.setBackground(getResources().getDrawable(R.drawable.light_green_oval));
                break;
            case R.id.orange:
                designer.setColor(getResources().getColor(R.color.orange));
                brushSize.setBackground(getResources().getDrawable(R.drawable.orange_oval));
                break;
            case R.id.pink:
                designer.setColor(getResources().getColor(R.color.pink));
                brushSize.setBackground(getResources().getDrawable(R.drawable.pink_oval));
                break;
            case R.id.red:
                designer.setColor(getResources().getColor(R.color.red));
                brushSize.setBackground(getResources().getDrawable(R.drawable.red_oval));
                break;
            case R.id.violet:
                designer.setColor(getResources().getColor(R.color.violet));
                brushSize.setBackground(getResources().getDrawable(R.drawable.violet_oval));
                break;
            case R.id.white:
                designer.setColor(getResources().getColor(R.color.white));
                brushSize.setBackground(getResources().getDrawable(R.drawable.white_oval));
                break;
            case R.id.yellow:
                designer.setColor(getResources().getColor(R.color.yellow));
                brushSize.setBackground(getResources().getDrawable(R.drawable.yellow_oval));
                break;
            case R.id.indigo:
                designer.setColor(getResources().getColor(R.color.indigo));
                brushSize.setBackground(getResources().getDrawable(R.drawable.indigo_oval));
                break;
            case R.id.light_grey:
                designer.setColor(getResources().getColor(R.color.light_grey));
                brushSize.setBackground(getResources().getDrawable(R.drawable.light_grey_oval));
                break;
            case R.id.dark_grey:
                designer.setColor(getResources().getColor(R.color.dark_grey));
                brushSize.setBackground(getResources().getDrawable(R.drawable.dark_grey_oval));
                break;
            case R.id.eraser:
                designer.setEraseMode(true);
                brushSize.setBackground(getResources().getDrawable(R.drawable.dashed_oval));
                break;
        }
        cancelPopup();
    }

    // Image Action Mode methods
    private ActionMode.Callback imageActionCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            actionMode.getMenuInflater().inflate(R.menu.menu_designer_image_edit, menu);
            hideNameBar();
            showUndoButton();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            switch (menuItem.getItemId()){
                case R.id.action_erase:
                    designer.setBackgroundErase();
                    showBrushSizeBar();
                    return true;
                case R.id.action_magic_erase:
                    hideBrushSizeBar();
                    designer.setMagicEraseMode(true);
                    return true;
                case R.id.action_cut_path:
                    hideBrushSizeBar();
                    designer.setCutPathMode();
                    return true;
                case R.id.action_flip_horz:
                    hideBrushSizeBar();
                    designer.flipHorz();
                    return true;
                case R.id.action_rotate_left:
                    hideBrushSizeBar();
                    designer.rotateLeft();
                    return true;
                case R.id.action_rotate_right:
                    hideBrushSizeBar();
                    designer.rotateRight();
                    return true;
                case R.id.action_heal:
                    designer.setHealMode(true);
                    showBrushSizeBar();
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mActionMode = null;
            designer.setMode(PuppetDesigner.MODE_NO_TOUCH);
            hideBrushSizeBar();
            showNameBar();
        }
    };
    private void showBrushSizeBar(){
        if (brushSizeBar.getVisibility() != View.VISIBLE) {
            // Configure brush bar view
            brushSizeBar.setBackground(new ColorDrawable(Color.WHITE));
            View closeButton = brushSizeBar.findViewById(R.id.close_button);
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    hideBrushSizeBar();
                }
            });

            // Set slider to current value
            SeekBar slider = (SeekBar) brushSizeBar.findViewById(R.id.brush_slider);
            slider.setMax(MAX_BRUSH_SIZE);
            slider.setProgress(paletteBrushSize);

            // Set brush view to current value
            final View brushSize = brushSizeBar.findViewById(R.id.brush_size);
            designer.setStrokeWidth((float) paletteBrushSize);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) brushSize.getLayoutParams();
            params.width = paletteBrushSize;
            params.height = paletteBrushSize;
            int margin = (MAX_BRUSH_SIZE - paletteBrushSize) / 2;
            params.setMargins(margin, margin, margin, margin);
            brushSize.setLayoutParams(params);

            // Listener to update brush view and palatteBrushSize
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    paletteBrushSize = i;
                    designer.setStrokeWidth((float) paletteBrushSize);
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) brushSize.getLayoutParams();
                    params.width = i;
                    params.height = i;
                    int margin = (MAX_BRUSH_SIZE - i) / 2;
                    params.setMargins(margin, margin, margin, margin);
                    brushSize.setLayoutParams(params);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideDown = ObjectAnimator.ofFloat(brushSizeBar, "TranslationY", -brushSizeBar.getHeight(), 0);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(brushSizeBar, "alpha", 0f, 1f);
            set.playTogether(slideDown, fadeIn);
            set.setTarget(brushSizeBar);
            brushSizeBar.setVisibility(View.VISIBLE);
            set.start();
        }
    }
    private void hideBrushSizeBar(){
        if (brushSizeBar.getVisibility() == View.VISIBLE) {
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideUp = ObjectAnimator.ofFloat(brushSizeBar, "TranslationY", 0, -brushSizeBar.getHeight());
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(brushSizeBar, "alpha", 1f, 0f);
            set.playTogether(slideUp, fadeOut);
            set.setTarget(brushSizeBar);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    brushSizeBar.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            set.start();
        }}

    // Portrait orientation methods
    private ActionMode.Callback protraitActionCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            actionMode.getMenuInflater().inflate(R.menu.menu_designer_portrait, menu);
            hideNameBar();
            hideUndoButton();
            designer.setSelectionMode();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            if (designer.getOrientation() == Puppet.PROFILE_RIGHT){
                MenuItem profileItem = menu.findItem(R.id.action_profile_right);
                profileItem.setIcon(getResources().getDrawable(R.drawable.ic_action_profile_right_selected));
            }else {
                MenuItem profileItem = menu.findItem(R.id.action_profile_left);
                profileItem.setIcon(getResources().getDrawable(R.drawable.ic_action_profile_left_selected));
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            switch (menuItem.getItemId()){
                case R.id.action_profile_right:
                    designer.setOrientation(Puppet.PROFILE_RIGHT);
                    menuItem.setIcon(getResources().getDrawable(R.drawable.ic_action_profile_right_selected));
                    MenuItem leftProfile = actionMode.getMenu().findItem(R.id.action_profile_left);
                    leftProfile.setIcon(getResources().getDrawable(R.drawable.ic_action_profile_left));
                    return true;
                case R.id.action_profile_left:
                    designer.setOrientation(Puppet.PROFILE_LEFT);
                    menuItem.setIcon(getResources().getDrawable(R.drawable.ic_action_profile_left_selected));
                    MenuItem rightProfile = actionMode.getMenu().findItem(R.id.action_profile_right);
                    rightProfile.setIcon(getResources().getDrawable(R.drawable.ic_action_profile_right));
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mActionMode = null;
            designer.setMode(PuppetDesigner.MODE_NO_TOUCH);
            showNameBar();
        }
    };

    // Root control button methods
    public void ControlButtonClick(View v){
        ShowGetNewImagePopup(0);
    }

    // Undo methods
    public void UndoButtonClick(View v){
        designer.Undo();
    }

}
