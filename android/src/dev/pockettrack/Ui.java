package dev.pockettrack;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Small shared palette and native controls. No animation or allocation on the touch path. */
final class Ui {
    static final int BG=Color.rgb(16,20,17), SURFACE=Color.rgb(27,33,29), BORDER=Color.rgb(48,57,51);
    static final int TEXT=Color.rgb(241,243,234), MUTED=Color.rgb(169,180,171), ACCENT=Color.rgb(199,235,159);
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius,boolean border){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(border)d.setStroke(dp(c,1),BORDER);return d;
    }
    static TextView label(Context c,String value,int size,int color,boolean bold){
        TextView v=new TextView(c);v.setText(value);v.setTextColor(color);v.setTextSize(size);
        v.setFontFeatureSettings("kern");v.setIncludeFontPadding(false);v.setLineSpacing(dp(c,3),1);
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return v;
    }
    static Button button(Context c,String value,boolean primary,Runnable action){
        Button b=new Button(c);b.setText(value);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,primary?BG:TEXT}));
        b.setMinHeight(dp(c,52));b.setMinimumHeight(dp(c,52));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10));b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227E9982),shape(c,primary?TEXT:SURFACE,16,!primary),null));
        b.setOnClickListener(v->action.run());return b;
    }
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static void gap(LinearLayout parent,int size){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),size)));}
    static void line(LinearLayout parent){View v=new View(parent.getContext());v.setBackgroundColor(BORDER);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(parent.getContext(),1));p.topMargin=dp(parent.getContext(),14);p.bottomMargin=dp(parent.getContext(),14);parent.addView(v,p);}
    static final class Mark extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Mark(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float unit=Math.min(getWidth(),getHeight())/108f;c.save();c.translate((getWidth()-108*unit)/2,(getHeight()-108*unit)/2);c.scale(unit,unit);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(TEXT);
            c.drawRoundRect(23,22,85,86,15,15,paint);
            paint.setStrokeWidth(3);paint.setColor(ACCENT);c.drawLine(42,66,42,48,paint);c.drawLine(55,66,55,40,paint);
            paint.setStyle(Paint.Style.FILL);c.drawCircle(68,37,4,paint);c.restore();
        }
    }
}
