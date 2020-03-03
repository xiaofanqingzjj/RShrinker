package net.yrom.shrinker.test.dummy;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

import net.yrom.shrinker.test.R;

public class MyTextView extends android.support.v7.widget.AppCompatTextView {
    public MyTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MyTextView);

        int color = ta.getColor(R.styleable.MyTextView_mtv_text_color, Color.RED);

        setTextColor(color);

        ta.recycle();


    }
}
