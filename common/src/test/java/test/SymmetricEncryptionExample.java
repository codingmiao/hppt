package test;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

public class SymmetricEncryptionExample {

    public static void main(String[] args) {
        try {
            Random r = new Random(233);
            // 生成随机密钥
            SecretKey secretKey = generateKey("test");
            String keyStr = encodeKeyToString(secretKey);
            System.out.println(keyStr);

            // 原始
            byte[] bt = new byte[1000_0000];
            for (int i = 0; i < bt.length; i++) {
                bt[i] = (byte) (r.nextInt(255) - 128);
            }
            System.out.println("org len: " +bt.length);

            //压缩
            bt = compress(bt);
            System.out.println("gzip len: " +bt.length);

            // 加密
            bt = encrypt(bt, secretKey);
            System.out.println("encrypt len: " +bt.length);


            // 解密
            SecretKey secretKey1 = decodeStringToKey(keyStr);
            bt = decrypt(bt, secretKey1);


            //解压缩
            bt = decompress(bt);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 生成随机密钥
    private static SecretKey generateSecretKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128); // 使用128位密钥
        return keyGenerator.generateKey();
    }

    // 使用密钥加密数据
    private static byte[] encrypt(byte[] data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    // 使用密钥解密数据
    private static byte[] decrypt(byte[] encryptedData, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(encryptedData);
    }

    private static SecretKey generateKey(String input) throws Exception {
//        input += System.currentTimeMillis()/86400000;
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128, new SecureRandom(input.getBytes(StandardCharsets.UTF_8))); // 使用128位密钥
        return keyGenerator.generateKey();
    }

    // 将密钥转换为Base64编码的字符串
    private static String encodeKeyToString(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // 将Base64编码的字符串转换为密钥
    private static SecretKey decodeStringToKey(String encodedKey) {
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }


    // 使用GZIP压缩字节数组
    private static byte[] compress(byte[] input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
            gzipOutputStream.write(input);
        }
        return baos.toByteArray();
    }

    // 使用GZIP解压缩字节数组
    private static byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        try (java.util.zip.GZIPInputStream gzipInputStream = new java.util.zip.GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }

}
