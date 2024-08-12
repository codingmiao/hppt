package org.wowtools.hppt.common.util;


import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GridAesCipherUtilTest {

    public static void main(String[] args) {
        String msg = "1234567890qwertyuiop";
        byte[] byte1 = GridAesCipherUtil.encrypt(msg.getBytes(StandardCharsets.UTF_8));
        byte[] byte2 = GridAesCipherUtil.encrypt(msg.getBytes(StandardCharsets.UTF_8));
        System.out.println(Base64.getEncoder().encodeToString(byte1));
        System.out.println(Base64.getEncoder().encodeToString(byte2));
        System.out.println(new String(GridAesCipherUtil.decrypt(byte1)));
        System.out.println(new String(GridAesCipherUtil.decrypt(byte2)));
    }
}
