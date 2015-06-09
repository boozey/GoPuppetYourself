package com.nakedape.gopuppetyourself;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.View;

import java.util.jar.Attributes;

/**
 * Created by Nathan on 6/8/2015.
 */
public class Puppet extends View {
    private Context context;
    private Head head;
    public Puppet(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        Initialize();
    }
    private void Initialize(){
        head = new Head(context);
    }

    public class Head extends View{
        Context context;
        private UpperJaw upperJaw;
        private LowerJaw lowerJaw;

        public Head(Context context) {
            super(context);
            this.context = context;
            Initialize();
        }
        private void Initialize(){
            upperJaw = new UpperJaw(context);
            lowerJaw = new LowerJaw(context);
        }

        public class UpperJaw extends View{
            private Context context;
            public UpperJaw(Context context) {
                super(context);
                this.context = context;
                Initialize();
            }
            private void Initialize(){
                setBackground(getResources().getDrawable(R.drawable.upper_jaw_blank));
            }
        }
        public class LowerJaw extends View {
            private Context context;
            public LowerJaw(Context context) {
                super(context);
                this.context = context;
                Initialize();
            }
            private void Initialize(){
                setBackground(getResources().getDrawable(R.drawable.lower_jaw_blank));
            }
        }
    }
}
