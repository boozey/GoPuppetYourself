package com.nakedape.gopuppetyourself;

import android.animation.AnimatorSet;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.Utils;

import java.io.File;
import java.io.IOException;


public class DesignerActivity extends ActionBarActivity {

    private static final String LOG_TAG = "DesignerActivity";
    private static final int REQUEST_IMAGE_GET = 4001;
    public static final String UPPER_JAW = "com.nakedape.gopuppetyourself.UPPER_JAW";
    public static final String LOWER_JAW = "com.nakedape.gopuppetyourself.LOWER_JAW";
    public static final String UPPER_PIVOT = "com.nakedape.gopuppetyourself.UPPER_PIVOT";
    public static final String LOWER_PIVOT = "com.nakedape.gopuppetyourself.LOWER_PIVOT";
    public static final String PROFILE_ORIENTATION = "com.nakedape.gopuppetyourself.PROFILE_ORIENTATION";
    private static final int BRUSH_SIZE_S = 6;
    private static final int BRUSH_SIZE_M = 12;
    private static final int BRUSH_SIZE_L = 18;
    private static final int[] mainButtonIds = {R.id.palette_button, R.id.background_button, R.id.show_box_button, R.id.save_button};

    PuppetDesigner designer;
    private Context context = this;
    private PopupWindow popup;
    private int colorSelection = R.id.black;
    private int brushSelection = R.id.brush_small;
    private int paletteBrushSize = BRUSH_SIZE_M;
    private ImageButton showBoxButton;
    private View.OnLongClickListener showBoxButtonLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            SwitchOrientation(view);
            return true;
        }
    };
    private Puppet puppet;
    private File storageDir;
    private int stageIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_designer);
        designer = (PuppetDesigner)findViewById(R.id.designer);
        showBoxButton = (ImageButton)findViewById(R.id.show_box_button);
        showBoxButton.setSelected(true);
        showBoxButton.setOnLongClickListener(showBoxButtonLongClick);
        showBoxButton.setTag(Puppet.PROFILE_RIGHT);

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
            stageIndex = intent.getIntExtra(MainActivity.PUPPET_INDEX, -1);
            EditText editText = (EditText)findViewById(R.id.puppet_name);
            editText.setText(puppet.getName());
        }
        else {
            launchGetPicIntent();
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
            //Bitmap thumbnail = data.getParcelableExtra("data");
            Uri fullPhotoUri = data.getData();
            NewPuppet(fullPhotoUri);
        }
    }

    private void launchGetPicIntent(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_GET);
        }
    }

    private void NewPuppet(Uri imageUri){
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
        } catch (IOException e){}
        if (bitmap != null){
            designer.SetNewImage(bitmap);
        }
    }

    public void Save(View v){
        Intent data = new Intent();
        EditText text = (EditText)findViewById(R.id.puppet_name);
        // Create puppet
        puppet = new Puppet(context, null);
        if (text.getText().length() > 0){
            puppet.setName(text.getText().toString());
        } else {
            puppet.setName(getString(R.string.default_puppet_name));
        }
        puppet.setOrientation(0);
        puppet.setImages(designer.getUpperJaw(), designer.getLowerJaw(), designer.getUpperJawPivotPoint(), designer.getLowerJawPivotPoint());
        // Save puppet to storage directory
        File saveFile = new File(storageDir, puppet.getName() + getResources().getString(R.string.puppet_extension));
        String filePath = Utils.WritePuppetToFile(puppet, saveFile);
        // Pass file name back to MainActivity
        data.putExtra(MainActivity.PUPPET_PATH, filePath);
        Log.d(LOG_TAG, "Saved to " + filePath);
        // Pass index back in case this was an edit
        data.putExtra(MainActivity.PUPPET_INDEX, stageIndex);

        setResult(MainActivity.RESULT_OK, data);
        finish();
    }
    public void PaletteClick(View v){
        // Hide button bar and show brush bar
        final View buttonBar = findViewById(R.id.button_bar);
        if (buttonBar.getVisibility() != View.GONE) {
            // Set slider to current value
            SeekBar slider = (SeekBar)findViewById(R.id.brush_slider);
            slider.setMax(48);
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
            Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down);
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
                    Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                    brushBar.startAnimation(scaleUpRight);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            buttonBar.startAnimation(scaleDownLeft);
        }
        else {
            showPalette();
        }

    }
    public void showPalette(){
        // Inflate the popup_layout.xml
        final LinearLayout viewGroup = (LinearLayout) findViewById(R.id.palette_popup);
        LayoutInflater layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = layoutInflater.inflate(R.layout.palette_popup, viewGroup);
        // Create the PopupWindow
        popup = new PopupWindow(context);
        popup.setContentView(layout);
        // Show current selection
        View selection = layout.findViewById(colorSelection);
        selection.setSelected(true);
        selection = layout.findViewById(brushSelection);
        selection.setSelected(true);
        // Set popup dimensions
        int width = (int)getResources().getDimension(R.dimen.palette_width),
                height = (int)getResources().getDimension(R.dimen.palette_height);
        popup.setWidth(width);
        popup.setHeight(height);
        popup.setFocusable(true);
        popup.setBackgroundDrawable(new ColorDrawable(
                android.graphics.Color.TRANSPARENT));
        // Displaying the popup at the specified location
        popup.showAsDropDown(designer, 0, -height);
    }
    public void BrushBarDoneClick(View v){
        final View brushBar = findViewById(R.id.brush_bar);
        if (brushBar.getVisibility() != View.GONE) {
            Animation scaleDownLeft = AnimationUtils.loadAnimation(this, R.anim.anim_scale_down);
            scaleDownLeft.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    brushBar.setVisibility(View.GONE);
                    View buttonBar = findViewById(R.id.button_bar);
                    buttonBar.setVisibility(View.VISIBLE);
                    Animation scaleUpRight = AnimationUtils.loadAnimation(context, R.anim.anim_scale_up_right);
                    buttonBar.startAnimation(scaleUpRight);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            brushBar.startAnimation(scaleDownLeft);
        }
    }

    public void ColorSelect(View v){
        popup.dismiss();
        designer.setEraseMode(false);
        designer.cancelMagicEraseMode();
        designer.cancelBackgroundErase();
        colorSelection = v.getId();
        switch (colorSelection){
            case R.id.black:
                designer.setColor(getResources().getColor(R.color.black));
                break;
            case R.id.blue:
                designer.setColor(getResources().getColor(R.color.blue));
                break;
            case R.id.brown:
                designer.setColor(getResources().getColor(R.color.brown));
                break;
            case R.id.green:
                designer.setColor(getResources().getColor(R.color.green));
                break;
            case R.id.light_blue:
                designer.setColor(getResources().getColor(R.color.light_blue));
                break;
            case R.id.light_brown:
                designer.setColor(getResources().getColor(R.color.light_brown));
                break;
            case R.id.light_green:
                designer.setColor(getResources().getColor(R.color.light_green));
                break;
            case R.id.orange:
                designer.setColor(getResources().getColor(R.color.orange));
                break;
            case R.id.pink:
                designer.setColor(getResources().getColor(R.color.pink));
                break;
            case R.id.red:
                designer.setColor(getResources().getColor(R.color.red));
                break;
            case R.id.violet:
                designer.setColor(getResources().getColor(R.color.violet));
                break;
            case R.id.white:
                designer.setColor(getResources().getColor(R.color.white));
                break;
            case R.id.yellow:
                designer.setColor(getResources().getColor(R.color.yellow));
                break;
            case R.id.indigo:
                designer.setColor(getResources().getColor(R.color.indigo));
                break;
            case R.id.light_grey:
                designer.setColor(getResources().getColor(R.color.light_grey));
                break;
            case R.id.dark_grey:
                designer.setColor(getResources().getColor(R.color.dark_grey));
                break;
            case R.id.eraser:
                designer.setEraseMode(true);
                break;
            case R.id.bg_eraser:
                designer.setBackgroundErase();
                break;
            case R.id.bg_magic_eraser:
                designer.setMagicEraseMode();
                break;
        }
    }
    public void BrushSizeSelect(View v){
        popup.dismiss();
        paletteBrushSize = v.getWidth();
        brushSelection = v.getId();
        designer.setStrokeWidth((float) paletteBrushSize);
    }

    public void ShowBoxes(View v){
        if (v.isSelected()){
            v.setSelected(false);
            designer.setIsDrawMode(true);
        }
        else {
            v.setSelected(true);
            designer.setIsDrawMode(false);
        }
    }
    public void SwitchOrientation(View v){
        if (designer.getOrientation() == Puppet.PROFILE_RIGHT){
            designer.setOrientation(Puppet.PROFILE_LEFT);

        }
        else {
            designer.setOrientation(Puppet.PROFILE_RIGHT);
        }
    }

}
