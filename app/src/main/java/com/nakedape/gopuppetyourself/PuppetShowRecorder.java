package com.nakedape.gopuppetyourself;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Nathan on 6/25/2015.
 */

public class PuppetShowRecorder {
    private static final String LOG_TAG = "PuppetShowPlayer";
    public static final int COUNTER_UPDATE = 919;
    public static final int COUNTER_END = 920;
    private boolean stopCounterThread = false, isReady = false,
            isRecording = false, isPlaying = false;
    private long startMillis = SystemClock.elapsedRealtime(),
            showLength = -1;
    private Context context;
    private RelativeLayout stage;
    private ArrayList<KeyFrame> frameSequence;
    private MediaRecorder mRecorder;
    private String audioFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "//recording.3gp";
    private MediaPlayer mPlayer;
    private int width, height;
    public  PuppetShow puppetShow;
    private ArrayList<String> bitmapPaths;
    private float xScaleFactor, yScaleFactor;

    public PuppetShowRecorder(Context context, RelativeLayout stage){
        this.context = context;
        this.stage = stage;
        width = stage.getWidth();
        height = stage.getHeight();
    }
    public void setStage(RelativeLayout stage){
        this.stage = stage;
    }

    // Recording flow
    public void prepareToRecord(){
        puppetShow = new PuppetShow(stage);
        bitmapPaths = new ArrayList<>();
        bitmapPaths.add("empty");
        width = stage.getWidth();
        height = stage.getHeight();
        puppetShow.origWidth = width;
        puppetShow.origHeight = height;
        isReady = true;
    }
    public void RecordStart(){
        if (isReady) {
            puppetShow.addFrame(getStartFrame());
            for (int i = 0; i < stage.getChildCount(); i++){
                Puppet p = (Puppet)stage.getChildAt(i);
                puppetShow.addFrame(new KeyFrame(0, p.getName(), KeyFrame.SET_SCALE, p.getScaleX(), p.getScaleY()));
                puppetShow.addFrame(new KeyFrame(0, p.getName(), KeyFrame.ROTATE, p.getRotation()));
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)p.getLayoutParams();
                puppetShow.addFrame(new KeyFrame(0, p.getName(), KeyFrame.MOVEMENT, params.leftMargin, params.topMargin));
                if (p.getVisibility() == View.GONE){
                    puppetShow.addFrame(new KeyFrame(0, p.getName(), KeyFrame.VISIBILITY, false));
                }
            }
            startMillis = SystemClock.elapsedRealtime();
            showLength = -1;

            // Setup audio recording
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setOutputFile(audioFilePath);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            try {
                mRecorder.prepare();
                mRecorder.start();
                Log.d(LOG_TAG, "Audio recording started");
            } catch (IOException e) {
                Log.e(LOG_TAG, "MediaRecorder.prepare() failed");
                e.printStackTrace();
            }

            isRecording = true;
            Log.d(LOG_TAG, "Show recording started");
        }
    } // Start recording
    public boolean isRecording(){
        return isRecording;
    }
    public void RecordFrame(String puppetId, int event){
        puppetShow.addFrame(new KeyFrame(getTimeFromStartMillis(), puppetId, event));
    } // Record a keyframe event
    public void RecordFrame(String puppetId, int event, float x, float y){
        puppetShow.addFrame(new KeyFrame(getTimeFromStartMillis(), puppetId, event, x, y));
    } // Record a keyframe event with movement
    public void RecordFrame(KeyFrame frame){
        puppetShow.addFrame(frame);
    }
    public KeyFrame getStartFrame(){
        return new KeyFrame(0, KeyFrame.START);
    }
    public KeyFrame getOpenMouthFrame(String puppetId, int degrees){
        return new KeyFrame(getTimeFromStartMillis(), puppetId, KeyFrame.OPEN_MOUTH_DEGREES, degrees);
    }
    public KeyFrame getCloseMouthFrame(String puppetId){
        return new KeyFrame(getTimeFromStartMillis(), puppetId, KeyFrame.CLOSE_MOUTH);
    }
    public KeyFrame getMoveFrame(String puppetId, int x, int y){
        return new KeyFrame(getTimeFromStartMillis(), puppetId, KeyFrame.MOVEMENT, x / width, y / height);
    }
    public KeyFrame getScaleFrame(String puppetId, float xScale, float yScale){
        return new KeyFrame(getTimeFromStartMillis(), puppetId, KeyFrame.SET_SCALE, xScale, yScale);
    }
    public KeyFrame getVisiblilityFrame(String puppetId, boolean visible){
        return new KeyFrame(getTimeFromStartMillis(), puppetId, KeyFrame.VISIBILITY, visible);
    }
    public KeyFrame getBackgroundFrame(String bitmapPath){
        bitmapPaths.add(bitmapPath);
        return new KeyFrame(getTimeFromStartMillis(), KeyFrame.SET_BACKGROUND, bitmapPaths.size() - 1);
    }
    public KeyFrame getRotateFrame(String puppetId, float degrees){
        return new KeyFrame(getTimeFromStartMillis(), puppetId, KeyFrame.ROTATE, degrees);
    }
    public void addPuppetToShow(Puppet p){
        puppetShow.addPuppet(p);
        if (isRecording)
            RecordFrame(new KeyFrame(getTimeFromStartMillis(), p.getName(), KeyFrame.VISIBILITY, true));
    }
    public void RecordStop(){
        if (isRecording()) {
            showLength = getTimeFromStartMillis();
            //frameSequence.add(new KeyFrame(showLength, KeyFrame.END));
            puppetShow.addFrame(new KeyFrame(showLength, KeyFrame.END));

            // Stop audio recording
            if (mRecorder != null) {
                mRecorder.stop();
                mRecorder.release();
                mRecorder = null;
            }

            isRecording = false;
            Log.i(LOG_TAG, "Recording stopped. Length: " + showLength);
        }
    } // Stop recording
    public void FinalizeRecording(){
        for (String path : bitmapPaths){
            if (!path.equals("empty")){
                puppetShow.addBackground(BitmapFactory.decodeFile(path));
            }
        }
    }

    public PuppetShow getShow(){
        return puppetShow;
    }
    public void setShow(PuppetShow puppetShow){
        this.puppetShow = puppetShow;
        this.puppetShow.SetContext(context);
    }
    public void WriteShowToFile(File saveFile){
        ObjectOutputStream out = null;
        if (saveFile.isFile()) saveFile.delete();

        try {
            saveFile.createNewFile();
            if (saveFile.canWrite()) {
                out = new ObjectOutputStream(new FileOutputStream(saveFile));
                puppetShow.writeObject(out);
                out.close();
                Log.d(LOG_TAG, "File written");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void WriteShowToZipFile(File saveFile){
        OutputStream os;
        ZipOutputStream zos;
        try {
            os = new FileOutputStream(saveFile);
            zos = new ZipOutputStream(os);
            byte[] bytes = puppetShow.getAsByteArray();
            ZipEntry entry = new ZipEntry("puppet_show");
            zos.putNextEntry(entry);
            zos.write(bytes);
            zos.closeEntry();
            entry = new ZipEntry("audio");
            zos.putNextEntry(entry);
            BufferedInputStream is = new BufferedInputStream(new FileInputStream(audioFilePath));
            bytes = new byte[1024 * 16];
            while (is.read(bytes) != -1){
                zos.write(bytes);
            }
            zos.closeEntry();
            zos.close();
        } catch (IOException e){e.printStackTrace();}
    }
    public void WriteShowToOutputStream(OutputStream os){
        ZipOutputStream zos;
        try {
            zos = new ZipOutputStream(os);
            byte[] bytes = puppetShow.getAsByteArray();
            ZipEntry entry = new ZipEntry("puppet_show");
            zos.putNextEntry(entry);
            zos.write(bytes);
            zos.closeEntry();
            entry = new ZipEntry("audio");
            zos.putNextEntry(entry);
            BufferedInputStream is = new BufferedInputStream(new FileInputStream(audioFilePath));
            bytes = new byte[1024 * 16];
            while (is.read(bytes) != -1){
                zos.write(bytes);
            }
            zos.closeEntry();
            zos.close();
            os.close();
        } catch (IOException e){e.printStackTrace();}
    }
    public void LoadShow(File file){
        ObjectInputStream input;
        puppetShow = new PuppetShow(context);
        try {
            input = new ObjectInputStream(new FileInputStream(file));
            puppetShow.readObject(input);
            input.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    public void LoadShowFromZipFile(File file){
        try {
            InputStream is = new FileInputStream(file);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
            ObjectInputStream input;
            FileOutputStream fos;
            puppetShow = new PuppetShow(context);

            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String filename = ze.getName();
                if (filename.equals("puppet_show")){
                    input = new ObjectInputStream(zis);
                    puppetShow.readObject(input);
                } else if (filename.equals("audio")){
                    fos = new FileOutputStream(audioFilePath);
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, count);
                    }
                    fos.close();
                }
            }
            zis.close();
            is.close();
        } catch (IOException | ClassNotFoundException e) {e.printStackTrace();}
    }
    public Bitmap getScreenShot(){
        if (puppetShow != null) {
            int width = 1200, height = 630;
            Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            if (puppetShow.backgroundCount() > 0) {
                Matrix m = new Matrix();
                m.setScale((float)width / puppetShow.getBackground(0).getWidth(), (float)height / puppetShow.getBackground(0).getHeight());
                canvas.drawBitmap(puppetShow.getBackground(0), m, null);
            }
            if (puppetShow.getPuppets().size() > 0) {
                Bitmap puppetThumb = puppetShow.getPuppet(0).getThumbnail();
                canvas.drawBitmap(puppetThumb, (width - puppetThumb.getWidth()) / 2, (height - puppetThumb.getHeight()) / 2, null);
            }
            return image;
        } else {
            return Utils.decodeSampledBitmapFromResource(context.getResources(), R.drawable.fb_share_image, 600, 315);
        }
    }

    public long getLength(){
        showLength = -1;
        int i = 0;
        do {
            if (frameSequence.get(i).eventType == KeyFrame.END)
                showLength = frameSequence.get(i).time;
            i++;
        } while (frameSequence.get(i).eventType != KeyFrame.END && i < frameSequence.size());
        if (showLength == -1){
            showLength = frameSequence.get(frameSequence.size() - 1).time;
            //Log.d(LOG_TAG, "Error determining frameSequence start time");
        }
        Log.i(LOG_TAG, "Show length: " + showLength);
        return showLength;
    }

    public long getTimeFromStartMillis(){
        return SystemClock.elapsedRealtime() - startMillis;
    }

}
