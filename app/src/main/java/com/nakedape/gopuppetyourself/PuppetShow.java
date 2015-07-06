package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    transient private String showName = "Untitled";
    transient private ArrayList<byte[]> puppets;
    transient private ArrayList<Bitmap> backgrounds;
    transient private ArrayList<KeyFrame> frameSequence;
    transient public float[] initialXs, initialYs;
    transient public float[] initialScales;
    transient private int orientation;
    transient private Context context;

    public PuppetShow(Context context){
        puppets = new ArrayList<>();
        backgrounds = new ArrayList<>();
        frameSequence = new ArrayList<>();
        this.context = context;
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
        Puppet p;
        for (int i = 0; i < stage.getChildCount(); i++) {
            p = (Puppet) stage.getChildAt(i);
            puppets.add(p.getBytes());
            Log.d(LOG_TAG, "Byte array size: " + puppets.get(i).length);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) p.getLayoutParams();
            initialXs[i] = (float) params.leftMargin / stage.getWidth();
            initialYs[i] = (float) params.topMargin / stage.getHeight();
        }
    }

    public void addPuppet(Puppet puppet){
        //puppets.add(puppet);
    }
    public Puppet getPuppet(int index){
        byte[] buffer = puppets.get(index);
        Puppet p = new Puppet(context, null);
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
    public int backgroundCount(){
        return backgrounds.size();
    }

    public void setFrameSequence(ArrayList<KeyFrame> frames){
        frameSequence = frames;
    }
    public void addFrame(KeyFrame frame){
        frameSequence.add(frame);
    }
    public ArrayList<KeyFrame> getFrameSequence(){ return  frameSequence; }

    public void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(showName);
        out.writeObject(puppets);
        out.writeObject(frameSequence);
        out.writeObject(initialXs);
        out.writeObject(initialYs);
        out.writeInt(backgroundCount());
        for (Bitmap b : backgrounds){
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, stream);
            int byteLength = stream.toByteArray().length;
            out.writeInt(byteLength);
            out.write(stream.toByteArray());
        }
    }
    public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
        showName = (String)in.readObject();
        puppets = (ArrayList<byte[]>)in.readObject();
        frameSequence = (ArrayList<KeyFrame>)in.readObject();
        initialXs = (float[])in.readObject();
        initialYs = (float[])in.readObject();
        int backgroundCount = in.readInt();
        for (int i = 0; i < backgroundCount; i++){
            byte[] bytes = new byte[in.readInt()];
            in.readFully(bytes);
            backgrounds.add(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        }
    }
    public byte[] getAsByteArray(){
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            writeObject(oos);
            oos.close();
            bos.close();
        } catch (IOException e)
        {e.printStackTrace();}
        return bos.toByteArray();
    }
}
