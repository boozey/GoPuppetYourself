package com.nakedape.gopuppetyourself;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Message;
import android.os.SystemClock;
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
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by Nathan on 8/2/2015.
 */
public class PuppetShowPlayer {

    public interface OnPlayFinishedListener {
        void OnPlayFinish();
    }

    final private static String LOG_TAG = "PuppetShowPlayer";

    private Context context;
    private OnPlayFinishedListener onPlayFinishedListener;
    private RelativeLayout stage;
    private int width, height;
    private PuppetShow puppetShow;
    private String audioFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "//audio.3gp";
    private MediaPlayer mPlayer;
    private boolean isPlaying = false, isPaused = false;
    private PlayLoop playLoop;
    private float xScaleFactor, yScaleFactor;
    private ArrayList<KeyFrame> frameSequence;
    private long startMillis, showLength, pausePoint;

    public PuppetShowPlayer(Context context, RelativeLayout stage){
        this.context = context;
        this.stage = stage;
        width = stage.getWidth();
        height = stage.getHeight();
    }
    public void setOnPlayFinishedListener(OnPlayFinishedListener listener){
        onPlayFinishedListener = listener;
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
            pausePoint = 0;
            // Set the width/height
            Point newDimensions = Utils.getScaledDimension(new Point(puppetShow.origWidth, puppetShow.origHeight), new Point(stage.getWidth(), stage.getHeight()));
            Log.d(LOG_TAG, "puppetShow.origWidth = " + puppetShow.origWidth);
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
                Point point = puppetShow.initialPositions.get(i);
                params.setMargins((int) (point.x * xScaleFactor), (int) (point.y * yScaleFactor), -250, -250);
                p.setLayoutParams(params);
                p.setTag(p.getName());
                stage.addView(p);
            }
            // Hide the puppets that aren't initially on stage
            for (String name : puppetShow.initOffStage){
                stage.findViewWithTag(name).setVisibility(View.GONE);
            }
            // Set the frame sequence and length
            frameSequence = puppetShow.getFrameSequence();
            getLength();

            stage.setAlpha(0f);
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
    public void release(){
        isPlaying = false;
        if (mPlayer != null){
            if (mPlayer.isPlaying()) mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
    public void Play(){
        if (!isPaused) {
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
                isPlaying = true;
                playLoop = new PlayLoop();
                new Thread(playLoop).start();
            } else {
                Toast.makeText(context, "Unable to gain audio focus", Toast.LENGTH_SHORT).show();
            }
        } else {
            isPaused = false;
            isPlaying = true;
            playLoop = new PlayLoop();
            new Thread(playLoop).start();
            mPlayer.start();
        }
    }
    public boolean isPlaying(){ return isPlaying; }
    public long Pause(){
        if (isPlaying){
            pausePoint = getTimeFromStartMillis();
            mPlayer.pause();
            isPlaying = false;
            isPaused = true;
            return pausePoint;
        } else {
            return 0;
        }
    }
    public void PlayFrom(long timeMillis){
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Start playing audio
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setDataSource(audioFilePath);
                mPlayer.prepare();
                mPlayer.seekTo((int)timeMillis);
                mPlayer.start();
            } catch (IOException e) {
                Log.e(LOG_TAG, "MediaPlayer.prepare() failed");
            }
            // Start playing animation
            isPlaying = true;
            isPaused = true;
            pausePoint = timeMillis;
            playLoop = new PlayLoop();
            new Thread(playLoop).start();
        } else {
            Toast.makeText(context, "Unable to gain audio focus", Toast.LENGTH_SHORT).show();
        }
    }
    private void seekTo(long timeMillis){
        for (int i = 0; i < frameSequence.size() && frameSequence.get(i).time < timeMillis; i++) {
            final KeyFrame frame = frameSequence.get(i);
            Puppet p = (Puppet)stage.findViewWithTag(frame.puppetId);
            switch (frame.eventType){
                case KeyFrame.OPEN_MOUTH_DEGREES:
                    p.OpenMouth(frame.integer);
                    break;
                case KeyFrame.CLOSE_MOUTH:
                    p.OpenMouth(0);
                    break;
                case KeyFrame.MOVEMENT:
                    RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) p
                            .getLayoutParams();
                    layoutParams.leftMargin = Math.round(frame.x * xScaleFactor);
                    layoutParams.topMargin = Math.round(frame.y * yScaleFactor);
                    layoutParams.rightMargin = -250;
                    layoutParams.bottomMargin = -250;
                    p.setLayoutParams(layoutParams);
                    break;
                case KeyFrame.SET_SCALE:
                    p.setScaleX(frame.x * xScaleFactor);
                    p.setScaleY(frame.y * yScaleFactor);
                    break;
                case KeyFrame.VISIBILITY:
                    if (frame.visible)
                        p.setVisibility(View.VISIBLE);
                    else
                        p.setVisibility(View.GONE);
                    break;
                case KeyFrame.ROTATE:
                    p.setRotation(frame.x);
                    break;
                case KeyFrame.SET_BACKGROUND:
                    stage.setBackground(new BitmapDrawable(context.getResources(), puppetShow.getBackground(frame.integer)));
                    break;
                case KeyFrame.END:
                    if (onPlayFinishedListener != null)
                        onPlayFinishedListener.OnPlayFinish();
            }
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
            Log.d(LOG_TAG, "Play loop started");
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            int startIndex = 0;
            if (pausePoint > 0){
                for (startIndex = 0; startIndex < frameSequence.size() && frameSequence.get(startIndex).time < pausePoint; startIndex++);
                startMillis = SystemClock.elapsedRealtime() - pausePoint;
                pausePoint = 0;
            } else {
                startMillis = SystemClock.elapsedRealtime();
            }
            for (int i = startIndex; i < frameSequence.size() && isPlaying; i++) {
                final KeyFrame frame = frameSequence.get(i);
                while (getTimeFromStartMillis() < frame.time && isPlaying){
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                final Puppet p = (Puppet)stage.findViewWithTag(frame.puppetId);
                switch (frame.eventType){
                    case KeyFrame.OPEN_MOUTH_DEGREES:
                        stage.post(new Runnable() {
                            @Override
                            public void run() {
                                p.OpenMouth(frame.integer);
                            }
                        });
                        break;
                    case KeyFrame.CLOSE_MOUTH:
                        stage.post(new Runnable() {
                            @Override
                            public void run() {
                                p.OpenMouth(0);
                            }
                        });
                        break;
                    case KeyFrame.MOVEMENT:
                        stage.post(new Runnable() {
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
                        stage.post(new Runnable() {
                            @Override
                            public void run() {
                                p.setScaleX(frame.x * xScaleFactor);
                                p.setScaleY(frame.y * yScaleFactor);
                            }
                        });
                        break;
                    case KeyFrame.VISIBILITY:
                        stage.post(new Runnable() {
                            @Override
                            public void run() {
                                if (frame.visible)
                                    p.setVisibility(View.VISIBLE);
                                else
                                    p.setVisibility(View.GONE);
                            }
                        });
                        break;
                    case KeyFrame.ROTATE:
                        stage.post(new Runnable() {
                            @Override
                            public void run() {
                                p.setRotation(frame.x);
                            }
                        });
                        break;
                    case KeyFrame.SET_BACKGROUND:
                        stage.post(new Runnable() {
                            @Override
                            public void run() {
                                stage.setBackground(new BitmapDrawable(context.getResources(), puppetShow.getBackground(frame.integer)));
                            }
                        });
                        break;
                    case KeyFrame.END:
                        stage.post(new Runnable() {
                            @Override
                            public void run() {
                                if (onPlayFinishedListener != null)
                                    onPlayFinishedListener.OnPlayFinish();
                            }
                        });
                        return;

                }
            }

        }
    }
}
