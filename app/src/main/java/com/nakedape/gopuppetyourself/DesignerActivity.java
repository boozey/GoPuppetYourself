package com.nakedape.gopuppetyourself;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class DesignerActivity extends Activity {

    private static final String LOG_TAG = "DesignerActivity";
    private static final int REQUEST_IMAGE_GET = 4001;
    private static final int REQUEST_IMAGE_CAPTURE = 4002;
    private static final int BRUSH_SIZE_S = 6;
    private static final int BRUSH_SIZE_M = 12;
    private static final int BRUSH_SIZE_L = 18;
    private static final int MAX_BRUSH_SIZE = 64;

    private View mainButtonBar, backgroundButtonBar, eraseBackgroundButtonBar, magicEraseButtonBar, portraitButtonBar, healButtonBar;
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
    private ImageButton showBoxButton;
    private Puppet puppet;
    private File puppetDir;
    private File appDir;
    private int stageIndex = -1;
    private String cameraCapturePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_designer);
        rootLayout = (RelativeLayout)findViewById(R.id.relative_layout);
        designer = (PuppetDesigner)findViewById(R.id.designer);
        mainButtonBar = findViewById(R.id.main_buttons);
        backgroundButtonBar = findViewById(R.id.background_button_bar);
        eraseBackgroundButtonBar = findViewById(R.id.bg_erase_bar);
        magicEraseButtonBar = findViewById(R.id.magic_erase_bar);
        portraitButtonBar = findViewById(R.id.portrait_bar);
        healButtonBar = findViewById(R.id.heal_button_bar);

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
            if (designer.getOrientation() == Puppet.PROFILE_LEFT) {
                findViewById(R.id.portrait_edit_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_left));
                findViewById(R.id.portrait_left_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_left_selected));
                findViewById(R.id.portrait_right_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_right));
            }
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
                }
            });
            ShowGetNewImagePopup();
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
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

    private void ShowGetNewImagePopup(){
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

    // Root control button methods
    public void ControlButtonClick(View v){
        ShowGetNewImagePopup();
    }

    // Undo methods
    public void UndoButtonClick(View v){
        designer.Undo();
    }

    // Draw menu methods
    public void BrushClick(View v){
        // Hide button bar and show brush bar
        final View buttonBar = mainButtonBar;
        if (buttonBar.getVisibility() != View.GONE) {
            final View navButton = findViewById(R.id.nav_button);
            // Set slider to current value
            SeekBar slider = (SeekBar)findViewById(R.id.brush_slider);
            slider.setMax(MAX_BRUSH_SIZE);
            slider.setProgress(paletteBrushSize);

            // Set brush view to current value
            final View view = findViewById(R.id.brush_size);
            designer.setStrokeWidth((float) paletteBrushSize);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)view.getLayoutParams();
            params.width = paletteBrushSize;
            params.height = paletteBrushSize;
            view.setLayoutParams(params);

            // Listener to update brush view and palatteBrushSize
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    paletteBrushSize = i;
                    designer.setStrokeWidth((float) paletteBrushSize);
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                    params.width = i;
                    params.height = i;
                    view.setLayoutParams(params);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            // Animate disappearance of button bar and appearance of brush bar
            Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
            scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    designer.setIsDrawMode(true);
                    buttonBar.setVisibility(View.GONE);
                    View brushBar = findViewById(R.id.brush_bar);
                    brushBar.setVisibility(View.VISIBLE);
                    navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_arrow_back));
                    navButton.setVisibility(View.VISIBLE);
                    Animation anim = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in_spin);
                    navButton.startAnimation(anim);
                    navButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            BrushBarDoneClick(view);
                        }
                    });
                    Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                    brushBar.startAnimation(scaleUpRight);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            buttonBar.startAnimation(scaleDownLeft);
            Animation anim = AnimationUtils.loadAnimation(context, R.anim.anim_fade_out_spin);
            navButton.startAnimation(anim);
        }
        else {
            showPalette();
        }

    }
    public void BrushBarDoneClick(View v){
        final View brushBar = findViewById(R.id.brush_bar);
        if (brushBar.getVisibility() != View.GONE) {
            final View navButton = findViewById(R.id.nav_button);
            cancelPopup();
            designer.setSelectionMode(true);
            Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
            scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    brushBar.setVisibility(View.GONE);
                    View navButton = findViewById(R.id.nav_button);
                    navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_menu));
                    Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in_spin);
                    navButton.startAnimation(fadeIn);
                    navButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ControlButtonClick(view);
                        }
                    });
                    View buttonBar = findViewById(R.id.main_buttons);
                    buttonBar.setVisibility(View.VISIBLE);
                    Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                    buttonBar.startAnimation(scaleUpRight);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.anim_fade_out_spin);
            navButton.startAnimation(fadeOut);
            brushBar.startAnimation(scaleDownLeft);
        }
    }
    public void showPalette(){
        // Inflate the popup_layout.xml
        if (popup == null) {
            designer.setOnTouchListener(backgroundTouchListener);
            final LinearLayout viewGroup = (LinearLayout) findViewById(R.id.palette_popup);
            LayoutInflater layoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            popup = layoutInflater.inflate(R.layout.palette_popup, viewGroup);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, R.id.button_bar);
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
            designer.setIsDrawMode(true);
        }
    }
    public void ColorSelect(View v){
        designer.setEraseMode(false);
        designer.cancelCutPathMode();
        designer.cancelBackgroundErase();
        ImageButton brushSize = (ImageButton)findViewById(R.id.brush_size);
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

    // Background menu methods
    public void BackgroundButtonClick(View v){
        // Hide button bar and show background bar
        View buttonBar = null;
        if (mainButtonBar.getVisibility() == View.VISIBLE)
            buttonBar = mainButtonBar;
        else if (magicEraseButtonBar.getVisibility() == View.VISIBLE) {
            buttonBar = magicEraseButtonBar;
            designer.setMagicEraseMode(false);
        }
        else if (eraseBackgroundButtonBar.getVisibility() == View.VISIBLE) {
            buttonBar = eraseBackgroundButtonBar;
            designer.cancelBackgroundErase();
        }
        final View buttonBarFinal = buttonBar;
        final View navButton = findViewById(R.id.nav_button);
        // Animate disappearance of button bar and appearance of background bar
        Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
        scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (buttonBarFinal != null) buttonBarFinal.setVisibility(View.GONE);
                View backgroundBar = findViewById(R.id.background_button_bar);
                backgroundBar.setVisibility(View.VISIBLE);
                navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_arrow_back));
                navButton.setVisibility(View.VISIBLE);
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in_spin);
                navButton.startAnimation(anim);
                navButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        BackgroundBarDoneClick(view);
                    }
                });
                Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                backgroundBar.startAnimation(scaleUpRight);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        Animation spinOut = AnimationUtils.loadAnimation(context, R.anim.anim_fade_out_spin);
        navButton.startAnimation(spinOut);
        if (buttonBarFinal != null) buttonBarFinal.startAnimation(scaleDownLeft);
    }
    public void BackgroundBarDoneClick(View v){
        UnselectCutPathButton();
        final View backgroundBar = findViewById(R.id.background_button_bar);
        if (backgroundBar.getVisibility() != View.GONE) {
            final View navButton = findViewById(R.id.nav_button);
            Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
            scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    backgroundBar.setVisibility(View.GONE);
                    View navButton = findViewById(R.id.nav_button);
                    navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_menu));
                    Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in_spin);
                    navButton.startAnimation(fadeIn);
                    navButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ControlButtonClick(view);
                        }
                    });
                    View buttonBar = findViewById(R.id.main_buttons);
                    buttonBar.setVisibility(View.VISIBLE);
                    Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                    buttonBar.startAnimation(scaleUpRight);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            Animation spinOut = AnimationUtils.loadAnimation(context, R.anim.anim_spin_ccw);
            navButton.startAnimation(spinOut);
            backgroundBar.startAnimation(scaleDownLeft);
        }
    }
    public void EraseBackGroundClick(View v){
        UnselectCutPathButton();
        // Hide background bar and show background erase bar
        final View buttonBar = backgroundButtonBar;
        final View navButton = findViewById(R.id.nav_button);
        // Set slider to current value
        SeekBar slider = (SeekBar)findViewById(R.id.erase_brush_slider);
        slider.setMax(MAX_BRUSH_SIZE);
        slider.setProgress(paletteBrushSize);

        // Set brush view to current value
        final View view = findViewById(R.id.erase_brush_size);
        designer.setStrokeWidth((float) paletteBrushSize);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)view.getLayoutParams();
        params.width = paletteBrushSize;
        params.height = paletteBrushSize;
        view.setLayoutParams(params);

        // Listener to update brush view and palatteBrushSize
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                paletteBrushSize = i;
                designer.setStrokeWidth((float) paletteBrushSize);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = i;
                params.height = i;
                view.setLayoutParams(params);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Animate disappearance of button bar and appearance of background bar
        Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
        scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                designer.setBackgroundErase();
                buttonBar.setVisibility(View.GONE);
                View newButtonBar = eraseBackgroundButtonBar;
                newButtonBar.setVisibility(View.VISIBLE);
                View navButton = findViewById(R.id.nav_button);
                navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_arrow_back));
                navButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        BackgroundButtonClick(view);
                    }
                });
                Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                newButtonBar.startAnimation(scaleUpRight);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        Animation spinOut = AnimationUtils.loadAnimation(context, R.anim.anim_fade_out_spin);
        navButton.startAnimation(spinOut);
        buttonBar.startAnimation(scaleDownLeft);

    }
    public void MagicEraseBGClick(View v){
        UnselectCutPathButton();
        // Hide background bar and show background erase bar
        final View buttonBar = findViewById(R.id.background_button_bar);
        final View navButton = findViewById(R.id.nav_button);
        final TextView thresholdText = (TextView)findViewById(R.id.threshold_text);

        // Listener to update brush view and palatteBrushSize
        SeekBar slider = (SeekBar)findViewById(R.id.magic_erase_slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                thresholdText.setText(String.valueOf(i));
                designer.setColorSimilaritySensitivity(0.5 + (double) i / 100);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Animate disappearance of old button bar and appearance of new button bar
        Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
        scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                designer.setMagicEraseMode(true);
                buttonBar.setVisibility(View.GONE);
                View newButtonBar = findViewById(R.id.magic_erase_bar);
                newButtonBar.setVisibility(View.VISIBLE);
                View navButton = findViewById(R.id.nav_button);
                navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_arrow_back));
                navButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        BackgroundButtonClick(view);
                    }
                });
                Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                newButtonBar.startAnimation(scaleUpRight);
                Toast.makeText(context, getString(R.string.toast_magic_erase), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        Animation spinOut = AnimationUtils.loadAnimation(context, R.anim.anim_spin_ccw);
        navButton.startAnimation(spinOut);
        buttonBar.startAnimation(scaleDownLeft);
    }
    public void FlipHorzClick(View v){
        designer.flipHorz();
    }
    public void RotatRightClick(View v){
        designer.rotateRight();
    }
    public void RotateLeftClick(View v){
        designer.rotateLeft();
    }
    public void CutPathClick(View v){
        if (designer.getMode().equals(PuppetDesigner.MODE_CUT_PATH)){
            designer.setMode(PuppetDesigner.MODE_NO_TOUCH);
            v.setBackground(getResources().getDrawable(R.drawable.ic_action_cut));
        } else{
            designer.setCutPathMode();
            v.setBackground(getResources().getDrawable(R.drawable.ic_action_cut_selected));
            Toast.makeText(context, getString(R.string.toast_cut_path), Toast.LENGTH_SHORT).show();
        }
    }
    public void HealClick(View v){
        UnselectCutPathButton();
        // Hide background bar and show background erase bar
        final View buttonBar = backgroundButtonBar;
        final View navButton = findViewById(R.id.nav_button);
        // Set slider to current value
        SeekBar slider = (SeekBar)findViewById(R.id.erase_brush_slider);
        slider.setMax(MAX_BRUSH_SIZE);
        slider.setProgress(paletteBrushSize);

        // Set brush view to current value
        final View view = findViewById(R.id.erase_brush_size);
        designer.setStrokeWidth((float) paletteBrushSize);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)view.getLayoutParams();
        params.width = paletteBrushSize;
        params.height = paletteBrushSize;
        view.setLayoutParams(params);

        // Listener to update brush view and palatteBrushSize
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                paletteBrushSize = i;
                designer.setStrokeWidth((float) paletteBrushSize);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
                params.width = i;
                params.height = i;
                view.setLayoutParams(params);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Animate disappearance of button bar and appearance of background bar
        Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
        scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                designer.setMode(PuppetDesigner.MODE_HEAL);
                buttonBar.setVisibility(View.GONE);
                View newButtonBar = eraseBackgroundButtonBar;
                newButtonBar.setVisibility(View.VISIBLE);
                View navButton = findViewById(R.id.nav_button);
                navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_arrow_back));
                Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in_spin);
                navButton.startAnimation(fadeIn);
                navButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        BackgroundButtonClick(view);
                    }
                });
                Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                newButtonBar.startAnimation(scaleUpRight);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        Animation spinOut = AnimationUtils.loadAnimation(context, R.anim.anim_fade_out_spin);
        navButton.startAnimation(spinOut);
        buttonBar.startAnimation(scaleDownLeft);
    }
    private void UnselectCutPathButton(){
        View v = findViewById(R.id.cut_path_button);
        v.setBackground(getResources().getDrawable(R.drawable.ic_action_cut));
    }

    // Portrait adjustment methods
    public void PortraitButtonClick(View v){
        final View buttonBar = mainButtonBar;
        final View navButton = findViewById(R.id.nav_button);

        // Animate disappearance of button bar and appearance of brush bar
        Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
        scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                designer.setShowLowerJawBox(true);
                designer.setShowUpperJawBox(true);
                designer.setSelectionMode(true);
                buttonBar.setVisibility(View.GONE);
                View newButtonBar = portraitButtonBar;
                newButtonBar.setVisibility(View.VISIBLE);
                navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_arrow_back));
                Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in_spin);
                navButton.startAnimation(fadeIn);
                navButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PortraitBarDoneClick(view);
                    }
                });
                Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                newButtonBar.startAnimation(scaleUpRight);
                Toast.makeText(context, getString(R.string.toast_profile), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        Animation spinOut = AnimationUtils.loadAnimation(context, R.anim.anim_fade_out_spin);
        buttonBar.startAnimation(scaleDownLeft);
        navButton.startAnimation(spinOut);

    }
    public void PortraitBarDoneClick(View v){
        final View buttonBar = portraitButtonBar;
        if (buttonBar.getVisibility() != View.GONE) {
            final View navButton = findViewById(R.id.nav_button);
            Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down_to_left);
            scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    buttonBar.setVisibility(View.GONE);
                    View navButton = findViewById(R.id.nav_button);
                    navButton.setBackground(getResources().getDrawable(R.drawable.ic_action_navigation_menu));
                    Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in_spin);
                    navButton.startAnimation(fadeIn);
                    navButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ControlButtonClick(view);
                        }
                    });
                    View buttonBar = findViewById(R.id.main_buttons);
                    buttonBar.setVisibility(View.VISIBLE);
                    Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                    buttonBar.startAnimation(scaleUpRight);
                    designer.setShowLowerJawBox(false);
                    designer.setShowUpperJawBox(false);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            Animation spinOut = AnimationUtils.loadAnimation(context, R.anim.anim_fade_out_spin);
            navButton.startAnimation(spinOut);
            buttonBar.startAnimation(scaleDownLeft);
        }

    }
    public void RightPortraitClick(View v){
        v.setBackground(getResources().getDrawable(R.drawable.ic_action_profile_right_selected));
        findViewById(R.id.portrait_edit_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_right));
        findViewById(R.id.portrait_left_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_left));
        designer.setOrientation(Puppet.PROFILE_RIGHT);
        Toast.makeText(context, getString(R.string.toast_profile_right), Toast.LENGTH_SHORT).show();
    }
    public void LeftPortraitClick(View v){
        v.setBackground(getResources().getDrawable(R.drawable.ic_action_profile_left_selected));
        findViewById(R.id.portrait_edit_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_left));
        findViewById(R.id.portrait_right_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_right));
        designer.setOrientation(Puppet.PROFILE_LEFT);
        Toast.makeText(context, getString(R.string.toast_profile_left), Toast.LENGTH_SHORT).show();
    }

}
