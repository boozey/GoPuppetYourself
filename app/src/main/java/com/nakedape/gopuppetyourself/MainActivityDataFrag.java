package com.nakedape.gopuppetyourself;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.ArrayList;

/**
 * Created by Nathan on 6/26/2015.
 */
public class MainActivityDataFrag extends Fragment {
    Bitmap currentBackground;
    ArrayList<ViewGroup.LayoutParams> layoutParamses = new ArrayList<>();
    boolean isBackstage;
    String cameraCapturePath;
    PuppetShow puppetShow;
    String currentBackgroundPath;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }
}
