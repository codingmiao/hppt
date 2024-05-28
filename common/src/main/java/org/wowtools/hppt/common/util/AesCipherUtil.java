package org.wowtools.hppt.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * aes加解密工具
 *
 * @author liuyu
 * @date 2023/12/18
 */
@Slf4j
public class AesCipherUtil {

    public final Encryptor encryptor;

    public final Descriptor descriptor;


    public AesCipherUtil(String strKey, long ts) {
        strKey = strKey + (ts / (30 * 60 * 1000));
        SecretKey key = generateKey(strKey);
        this.encryptor = new Encryptor(key);
        this.descriptor = new Descriptor(key);
    }

    public AesCipherUtil(String strKey) {
        SecretKey key = generateKey(strKey);
        this.encryptor = new Encryptor(key);
        this.descriptor = new Descriptor(key);
    }

    /**
     * 加密器
     */
    public static final class Encryptor {
        private final Cipher cipher;

        private Encryptor(SecretKey key) {
            try {
                cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public byte[] encrypt(byte[] bytes) {
            try {
                return cipher.doFinal(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    /**
     * 解密器
     */
    public static final class Descriptor {
        private final Cipher cipher;

        private Descriptor(SecretKey key) {
            try {
                cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public byte[] decrypt(byte[] bytes) {
            try {
                return cipher.doFinal(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }


    private static SecretKey generateKey(String input) {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");// 创建AES的Key生产者
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(input.getBytes());
            kgen.init(128, secureRandom);

            SecretKey secretKey = kgen.generateKey();// 根据用户密码，生成一个密钥
            return secretKey;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


}
