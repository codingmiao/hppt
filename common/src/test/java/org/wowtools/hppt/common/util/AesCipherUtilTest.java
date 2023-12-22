package org.wowtools.hppt.common.util;


import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

public class AesCipherUtilTest {

    public static void main(String[] args) throws Exception {
        System.out.println("---------------");
        t2();
    }

    private static void t1() throws Exception {

        SecretKey key1;
        {
            Random r = new Random();
            byte[] se = new byte[16];
            for (int i = 0; i < se.length; i++) {
                se[i] = (byte) (r.nextInt(255) - 128);
            }
            key1 = new SecretKeySpec(se, 0, se.length, "AES");
        }

        byte[] bts = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] r;
        {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key1);
            r = cipher.doFinal(bts);
            System.out.println(BytesUtil.bytes2base64(r));
        }
        {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key1);
            byte[] r1 = cipher.doFinal(r);
            System.out.println(new String(r1, StandardCharsets.UTF_8));
        }
    }

    private static void t2() throws Exception{
        Random random = new Random(233);
        byte[] bts = new byte[111];
        for (int i = 0; i < bts.length; i++) {
            bts[i] = (byte) (random.nextInt(255) - 128);
        }
        System.out.println(bts.length);
        AesCipherUtil aesCipherUtil = new AesCipherUtil("yntdjkmjq",System.currentTimeMillis());
        byte[] r = aesCipherUtil.encryptor.encrypt(bts);
        System.out.println(r.length);
//        System.out.println(BytesUtil.bytes2base64(r));

        r = aesCipherUtil.descriptor.decrypt(r);
//        System.out.println(new String(r,StandardCharsets.UTF_8));

        if (bts.length != r.length) {
            throw new RuntimeException();
        }
        for (int i = 0; i < bts.length; i++) {
            if (r[i] != bts[i]) {
                throw new RuntimeException();
            }
        }
    }
}
