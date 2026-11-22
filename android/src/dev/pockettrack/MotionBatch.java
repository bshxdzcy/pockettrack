package dev.pockettrack;

import java.util.Locale;

/** Combines adjacent motion frames without reordering clicks or held-key events. */
public final class MotionBatch {
    private final StringBuilder result=new StringBuilder();
    private String motion=null;
    private double x=0,y=0;
    public void add(String line) {
        String[] fields=line.split(" ");
        boolean isMotion=fields.length==3 && (fields[0].equals("MOVE") || fields[0].equals("SCROLL"));
        if(!isMotion) {flushMotion();result.append(line).append('\n');return;}
        if(!fields[0].equals(motion))flushMotion();
        motion=fields[0];x+=Double.parseDouble(fields[1]);y+=Double.parseDouble(fields[2]);
    }
    private void flushMotion() {
        if(motion==null)return;
        // Split large batches so the receiver's per-frame bounds still hold.
        int parts=Math.max(1,(int)Math.ceil(Math.max(Math.abs(x),Math.abs(y))/500));
        for(int i=0;i<parts;i++)result.append(String.format(Locale.US,"%s %.4f %.4f\n",motion,x/parts,y/parts));
        motion=null;x=0;y=0;
    }
    public String finish(){flushMotion();return result.toString();}
}
