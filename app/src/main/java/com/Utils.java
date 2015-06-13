package com;

import android.graphics.Bitmap;
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
import java.io.StreamCorruptedException;

/**
 * Created by Nathan on 6/12/2015.
 */
public class Utils {

    private static final String LOG_TAG = "Utils";

    public static String WritePuppetToFile(Puppet puppet, String path){
        ObjectOutput out = null;
        PuppetData data = puppet.getData(path);
        File file = new File(path + File.pathSeparator + "puppet_data");

        try {
            out = new ObjectOutputStream(new FileOutputStream(file));
            out.writeObject(data);
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }

    public static PuppetData ReadPuppetFromFile(File file){
        ObjectInputStream input;
        PuppetData data = null;

        try {
            input = new ObjectInputStream(new FileInputStream(file));
            data = (PuppetData)input.readObject();
            input.close();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return data;
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
}
