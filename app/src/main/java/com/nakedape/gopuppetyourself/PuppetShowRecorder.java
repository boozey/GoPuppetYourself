package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.util.ArrayList;

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

    public PuppetShowRecorder(Context context, RelativeLayout stage){
        this.context = context;
        this.stage = stage;
        width = stage.getWidth();
        height = stage.getHeight();
    }
    public void setHandler(Handler handler){
        mHandler = handler;
    }

    public void setFrameSequence(ArrayList<KeyFrame> framesSequence){
        this.frameSequence = framesSequence;
    }

    // Recording flow
    public void prepareToRecord(){
        puppetShow = new PuppetShow(stage);
        width = stage.getWidth();
        height = stage.getHeight();
        isReady = true;
    }
    public void RecordStart(){
        //frameSequence = new ArrayList<>();
        //frameSequence.add(new KeyFrame(0, KeyFrame.START));
        if (isReady) {
            puppetShow.addFrame(KeyFrame.getStartFrame());
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
        puppetShow.addFrame(new KeyFrame(getTimeFromStartMillis(), puppetId, event, x / width, y / height));
        Log.d(LOG_TAG, "Puppet movement: " + x / width + ", " + y / height);
    } // Record a keyframe event with movement
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
            Log.d(LOG_TAG, "Recording stopped. Length: " + showLength);
        }
    } // Stop recording
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
            Log.d(LOG_TAG, "Error determining frameSequence start time");
        }
        Log.d(LOG_TAG, "Show length: " + showLength);
        return showLength;
    }

    public long getTimeFromStartMillis(){
        return SystemClock.elapsedRealtime() - startMillis;
    }

    // Play flow
    public boolean prepareToPlay(){
        if (puppetShow != null){
            // Set the width/height
            width = stage.getWidth();
            height = stage.getHeight();
            // Remove any puppets that are on the stage
            for (int i = 0; i < stage.getChildCount(); i++){
                stage.removeViewAt(i);
            }
            // Set the first background
            stage.setBackground(new BitmapDrawable(context.getResources(), puppetShow.getBackground(0)));
            // Add the puppets to the stage for the show and set initial properties
            Puppet p;
            for (int i = 0; i < puppetShow.getPuppets().size(); i++){
                p = puppetShow.getPuppet(i);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins((int)(puppetShow.initialXs[i] * width), (int)(puppetShow.initialYs[i] * height), 0, 0);
                p.setLayoutParams(params);
                if (puppetShow.initialVisibilities[i]) {
                    p.setOnStage(true);
                    p.setVisibility(View.VISIBLE);
                }
                else {
                    p.setOnStage(false);
                    p.setVisibility(View.GONE);
                }
                p.setScaleX(puppetShow.initialScales[i]);
                p.setScaleY(Math.abs(puppetShow.initialScales[i]));
                p.setTag(p.getName());
                stage.addView(p);
                Log.d(LOG_TAG, "Puppet added, visibility = " + p.isOnStage());
            }
            // Set the frame sequence and length
            frameSequence = puppetShow.getFrameSequence();
            getLength();
            return true;
        } else
            return false;
    }
    public void Play(){
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

    private class PlayLoop implements Runnable{
        @Override
        public void run(){
            Log.d(LOG_TAG, "Play loop started");
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
                    case KeyFrame.OPEN_MOUTH_NARROW:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.OpenMouth(15);
                            }
                        });
                        break;
                    case KeyFrame.OPEN_MOUTH_MED:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.OpenMouth(30);
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
                                layoutParams.leftMargin = (int)(frame.x * width);
                                layoutParams.topMargin = (int)(frame.y * height);
                                layoutParams.rightMargin = -250;
                                layoutParams.bottomMargin = -250;
                                p.setLayoutParams(layoutParams);
                            }
                        });
                        break;
                    case KeyFrame.END:
                        Message msg = mHandler.obtainMessage(COUNTER_END);
                        msg.sendToTarget();
                        Log.d(LOG_TAG, "Show end time: " + frame.time);
                        Log.d(LOG_TAG, "Actual end time: " + getTimeFromStartMillis());
                        return;

                }
            }
            Message msg = mHandler.obtainMessage(COUNTER_END);
            msg.sendToTarget();

        }
    }
}
