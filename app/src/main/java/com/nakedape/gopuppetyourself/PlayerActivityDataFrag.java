package com.nakedape.gopuppetyourself;

import android.app.Fragment;
import android.os.Bundle;

import java.io.File;

/**
 * Created by Nathan on 8/6/2015.
 */
public class PlayerActivityDataFrag extends Fragment {
    long pausePoint;
    File puppetShowFile;
    boolean isPlaying;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }
}
