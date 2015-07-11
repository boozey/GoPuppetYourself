package com.nakedape.gopuppetyourself;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.nakedape.gopuppetyourself.Puppet;
import com.nakedape.gopuppetyourself.PuppetData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;

/**
 * Created by Nathan on 6/12/2015.
 */
public class Utils {

    private static final String LOG_TAG = "Utils";

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public static String WritePuppetToFile(Puppet puppet, File saveFile){
        ObjectOutputStream out = null;
        if (saveFile.isFile()) saveFile.delete();

        try {
            saveFile.createNewFile();
            if (saveFile.canWrite()) {
                out = new ObjectOutputStream(new FileOutputStream(saveFile));
                puppet.writeObject(out);
                out.close();
                Log.d(LOG_TAG, "File written");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return saveFile.getAbsolutePath();
    }

    public static void ReadPuppetFromFile(Puppet puppet, File file) {
        ObjectInputStream input;

        try {
            input = new ObjectInputStream(new FileInputStream(file));
            puppet.readObject(input);
            input.close();
            puppet.setPath(file.getAbsolutePath());
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static String WriteImage(Bitmap image, String filePath) {
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

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static Bitmap decodeSampledBitmapFromContentResolver(ContentResolver r, Uri uri, int reqWidth, int reqHeight) throws IOException{
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream inputStream = r.openInputStream(uri);
        BitmapFactory.decodeStream(inputStream, null, options);

        Point dimensions = getScaledDimension(options.outWidth, options.outHeight, reqWidth, reqHeight);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, dimensions.x, dimensions.y);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        inputStream.close();
        inputStream = r.openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream, null, options);
    }

    public static Bitmap decodedSampledBitmapFromFile(File f, int reqWidth, int reqHeight){
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), options);

        Point dimensions = getScaledDimension(options.outWidth, options.outHeight, reqWidth, reqHeight);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, dimensions.x, dimensions.y);

        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(f.getPath(), options);
    }

    public static Bitmap drawableToBitmap (Drawable drawable) {
        Bitmap bitmap = null;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static int getInBounds(int value, int min, int max){
        value = Math.max(min, value);
        return Math.min(value, max);
    }

    public static Point getScaledDimension(int origWidth, int origHeight, int maxWidth, int maxHeight){
        return getScaledDimension(new Point(origWidth, origHeight), new Point(maxWidth, maxHeight));
    }

    public static Point getScaledDimension(Point imgSize, Point boundary) {

        float original_width = imgSize.x;
        float original_height = imgSize.y;
        float bound_width = boundary.x;
        float bound_height = boundary.y;
        float new_width = original_width;
        float new_height = original_height;

        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }

        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }

        return new Point(Math.round(new_width), Math.round(new_height));
    }






}
