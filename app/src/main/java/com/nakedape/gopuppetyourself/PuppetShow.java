package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    transient public ArrayList<String> initOffStage;
    transient public float[] initialXs, initialYs;
    transient public ArrayList<Point> initialPositions;
    transient private Context context;
    transient int origWidth, origHeight;

    public PuppetShow(Context context){
        puppets = new ArrayList<>();
        backgrounds = new ArrayList<>();
        frameSequence = new ArrayList<>();
        this.context = context;
    }
    public PuppetShow(RelativeLayout stage){
        origWidth = stage.getWidth();
        origHeight = stage.getHeight();
        context = stage.getContext();
        puppets = new ArrayList<>();
        backgrounds = new ArrayList<>();
        frameSequence = new ArrayList<>();
        initOffStage = new ArrayList<>();

        if (stage.getBackground() != null){
            String path = (String)stage.getTag();
            if (path == null || path.equals("default")) {
                Bitmap bitmap = Bitmap.createBitmap(stage.getWidth(), stage.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.parseColor("#8B37AF"));
                backgrounds.add(bitmap);
            } else {
                backgrounds.add(Utils.drawableToBitmap(stage.getBackground()));
            }
        } else {
            Bitmap bitmap = Bitmap.createBitmap(stage.getWidth(), stage.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.parseColor("#8B37AF"));
            backgrounds.add(bitmap);
        }
        Puppet p;
        initialPositions = new ArrayList<>(stage.getChildCount());
        for (int i = 0; i < stage.getChildCount(); i++) {
            p = (Puppet) stage.getChildAt(i);
            puppets.add(p.getBytes());
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) p.getLayoutParams();
            initialPositions.add(new Point(params.leftMargin, params.topMargin));
        }
    }
    public void SetContext(Context context){
        this.context = context;
    }
    public void ReleaseContext(){
        context = null;
    }

    public void addPuppet(Puppet puppet){
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) puppet.getLayoutParams();
        initialPositions.add(new Point(params.leftMargin, params.topMargin));
        puppets.add(puppet.getBytes());
        initOffStage.add(puppet.getName());
        // Load the new puppet to set initial visibility to zero
        //Puppet temp = getPuppet(puppets.size() - 1);
        //temp.setOnStage(false);
        //puppets.remove(puppets.size() - 1);
        //puppets.add(temp.getBytes());

        puppet.setOnStage(false);
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
    public Bitmap getBackground(int index){ return backgrounds.get(index);}
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
        for (int i = 0; i < initialPositions.size(); i++){
            Point p = initialPositions.get(i);
            initialXs[i] = p.x;
            initialYs[i] = p.y;
        }
        out.writeObject(showName);
        out.writeObject(puppets);
        out.writeObject(frameSequence);
        initialXs = new float[initialPositions.size()];
        initialYs = new float[initialPositions.size()];
        out.writeObject(initialXs);
        out.writeObject(initialYs);
        out.writeObject(initOffStage);
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
        initOffStage = (ArrayList<String>)in.readObject();
        int backgroundCount = in.readInt();
        for (int i = 0; i < backgroundCount; i++){
            byte[] bytes = new byte[in.readInt()];
            in.readFully(bytes);
            backgrounds.add(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        }
        for (int i = 0; i < initialXs.length; i++){
            initialPositions.add(new Point((int)initialXs[i], (int)initialYs[i]));
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
