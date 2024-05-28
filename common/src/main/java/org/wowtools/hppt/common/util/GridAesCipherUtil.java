package org.wowtools.hppt.common.util;

import java.util.Random;

/**
 * 以栅格方式在奇数位加盐，再aes加密的工具
 *
 * @author liuyu
 * @date 2024/4/16
 */
public class GridAesCipherUtil {
    private static final AesCipherUtil aesCipherUtil = new AesCipherUtil("hppt");
    private static final Random random = new Random();

    /**
     * 加密
     *
     * @param bytes bytes
     * @return bytes
     */
    public static byte[] encrypt(byte[] bytes) {
        byte[] bytes1 = new byte[bytes.length * 2];
        for (int i = 0; i < bytes1.length; i += 2) {
            bytes1[i] = bytes[i / 2];
            bytes1[i + 1] = (byte) random.nextInt(255);
        }
        return aesCipherUtil.encryptor.encrypt(bytes1);
    }

    /**
     * 解密
     *
     * @param bytes bytes
     * @return bytes
     */
    public static byte[] decrypt(byte[] bytes) {
        bytes = aesCipherUtil.descriptor.decrypt(bytes);
        byte[] bytes1 = new byte[bytes.length / 2];
        for (int i = 0; i < bytes1.length; i++) {
            bytes1[i] = bytes[i * 2];
        }
        return bytes1;
    }
}
