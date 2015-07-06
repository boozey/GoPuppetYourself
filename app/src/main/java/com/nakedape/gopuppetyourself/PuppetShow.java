package com.nakedape.gopuppetyourself;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Nathan on 6/26/2015.
 */
public class PuppetShow implements Serializable {
    transient private static final String LOG_TAG = "PuppetShow";
    transient private ViewGroup stage;
    //private ArrayList<Puppet> puppets;
    private ArrayList<byte[]> puppets;
    private ArrayList<Bitmap> backgrounds;
    private ArrayList<KeyFrame> frameSequence;
    public float[] initialXs, initialYs;
    public float[] initialScales;
    public boolean[] initialVisibilities;
    private int orientation;

    public PuppetShow(){
        puppets = new ArrayList<>();
        backgrounds = new ArrayList<>();
        frameSequence = new ArrayList<>();
    }
    public PuppetShow(RelativeLayout stage){
        this.stage = stage;
        puppets = new ArrayList<>();
        backgrounds = new ArrayList<>();
        frameSequence = new ArrayList<>();

        if (stage.getBackground() != null){
            backgrounds.add(Utils.drawableToBitmap(stage.getBackground()));
        }
        initialXs = new float[stage.getChildCount()];
        initialYs = new float[stage.getChildCount()];
        initialScales = new float[stage.getChildCount()];
        initialVisibilities = new boolean[stage.getChildCount()];
        Puppet p;
        for (int i = 0; i < stage.getChildCount(); i++) {
            p = (Puppet) stage.getChildAt(i);
            puppets.add(p.getBytes());
            Log.d(LOG_TAG, "Byte array size: " + puppets.get(i).length);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) p.getLayoutParams();
            initialXs[i] = (float) params.leftMargin / stage.getWidth();
            initialYs[i] = (float) params.topMargin / stage.getHeight();
            initialScales[i] = p.getScaleX();
            initialVisibilities[i] = p.isOnStage();
        }
    }

    public void addPuppet(Puppet puppet){
        //puppets.add(puppet);
    }
    public Puppet getPuppet(int index){
        byte[] buffer = puppets.get(index);
        Puppet p = new Puppet(stage.getContext(), null);
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
            ObjectInputStream ois = new ObjectInputStream(bis);
            p.readObject(ois);
        } catch (IOException | ClassNotFoundException e) {e.printStackTrace();}
        return p;
    }
    public ArrayList<byte[]> getPuppets(){ return puppets; }

    public void addBackground(Bitmap bitmap){
        backgrounds.add(bitmap);
    }
    public Bitmap getBackground(int index){ return backgrounds.get(0);}

    public void setFrameSequence(ArrayList<KeyFrame> frames){
        frameSequence = frames;
    }
    public void addFrame(KeyFrame frame){
        frameSequence.add(frame);
    }
    public ArrayList<KeyFrame> getFrameSequence(){ return  frameSequence; }
}
