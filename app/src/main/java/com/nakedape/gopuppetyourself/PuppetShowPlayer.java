package com.nakedape.gopuppetyourself;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Created by Nathan on 6/25/2015.
 */

public class PuppetShowPlayer {
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

    public void RecordFrame(int puppetId, int event){
        frameSequence.add(new KeyFrame(getTimeFromStartMillis(), puppetId, event));
    }
    public void RecordFrame(int puppetId, int event, int x, int y){
        frameSequence.add(new KeyFrame(getTimeFromStartMillis(), puppetId, event, x, y));
    }


    public long getTimeFromStartMillis(){
        return SystemClock.elapsedRealtime() - startMillis;
    }

    public void Play(){
        isPlaying = true;
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
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            startMillis = SystemClock.elapsedRealtime() - getTimeFromStartMillis();
            do {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Message msg = mHandler.obtainMessage(COUNTER_UPDATE);
                msg.sendToTarget();
                if (isRecording)
                    recordingEndTime = getTimeFromStartMillis();
            } while ((isRecording || isPlaying) && !stopCounterThread);

        }
    }
}
