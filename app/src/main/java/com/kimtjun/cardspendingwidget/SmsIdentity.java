package com.kimtjun.cardspendingwidget;
import java.security.*;
import java.nio.charset.StandardCharsets;
public final class SmsIdentity {
    /** Exact transport identity, not amount + displayed minute. */
    public static String key(String sender,String format,byte[][] pdus){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            md.update(sender.getBytes(StandardCharsets.UTF_8));md.update((byte)0);
            md.update(String.valueOf(format).getBytes(StandardCharsets.UTF_8));
            for(byte[] p:pdus){md.update(new byte[]{(byte)(p.length>>>24),(byte)(p.length>>>16),(byte)(p.length>>>8),(byte)p.length});md.update(p);}
            StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format("%02x",b&255));return s.toString();
        }catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
