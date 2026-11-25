package dev.pockettrack;

import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;

/** App-to-app authentication/encryption. The shared key is provisioned once over authorized USB. */
public final class TextEnvelope {
    public static final int MAX_TEXT_BYTES=8192;
    public static byte[] key(String hex){
        if(hex==null || !hex.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Pair first");
        byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);return b;
    }
    public static byte[] proof(byte[] key,byte[] session,String role) throws GeneralSecurityException {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        mac.update(("PocketTrack-text-v1:"+role+":").getBytes(StandardCharsets.UTF_8));mac.update(session);
        return Arrays.copyOf(mac.doFinal(),16);
    }
    public static byte[] seal(byte[] key,byte[] session,byte[] id,String text) throws GeneralSecurityException {
        byte[] utf8=text.getBytes(StandardCharsets.UTF_8);
        if(session.length!=16 || id.length!=16 || utf8.length==0 || utf8.length>MAX_TEXT_BYTES)throw new IllegalArgumentException("Text length");
        for(int i=0;i<text.length();i++)if((text.charAt(i)<32 && text.charAt(i)!='\n' && text.charAt(i)!='\t') || text.charAt(i)==127)throw new IllegalArgumentException("Control character");
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        byte[] plain=new byte[16+utf8.length];System.arraycopy(id,0,plain,0,16);System.arraycopy(utf8,0,plain,16,utf8.length);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(session);
        byte[] encrypted=cipher.doFinal(plain),result=new byte[12+encrypted.length];System.arraycopy(nonce,0,result,0,12);System.arraycopy(encrypted,0,result,12,encrypted.length);return result;
    }
}
