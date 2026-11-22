package dev.pockettrack;

/** Speed-sensitive pointer gain and time-based scroll momentum, independent of UI. */
public final class MotionDynamics {
    private double pointerVelocity, vx, vy;
    private double flingElapsed;
    public void reset(){pointerVelocity=0;vx=0;vy=0;flingElapsed=0;}
    public float pointerGain(float distance, long elapsedMs, float base) {
        double dt=Math.max(1,Math.min(50,elapsedMs));
        double alpha=dt/(dt+24.0);
        pointerVelocity+=alpha*(distance/dt-pointerVelocity);
        return (float)(base*(1+Math.min(1.8,Math.max(0,(pointerVelocity-0.08)*2.5))));
    }
    public void recordScroll(float dx,float dy,long elapsedMs) {
        double dt=Math.max(1,Math.min(50,elapsedMs));
        double alpha=dt/(dt+12.0);
        vx+=alpha*(dx/dt-vx);vy+=alpha*(dy/dt-vy);
        double speed=Math.hypot(vx,vy);
        if(speed>10){vx*=10/speed;vy*=10/speed;}
        flingElapsed=0;
    }
    public boolean hasMomentum(){return Math.hypot(vx,vy)>0.025 && flingElapsed<1400;}
    public boolean shouldFling(){return Math.hypot(vx,vy)>0.25;}
    public double[] step(long elapsedMs) {
        double dt=Math.max(1,elapsedMs),decay=Math.exp(-dt/220.0);
        double dx=vx*220*(1-decay),dy=vy*220*(1-decay);
        vx*=decay;vy*=decay;flingElapsed+=dt;
        return new double[]{dx,dy};
    }
}
