package com;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.nakedape.gopuppetyourself.Puppet;
import com.nakedape.gopuppetyourself.PuppetData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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







}
