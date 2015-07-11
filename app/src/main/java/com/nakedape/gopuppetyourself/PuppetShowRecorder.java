package com.nakedape.gopuppetyourself;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.Utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
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
    private Handler mHandler = new Handler();
    private PlayLoop playLoop;
    private MediaRecorder mRecorder;
    private String audioFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "//recording.3gp";
    private MediaPlayer mPlayer;
    private int width, height;
    private PuppetShow puppetShow;
    private ArrayList<String> bitmapPaths;
    private float xScaleFactor, yScaleFactor;

    public PuppetShowRecorder(Context context, RelativeLayout stage){
        this.context = context;
        this.stage = stage;
        width = stage.getWidth();
        height = stage.getHeight();
    }
    public void setHandler(Handler handler){
        mHandler = handler;
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
        //frameSequence = new ArrayList<>();
        //frameSequence.add(new KeyFrame(0, KeyFrame.START));
        if (isReady) {
            puppetShow.addFrame(getStartFrame());
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

    // Play flow
    public boolean prepareToPlay(){
        if (puppetShow != null){
            // Set the width/height
            Point newDimensions = Utils.getScaledDimension(new Point(puppetShow.origWidth, puppetShow.origHeight), new Point(stage.getWidth(), stage.getHeight()));
            RelativeLayout.LayoutParams stageParams = (RelativeLayout.LayoutParams)stage.getLayoutParams();
            stageParams.width = newDimensions.x;
            stageParams.height = newDimensions.y;
            stage.setLayoutParams(stageParams);
            width = newDimensions.x;
            height = newDimensions.y;
            xScaleFactor = (float) width / puppetShow.origWidth;
            yScaleFactor = (float) height / puppetShow.origHeight;
            // Remove any puppets that are on the stage
            stage.removeAllViews();
            // Set the first background
            if (puppetShow.backgroundCount() > 0)
                stage.setBackground(new BitmapDrawable(context.getResources(), puppetShow.getBackground(0)));
            // Add the puppets to the stage for the show and set initial properties
            Puppet p;
            for (int i = 0; i < puppetShow.getPuppets().size(); i++){
                p = puppetShow.getPuppet(i);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                p.setScaleX(xScaleFactor * p.getScaleX());
                p.setScaleY(yScaleFactor * p.getScaleY());
                params.setMargins((int) (puppetShow.initialXs[i] * xScaleFactor), (int) (puppetShow.initialYs[i] * yScaleFactor), -250, -250);
                p.setLayoutParams(params);
                p.setTag(p.getName());
                stage.addView(p);
            }
            // Set the frame sequence and length
            frameSequence = puppetShow.getFrameSequence();
            getLength();

            stage.setVisibility(View.VISIBLE);
            AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(context, R.animator.fade_in);
            set.setTarget(stage);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    stage.setAlpha(1f);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            set.start();
            return true;
        } else
            return false;
    }
    public void Play(){
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Start playing audio
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setDataSource(audioFilePath);
                mPlayer.prepare();
                mPlayer.start();
            } catch (IOException e) {
                Log.e(LOG_TAG, "MediaPlayer.prepare() failed");
            }
            // Start playing animation
            playLoop = new PlayLoop();
            new Thread(playLoop).start();
            isPlaying = true;
        } else {
            Toast.makeText(context, "Unable to gain audio focus", Toast.LENGTH_SHORT).show();
        }
    }
    public void Stop(){
        // Stop media player
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

        // Stop animation loop
        isPlaying = false;
        isRecording = false;
        stopCounterThread = true;
    }
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int i) {
            if (i == AudioManager.AUDIOFOCUS_LOSS || i == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
                if (mPlayer != null) {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                }
                isPlaying = false;
            }
        }
    };


    private class PlayLoop implements Runnable{
        @Override
        public void run(){
            //Log.d(LOG_TAG, "Play loop started");
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            startMillis = SystemClock.elapsedRealtime();
            for (int i = 0; i < frameSequence.size() && isPlaying; i++) {
                final KeyFrame frame = frameSequence.get(i);
                while (getTimeFromStartMillis() < frame.time && isPlaying){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Message msg = mHandler.obtainMessage(COUNTER_UPDATE);
                    msg.sendToTarget();
                }
                final Puppet p = (Puppet)stage.findViewWithTag(frame.puppetId);
                switch (frame.eventType){
                    case KeyFrame.OPEN_MOUTH_DEGREES:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.OpenMouth(frame.integer);
                            }
                        });
                        break;
                    case KeyFrame.CLOSE_MOUTH:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.OpenMouth(0);
                            }
                        });
                        break;
                    case KeyFrame.MOVEMENT:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) p
                                        .getLayoutParams();
                                layoutParams.leftMargin = Math.round(frame.x * xScaleFactor);
                                layoutParams.topMargin = Math.round(frame.y * yScaleFactor);
                                layoutParams.rightMargin = -250;
                                layoutParams.bottomMargin = -250;
                                p.setLayoutParams(layoutParams);
                            }
                        });
                        break;
                    case KeyFrame.SET_SCALE:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.setScaleX(frame.x * xScaleFactor);
                                p.setScaleY(frame.y * yScaleFactor);
                            }
                        });
                        break;
                    case KeyFrame.VISIBILITY:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (frame.visible)
                                    p.setVisibility(View.VISIBLE);
                                else
                                    p.setVisibility(View.GONE);
                            }
                        });
                        break;
                    case KeyFrame.SET_BACKGROUND:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                stage.setBackground(new BitmapDrawable(context.getResources(), puppetShow.getBackground(frame.integer)));
                            }
                        });
                        break;
                    case KeyFrame.END:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                stage.removeAllViews();
                            }
                        });
                        Message msg = mHandler.obtainMessage(COUNTER_END);
                        msg.sendToTarget();
                        //Log.d(LOG_TAG, "Show end time: " + frame.time);
                        //Log.d(LOG_TAG, "Actual end time: " + getTimeFromStartMillis());
                        return;

                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stage.removeAllViews();
                }
            });
            Message msg = mHandler.obtainMessage(COUNTER_END);
            msg.sendToTarget();

        }
    }
}
