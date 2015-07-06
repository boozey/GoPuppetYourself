package com.nakedape.gopuppetyourself;

import java.io.Serializable;

/**
 * Created by Nathan on 6/25/2015.
 */
public class KeyFrame implements Serializable {
    public final static int START = 1000;
    public final static int MOVEMENT = 1001;
    public final static int CLOSE_MOUTH = 1002;
    public final static int OPEN_MOUTH_NARROW = 1003;
    public final static int OPEN_MOUTH_MED = 1004;
    public final static int OPEN_MOUTH_WIDE = 1005;
    public final static int OPEN_MOUTH_DEGREES = 1006;
    public final static int SET_SCALE = 1007;
    public final static int VISIBILITY = 1008;
    public final static int END = 9000;

    private final static int UNSET = -1;

    public String puppetId;
    public int eventType;
    public long time;
    public float x = UNSET, y = UNSET;
    public boolean visible = true;
    public int degrees;


    public KeyFrame(){

    }
    public KeyFrame(long time, int eventType){
        this.time = time;
        this.eventType = eventType;
    }
    public KeyFrame(long time, String puppetId, int eventType){
        this.time = time;
        this.puppetId = puppetId;
        this.eventType = eventType;
    }
    public KeyFrame(long time, String puppetId, int eventType, float x, float y){
        this.time = time;
        this.puppetId = puppetId;
        this.eventType = eventType;
        this.x = x;
        this.y = y;
    }
    public KeyFrame(long time, String puppetId, int eventType, int degrees){
        this.time = time;
        this.puppetId = puppetId;
        this. eventType = eventType;
        this.degrees = degrees;
    }
    public KeyFrame(long time, String puppetId, int eventType, boolean visible){
        this.time = time;
        this.puppetId = puppetId;
        this.eventType = eventType;
        this.visible = visible;
    }

    public static KeyFrame getStartFrame(){
        return new KeyFrame(0, START);
    }
    public static KeyFrame getOpenMouthFrame(long time, String puppetId, int degrees){
        return new KeyFrame(time, puppetId, OPEN_MOUTH_DEGREES, degrees);
    }
    public static KeyFrame getCloseMouthFrame(long time, String puppetId){
        return new KeyFrame(time, puppetId, CLOSE_MOUTH);
    }
    public static KeyFrame getMoveFrame(long time, String puppetId, int x, int y){
        return new KeyFrame(time, puppetId, MOVEMENT, x, y);
    }
}
