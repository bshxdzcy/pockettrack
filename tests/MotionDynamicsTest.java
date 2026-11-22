import dev.pockettrack.MotionDynamics;
public final class MotionDynamicsTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        MotionDynamics slow=new MotionDynamics(),fast=new MotionDynamics();float a=0,b=0;
        for(int i=0;i<30;i++){a=slow.pointerGain(0.5f,16,4);b=fast.pointerGain(8,8,4);}
        check(a==4 && b>9 && b<=11.2f);
        MotionDynamics one=new MotionDynamics(),many=new MotionDynamics();
        one.recordScroll(10,40,8);many.recordScroll(10,40,8);
        double[] total=one.step(160);double x=0,y=0,previous=Double.MAX_VALUE;
        for(int i=0;i<10;i++){double[] delta=many.step(16);x+=delta[0];y+=delta[1];check(delta[1]<previous);previous=delta[1];}
        check(Math.abs(total[0]-x)<0.001 && Math.abs(total[1]-y)<0.001);
        many.reset();check(!many.hasMomentum());
        System.out.println("PASS: slow precision, fast acceleration, time-independent momentum and cancellation");
    }
}
