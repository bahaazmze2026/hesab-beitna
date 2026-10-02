package com.hesabbeitna.app;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class CryptoCheck {
    static int checks=0;
    static void check(boolean test){checks++;if(!test)throw new AssertionError("crypto check "+checks);}
    static void rejects(byte[] bytes,char[] pass){boolean failed=false;try{BackupCrypto.decrypt(bytes,pass);}catch(Exception e){failed=true;}check(failed);}
    public static void main(String[] args) throws Exception {
        byte[] data="بيانات بيتنا: 125.50 جنيه — account, transfer, due".getBytes(StandardCharsets.UTF_8);
        char[] pass="Acceptance-test-only-2026".toCharArray();
        byte[] a=BackupCrypto.encrypt(data,pass),b=BackupCrypto.encrypt(data,pass);
        check(Arrays.equals(data,BackupCrypto.decrypt(a,pass)));
        check(!Arrays.equals(a,b));
        check(!new String(a,StandardCharsets.UTF_8).contains("125.50"));
        rejects(a,"wrong-password".toCharArray());
        for(int offset:new int[]{0,5,20,35,a.length-1}) {byte[] bad=a.clone();bad[offset]^=1;rejects(bad,pass);}
        rejects(Arrays.copyOf(a,a.length-1),pass);rejects(new byte[3],pass);
        boolean shortPassword=false;try{BackupCrypto.encrypt(data,"123".toCharArray());}catch(IllegalArgumentException e){shortPassword=true;}check(shortPassword);
        byte[] large=new byte[2*1024*1024];for(int i=0;i<large.length;i++)large[i]=(byte)(i%127);
        check(Arrays.equals(large,BackupCrypto.decrypt(BackupCrypto.encrypt(large,pass),pass)));
        Arrays.fill(pass,'\0');
        System.out.println("PASS: "+checks+" authenticated backup acceptance checks");
    }
}
