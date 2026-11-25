import dev.pockettrack.HidReports;
import java.util.*;
public final class HidReportsTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int axis(byte[] r,int i){return (short)((r[i]&255)|((r[i+1]&255)<<8));}
    static boolean zero(byte[] r){for(byte b:r)if(b!=0)return false;return true;}
    public static void main(String[] args){
        HidReports hid=new HidReports();
        int x=0,y=0;
        for(int i=0;i<120;i++)for(HidReports.Report r:hid.apply("MOVE 0.75 -1.5")){x+=axis(r.data,1);y+=axis(r.data,3);}
        check(x==30 && y==-60,"fractional movement and signed axes");
        byte[] move=hid.apply("MOVE 1500 -1500").get(0).data;
        check(axis(move,1)==500 && axis(move,3)==-500,"16-bit axes");
        hid.apply("RESET");int wheel=0,pan=0;
        for(int i=0;i<128;i++)for(HidReports.Report r:hid.apply("SCROLL 0.5 -1")){pan+=r.data[6];wheel+=r.data[5];}
        check(pan==2 && wheel==-4,"fractional scrolling");
        wheel=0;for(HidReports.Report r:hid.apply("SCROLL 0 16000")){check(Math.abs(r.data[5])<=127,"wheel report limit");wheel+=r.data[5];}
        check(wheel==500,"large scroll preserves distance");
        check(hid.apply("OPTION 1").get(0).id==2,"keyboard report id");
        byte[] keys=hid.current(2);check(keys.length==8 && keys[0]==0x40,"right Alt only");
        for(int i=1;i<8;i++)check(keys[i]==0,"no extra keys");
        check(zero(hid.apply("OPTION 0").get(0).data),"release Option");
        hid.apply("DRAG 1");List<HidReports.Report> click=hid.apply("CLICK R");
        check(click.get(0).data[0]==3 && click.get(1).data[0]==1,"click preserves held drag");
        hid.apply("OPTION 1");List<HidReports.Report> reset=hid.apply("RESET");
        check(reset.size()==2 && zero(reset.get(0).data) && zero(reset.get(1).data),"release all on exit");
        List<HidReports.Report> paste=hid.apply("PASTE");
        check(paste.size()==2 && paste.get(0).id==2 && paste.get(0).data[0]==8 && paste.get(0).data[2]==25,"Command V only");
        check(zero(paste.get(1).data),"paste releases all keys");
        for(HidReports.Report r:paste)for(int i=2;i<8;i++)check(r.data[i]!=40,"paste never sends Return");
        // Parse HID short items independently: verify report widths and balanced collections.
        int size=0,count=0,id=0,collections=0;Map<Integer,Integer> bits=new HashMap<>();
        byte[] descriptor=HidReports.DESCRIPTOR;
        for(int i=0;i<descriptor.length;){
            int prefix=descriptor[i++]&255,n=prefix&3;if(n==3)n=4;
            check(i+n<=descriptor.length && prefix!=254,"valid short item");
            int value=0;for(int j=0;j<n;j++)value|=(descriptor[i++]&255)<<(8*j);
            switch(prefix&252){case 116:size=value;break;case 148:count=value;break;case 132:id=value;break;
                case 128:bits.put(id,bits.getOrDefault(id,0)+size*count);break;
                case 160:collections++;break;case 192:collections--;check(collections>=0,"balanced collections");break;}
        }
        check(collections==0 && bits.size()==2 && bits.get(1)==56 && bits.get(2)==64,"descriptor matches mouse and keyboard report lengths");
        System.out.println("PASS: HID descriptor, signed/fractional motion, scroll, right Option, drag and releases");
    }
}
