package com.nakedape.gopuppetyourself;

import android.graphics.Bitmap;

import java.util.ArrayList;

/**
 * Created by Nathan on 6/26/2015.
 */
public class PuppetShow {
    private ArrayList<Puppet> puppets;
    private ArrayList<Bitmap> backgrounds;
    private ArrayList<KeyFrame> frameSequence;

    public PuppetShow(){
        puppets = new ArrayList<>();
        backgrounds = new ArrayList<>();
        frameSequence = new ArrayList<>();
    }

    public void addPuppet(Puppet puppet){
        puppets.add(puppet);
    }
    public void addBackground(Bitmap bitmap){
        backgrounds.add(bitmap);
    }
    public void setFrameSequence(ArrayList<KeyFrame> frames){
        frameSequence = frames;
    }
}
