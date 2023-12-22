package test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class ByteArrayCompressionExample {

    public static void main(String[] args) {
        String inputString = "123.";
        byte[] inputBytes = inputString.getBytes();

        try {
            // 压缩字节数组
            byte[] compressedBytes = compress(inputBytes);

            // 打印压缩前后的大小
            System.out.println("Original Size: " + inputBytes.length + " bytes");
            System.out.println("Compressed Size: " + compressedBytes.length + " bytes");

            // 解压缩字节数组
            byte[] decompressedBytes = decompress(compressedBytes);

            // 将解压缩后的字节数组转换为字符串并打印
            String decompressedString = new String(decompressedBytes);
            System.out.println("Decompressed String: " + decompressedString);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
