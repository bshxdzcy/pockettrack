package dev.pockettrack;

import java.util.ArrayList;
import java.util.List;

/** Fixed mouse + right-Alt reports. No arbitrary text or key injection interface. */
public final class HidReports {
    public static final byte[] DESCRIPTOR = hex(
        "05 01 09 02 A1 01 85 01 09 01 A1 00 " +
        "05 09 19 01 29 03 15 00 25 01 95 03 75 01 81 02 " +
        "95 01 75 05 81 03 " +
        "05 01 09 30 09 31 16 01 80 26 FF 7F 75 10 95 02 81 06 " +
        "09 38 15 81 25 7F 75 08 95 01 81 06 " +
        "05 0C 0A 38 02 15 81 25 7F 75 08 95 01 81 06 C0 C0 " +
        "05 01 09 06 A1 01 85 02 05 07 19 E0 29 E7 15 00 25 01 75 01 95 08 81 02 " +
        "75 08 95 01 81 03 15 00 25 65 19 00 29 65 75 08 95 06 81 00 C0");
    public static final class Report {
        public final int id;
        public final byte[] data;
        Report(int id,byte[] data){this.id=id;this.data=data;}
    }
    private int buttons=0,modifiers=0;
    private double moveX,moveY,wheelX,wheelY;
    private static byte[] hex(String text){String[] pieces=text.split(" ");byte[] bytes=new byte[pieces.length];for(int i=0;i<pieces.length;i++)bytes[i]=(byte)Integer.parseInt(pieces[i],16);return bytes;}
    private byte[] mouse(int x,int y,int wheel,int pan){return new byte[]{(byte)buttons,(byte)x,(byte)(x>>8),(byte)y,(byte)(y>>8),(byte)wheel,(byte)pan};}
    private byte[] keyboard(){return new byte[]{(byte)modifiers,0,0,0,0,0,0,0};}
    public synchronized byte[] current(int id){return id==1?mouse(0,0,0,0):id==2?keyboard():null;}
    public synchronized List<Report> apply(String command){
        List<Report> result=new ArrayList<>();String[] p=command.split(" ");
        switch(p[0]){
            case "MOVE":
                // The Mac applies its mouse acceleration to HID counts.
                moveX+=Double.parseDouble(p[1])/3.0;moveY+=Double.parseDouble(p[2])/3.0;
                int x=(int)moveX,y=(int)moveY;moveX-=x;moveY-=y;
                if(x!=0 || y!=0)result.add(new Report(1,mouse(x,y,0,0)));
                break;
            case "SCROLL":
                wheelX+=Double.parseDouble(p[1])/32.0;wheelY+=Double.parseDouble(p[2])/32.0;
                int horizontal=(int)wheelX,vertical=(int)wheelY;wheelX-=horizontal;wheelY-=vertical;
                while(horizontal!=0 || vertical!=0){
                    int h=Math.max(-127,Math.min(127,horizontal)),v=Math.max(-127,Math.min(127,vertical));
                    result.add(new Report(1,mouse(0,0,v,h)));horizontal-=h;vertical-=v;
                }
                break;
            case "CLICK":
                int previous=buttons;buttons|=p[1].equals("R")?2:1;result.add(new Report(1,mouse(0,0,0,0)));
                buttons=previous;result.add(new Report(1,mouse(0,0,0,0)));break;
            case "DRAG":buttons=p[1].equals("1")?(buttons|1):(buttons&~1);result.add(new Report(1,mouse(0,0,0,0)));break;
            case "OPTION":modifiers=p[1].equals("1")?0x40:0;result.add(new Report(2,keyboard()));break;
            case "PASTE":
                // Fixed Command+V only; no arbitrary key/text injection through HID.
                result.add(new Report(2,new byte[]{8,0,25,0,0,0,0,0}));
                result.add(new Report(2,keyboard()));break;
            case "RESET":buttons=0;modifiers=0;moveX=moveY=wheelX=wheelY=0;result.add(new Report(1,mouse(0,0,0,0)));result.add(new Report(2,keyboard()));break;
            default:throw new IllegalArgumentException("Unsupported input command");
        }
        return result;
    }
}
