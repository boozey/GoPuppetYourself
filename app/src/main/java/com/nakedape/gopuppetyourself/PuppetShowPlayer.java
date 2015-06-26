package com.nakedape.gopuppetyourself;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.ArrayList;

/**
 * Created by Nathan on 6/25/2015.
 */

public class PuppetShowPlayer {
    private static final String LOG_TAG = "PuppetShowPlayer";
    public static final int COUNTER_UPDATE = 919;
    private boolean stopCounterThread = false,
            isRecording = false, isPlaying = false;
    private long startMillis = SystemClock.elapsedRealtime(),
            recordingEndTime = -1;
    private ViewGroup stage;
    private ArrayList<KeyFrame> frameSequence;
    private Handler mHandler = new Handler();
    private PlayLoop playLoop;

    public PuppetShowPlayer(ViewGroup stage){
        this.stage = stage;
    }
    public void setHandler(Handler handler){
        mHandler = handler;
    }

    public void setFrameSequence(ArrayList<KeyFrame> framesSequence){
        this.frameSequence = framesSequence;
    }

    public void RecordStart(){
        frameSequence = new ArrayList<>(100);
        startMillis = SystemClock.elapsedRealtime();
        recordingEndTime = -1;
        isRecording = true;
    }
    public boolean isRecording(){
        return isRecording;
    }
    public void RecordFrame(int puppetId, int event){
        frameSequence.add(new KeyFrame(getTimeFromStartMillis(), puppetId, event));
    }
    public void RecordFrame(int puppetId, int event, int x, int y){
        frameSequence.add(new KeyFrame(getTimeFromStartMillis(), puppetId, event, x, y));
    }
    public void RecordStop(){
        recordingEndTime = getTimeFromStartMillis();
        isRecording = false;
    }
    public PuppetShow getRecording(){
        PuppetShow show = new PuppetShow();
        for (int i = 0; i < stage.getChildCount(); i++){
            show.addPuppet((Puppet)stage.getChildAt(i));
        }
        show.setFrameSequence(frameSequence);
        return show;
    }

    public long getTimeFromStartMillis(){
        return SystemClock.elapsedRealtime() - startMillis;
    }

    public void Play(){
        isPlaying = true;
        playLoop = new PlayLoop();
        new Thread(playLoop).start();
    }
    public void Stop(){
        isPlaying = false;
        isRecording = false;
        stopCounterThread = true;
    }

    private class PlayLoop implements Runnable{
        @Override
        public void run(){
            Log.d(LOG_TAG, "Play loop started");
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
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
                final Puppet p = (Puppet)stage.findViewById(frame.puppetId);
                switch (frame.eventType){
                    case KeyFrame.OPEN_MOUTH_NARROW:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.upperJaw.setRotation(15 * p.getPivotDirection());
                            }
                        });
                        break;
                    case KeyFrame.OPEN_MOUTH_MED:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.upperJaw.setRotation(30 * p.getPivotDirection());
                            }
                        });
                        break;
                    case KeyFrame.CLOSE_MOUTH:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                p.upperJaw.setRotation(0);
                            }
                        });
                        break;
                    case KeyFrame.MOVEMENT:
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) p
                                        .getLayoutParams();
                                layoutParams.leftMargin = frame.x;
                                layoutParams.topMargin = frame.y;
                                layoutParams.rightMargin = -250;
                                layoutParams.bottomMargin = -250;
                                p.setLayoutParams(layoutParams);
                            }
                        });
                        break;
                }
            }

        }
    }
}
