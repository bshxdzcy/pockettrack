package dev.pockettrack;

/** Edge gestures never become mouse movement or clicks, even when canceled. Values are dp. */
public final class EdgeExit {
    private float startX,startY;private int side;
    public boolean begin(float x,float y,float width){startX=x;startY=y;side=x<=24?1:x>=width-24?-1:0;return side!=0;}
    public boolean reserved(){return side!=0;}
    public boolean completed(float x,float y){return side!=0 && (x-startX)*side>=56 && Math.abs(y-startY)<=100;}
    public void reset(){side=0;}
}
