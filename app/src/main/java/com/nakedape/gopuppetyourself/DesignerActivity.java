package com.nakedape.gopuppetyourself;

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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

    PuppetDesigner designer;
    private Context context = this;
    private PopupWindow popup;
    private int paletteSelection = R.id.black;
    private int paletteBrushSize = 6;
    private ImageButton showBoxButton;
    private View.OnLongClickListener showBoxButtonLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            SwitchOrientation(view);
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_designer);
        designer = (PuppetDesigner)findViewById(R.id.designer);
        showBoxButton = (ImageButton)findViewById(R.id.show_box_button);
        showBoxButton.setSelected(true);
        showBoxButton.setOnLongClickListener(showBoxButtonLongClick);
        showBoxButton.setTag(Puppet.PROFILE_RIGHT);
        launchGetPicIntent();
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
            //designer.setBackground(new BitmapDrawable(getResources(), bitmap));
            designer.SetNewImage(bitmap);
        }
    }

    public void Save(View v){
        Intent data = new Intent();
        String filePath = storeImage(designer.getUpperJaw(), "upper_jaw.bmp");
        Log.d(LOG_TAG, filePath);
        data.putExtra(UPPER_JAW, filePath);
        filePath = storeImage(designer.getLowerJaw(), "lower_jaw.bmp");
        data.putExtra(LOWER_JAW, filePath);
        data.putExtra(UPPER_PIVOT, designer.getUpperJawPivotPoint());
        data.putExtra(LOWER_PIVOT, designer.getLowerJawPivotPoint());
        data.putExtra(PROFILE_ORIENTATION, designer.getOrientation());
        setResult(MainActivity.RESULT_OK, data);
        finish();
    }
    public void OpenPalette(View v){
        // Inflate the popup_layout.xml
        final LinearLayout viewGroup = (LinearLayout) findViewById(R.id.palette_popup);
        LayoutInflater layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = layoutInflater.inflate(R.layout.palette_popup, viewGroup);
        // Create the PopupWindow
        popup = new PopupWindow(context);
        popup.setContentView(layout);
        // Set previous selection
        View selectedColor = layout.findViewById(paletteSelection);
        selectedColor.setSelected(true);
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
        //popup.showAtLocation(layout, Gravity.START, 0, 0);
        designer.setIsDrawMode(true);
    }

    public void ColorSelect(View v){
        popup.dismiss();
        designer.setEraseMode(false);
        designer.cancelMagicEraseMode();
        designer.cancelBackgroundErase();
        paletteSelection = v.getId();
        switch (paletteSelection){
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

    private String storeImage(Bitmap image, String filename) {
        String filePath = getCacheDir().getAbsolutePath() + "//" + filename;
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
            return filePath;
        } catch (FileNotFoundException e) {
            Log.d(LOG_TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(LOG_TAG, "Error accessing file: " + e.getMessage());
        }
        return "";
    }
}
