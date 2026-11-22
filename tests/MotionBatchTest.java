import dev.pockettrack.MotionBatch;
public final class MotionBatchTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        MotionBatch b=new MotionBatch();
        for(int i=0;i<100;i++)b.add("MOVE 0.5 -0.25");
        b.add("CLICK L");b.add("MOVE 2 3");b.add("OPTION 1");b.add("OPTION 0");
        check(b.finish().equals("MOVE 50.0000 -25.0000\nCLICK L\nMOVE 2.0000 3.0000\nOPTION 1\nOPTION 0\n"));
        MotionBatch large=new MotionBatch();for(int i=0;i<30;i++)large.add("MOVE 100 -100");
        double x=0,y=0;
        for(String line:large.finish().split("\n")){
            String[] f=line.split(" ");double dx=Double.parseDouble(f[1]),dy=Double.parseDouble(f[2]);
            check(Math.abs(dx)<=500 && Math.abs(dy)<=500);x+=dx;y+=dy;
        }
        check(x==3000 && y==-3000);
        MotionBatch mixed=new MotionBatch();mixed.add("MOVE 1 2");mixed.add("SCROLL 0.2 0.3");mixed.add("DRAG 1");
        check(mixed.finish().equals("MOVE 1.0000 2.0000\nSCROLL 0.2000 0.3000\nDRAG 1\n"));
        System.out.println("PASS: batching preserves displacement, command ordering, and receiver bounds");
    }
}
