package com.hesabbeitna.app;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Portable authenticated backup envelope; independent of Android's device key. */
public final class BackupCrypto {
    private BackupCrypto() {}
    public static final int MAX_SIZE=32*1024*1024;
    private static final byte[] MAGIC={72,66,66,49};
    private static SecretKeySpec derive(char[] password,byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password,salt,210_000,256);
        byte[] raw=null;
        try {
            raw=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(raw,"AES");
        } finally { spec.clearPassword(); if(raw!=null)Arrays.fill(raw,(byte)0); }
    }
    public static byte[] encrypt(byte[] plaintext,char[] password) throws GeneralSecurityException {
        if(password.length<8) throw new IllegalArgumentException("كلمة المرور: 8 أحرف على الأقل");
        if(plaintext.length>MAX_SIZE-48) throw new IllegalArgumentException("حجم النسخة أكبر من الحد المدعوم");
        byte[] salt=new byte[16], iv=new byte[12];
        SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,derive(password,salt),new GCMParameterSpec(128,iv));
        cipher.updateAAD(MAGIC);
        byte[] encrypted=cipher.doFinal(plaintext);
        byte[] result=new byte[32+encrypted.length];
        System.arraycopy(MAGIC,0,result,0,4);System.arraycopy(salt,0,result,4,16);
        System.arraycopy(iv,0,result,20,12);System.arraycopy(encrypted,0,result,32,encrypted.length);
        Arrays.fill(encrypted,(byte)0);
        return result;
    }
    public static byte[] decrypt(byte[] backup,char[] password) throws GeneralSecurityException {
        if(backup.length<48||backup.length>MAX_SIZE) throw new IllegalArgumentException("حجم ملف النسخة غير صالح");
        if(!Arrays.equals(Arrays.copyOfRange(backup,0,4),MAGIC)) throw new IllegalArgumentException("الملف ليس نسخة Meow Budget");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,derive(password,Arrays.copyOfRange(backup,4,20)),new GCMParameterSpec(128,Arrays.copyOfRange(backup,20,32)));
        cipher.updateAAD(MAGIC);
        return cipher.doFinal(backup,32,backup.length-32);
    }
}
