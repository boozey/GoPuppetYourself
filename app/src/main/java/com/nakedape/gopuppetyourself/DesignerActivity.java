package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
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
import android.widget.Toast;

import com.Utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class DesignerActivity extends ActionBarActivity {

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
    private File storageDir;
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
            storageDir = new File(getExternalFilesDir(null), getResources().getString(R.string.puppet_directory));
            if (!storageDir.exists())
                if (!storageDir.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
        }
        else {
            storageDir = new File(getFilesDir(), getResources().getString(R.string.puppet_directory));
            if (!storageDir.exists())
                if (!storageDir.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
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
            ShowGetNewImagePopup();
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

    private void ShowGetNewImagePopup(){
        View layout = getLayoutInflater().inflate(R.layout.new_image_popup, null);
        int width = 200;
        int height = 400;
        layout.setMinimumHeight(height);
        layout.setMinimumWidth(width);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)layout.getLayoutParams();
        if (params == null){
            params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.CENTER_IN_PARENT, designer.getId());
        }
        layout.setLayoutParams(params);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Button cameraButton = (Button) layout.findViewById(R.id.camera_button);
            cameraButton.setVisibility(View.GONE);
        }
        rootLayout.addView(layout);
        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.anim_fade_in);
        if (rootLayout.getWidth() == 0)
            fadeIn.setStartOffset(300);
        layout.startAnimation(fadeIn);

    }
    public void CloseGetNewImagePopup(View v){
        View layout = findViewById(R.id.new_image_popup);
        rootLayout.removeView(layout);
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
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        cameraCapturePath = image.getAbsolutePath();
        return image;
    }


    private void NewPuppet(Uri imageUri){
        Bitmap bitmap = null;
        int width, height;
        try {
            try {
                final String[] columns = {MediaStore.Images.ImageColumns.WIDTH, MediaStore.Images.ImageColumns.HEIGHT};
                Cursor cursor = MediaStore.Images.Media.query(getContentResolver(), imageUri, columns);
                cursor.moveToFirst();
                width = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.WIDTH));
                Log.d(LOG_TAG, "image width = " + width);
                height = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.ImageColumns.HEIGHT));
                Log.d(LOG_TAG, "image width = " + height);
                if (width > designer.getWidth() || height > designer.getHeight()){
                    double scale = Math.min(((float) designer.getWidth() / width), ((float) designer.getHeight() / height));
                    bitmap = Utils.decodeSampledBitmapFromContentResolver(getContentResolver(), imageUri, (int)(designer.getWidth() * scale), (int)(designer.getHeight() * scale));
                    Log.d(LOG_TAG, "Scaled image width = " + String.valueOf(designer.getWidth() * scale));
                }
                else {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                e.printStackTrace();
                bitmap = Utils.decodeSampledBitmapFromContentResolver(getContentResolver(), imageUri, designer.getWidth(), designer.getHeight());
            }
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(context, "Unable to load image", Toast.LENGTH_LONG).show();
        }
        if (bitmap != null){
            designer.SetNewImage(bitmap);
            designer.invalidate();
            Log.d(LOG_TAG, "Bitmap width = " + String.valueOf(bitmap.getWidth()));
        }
    }
    private void NewPuppet(){
        // Get the dimensions of the View
        int targetW = (int)designer.getWidth();
        int targetH = (int)designer.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(cameraCapturePath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        Bitmap bitmap = BitmapFactory.decodeFile(cameraCapturePath, bmOptions);
        if (bitmap != null) {
            designer.SetNewImage(bitmap);
            designer.invalidate();
            Log.d(LOG_TAG, "Bitmap width = " + String.valueOf(bitmap.getWidth()));
        }
    }

    public void Save(View v){
        EditText text = (EditText)findViewById(R.id.puppet_name);
        String newName = text.getText().toString();
        if (newName.length() <= 0){
            newName = "Unnamed";
        }
        final String name = newName;
        // Check if the puppet name has changed and prompt if a puppet already has that name
        File[] files = storageDir.listFiles(new FileFilter() {
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
        // Create puppet
        puppet = new Puppet(context, null);
        puppet.setOrientation(designer.getOrientation());
        puppet.setImages(designer.getUpperJaw(), designer.getLowerJaw(), designer.getUpperJawPivotPoint(), designer.getLowerJawPivotPoint());
        puppet.setName(puppetName);
        // Save puppet to storage directory
        File saveFile = new File(storageDir, puppet.getName() + getResources().getString(R.string.puppet_extension));
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
            designer.setColor(getResources().getColor(R.color.black));
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
        // Hide background bar and show background erase bar
        final View buttonBar = findViewById(R.id.background_button_bar);
        final View navButton = findViewById(R.id.nav_button);
        final EditText thresholdText = (EditText)findViewById(R.id.threshold_text);

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
    public void CutPathClick(View v){
        if (designer.getMode() == PuppetDesigner.MODE_CUT_PATH){
            designer.setMode(PuppetDesigner.MODE_NO_TOUCH);
            v.setBackground(getResources().getDrawable(R.drawable.ic_action_cut));
        } else{
            designer.setCutPathMode();
            v.setBackground(getResources().getDrawable(R.drawable.ic_action_cut_selected));
        }
    }
    public void HealClick(View v){
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
    }
    public void LeftPortraitClick(View v){
        v.setBackground(getResources().getDrawable(R.drawable.ic_action_profile_left_selected));
        findViewById(R.id.portrait_edit_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_left));
        findViewById(R.id.portrait_right_button).setBackground(getResources().getDrawable(R.drawable.ic_action_profile_right));
        designer.setOrientation(Puppet.PROFILE_LEFT);
    }

}
