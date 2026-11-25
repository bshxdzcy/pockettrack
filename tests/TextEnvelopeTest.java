import dev.pockettrack.TextEnvelope;
import dev.pockettrack.EdgeExit;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public final class TextEnvelopeTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        byte[] key=new byte[32],session=new byte[16],id=new byte[16];for(int i=0;i<32;i++)key[i]=(byte)i;for(int i=0;i<16;i++){session[i]=(byte)(i+32);id[i]=(byte)(i+48);}
        String text="手机语音 → Mac：你好 👋\n第二行";
        byte[] packet=TextEnvelope.seal(key,session,id,text);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(packet,12)));c.updateAAD(session);
        byte[] plain=c.doFinal(Arrays.copyOfRange(packet,12,packet.length));check(Arrays.equals(id,Arrays.copyOf(plain,16)));check(new String(plain,16,plain.length-16,StandardCharsets.UTF_8).equals(text));
        check(!Arrays.equals(TextEnvelope.proof(key,session,"mac"),TextEnvelope.proof(key,session,"phone")));
        boolean rejected=false;try{TextEnvelope.seal(key,session,id,"bad\u0000");}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        rejected=false;try{TextEnvelope.seal(key,session,id,new String(new char[8193]).replace('\0','a'));}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        if(args.length>0)Files.write(Paths.get(args[0]),Arrays.asList(Base64.getEncoder().encodeToString(packet),Base64.getEncoder().encodeToString(TextEnvelope.proof(key,session,"mac")),Base64.getEncoder().encodeToString(TextEnvelope.proof(key,session,"phone"))),StandardCharsets.UTF_8);
        EdgeExit edge=new EdgeExit();check(edge.begin(4,100,400));check(!edge.completed(20,100));check(edge.completed(80,110));edge.reset();check(!edge.begin(200,100,400));check(!edge.reserved());check(edge.begin(390,100,400));check(edge.completed(300,120));check(!edge.completed(300,300));
        System.out.println("PASS: encrypted Unicode, role-bound auth, length/control rejection and edge exit");
    }
}
