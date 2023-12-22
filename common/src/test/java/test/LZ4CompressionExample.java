package test;


import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class LZ4CompressionExample {

    public static void main(String[] args) {
        Random r = new Random(233);
        byte[] bt = new byte[1000_0000];
        for (int i = 0; i < bt.length; i++) {
            bt[i] = (byte) (r.nextInt(255) - 128);
        }
        // 将字符串转换为字节数组
        byte[] inputBytes = bt;
        int orgLen = inputBytes.length;

        // 压缩字节数组
        byte[] compressedBytes = compress(inputBytes);

        // 打印压缩前后的大小
        System.out.println("Original Size: " + inputBytes.length + " bytes");
        System.out.println("Compressed Size: " + compressedBytes.length + " bytes");

        // 解压缩字节数组
        byte[] decompressedBytes = decompress(compressedBytes,orgLen);

        // 将解压缩后的字节数组转换为字符串并打印
        String decompressedString = new String(decompressedBytes, StandardCharsets.UTF_8);
        System.out.println("Decompressed String: " + decompressedString.length());
    }

    // 使用LZ4压缩字节数组
    private static byte[] compress(byte[] input) {
        LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
        LZ4Compressor compressor = lz4Factory.fastCompressor();

        int maxCompressedLength = compressor.maxCompressedLength(input.length);
        byte[] compressedOutput = new byte[maxCompressedLength];

        int compressedLength = compressor.compress(input, 0, input.length, compressedOutput, 0, maxCompressedLength);

        // 如果需要确保压缩后的数组大小，可以使用Arrays.copyOfRange
        return Arrays.copyOfRange(compressedOutput, 0, compressedLength);
    }

    // 使用LZ4解压缩字节数组
    private static byte[] decompress(byte[] compressed,int orgLen) {
        LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
        LZ4FastDecompressor decompressor = lz4Factory.fastDecompressor();

        byte[] decompressedOutput = new byte[orgLen];

        decompressor.decompress(compressed, 0, decompressedOutput, 0, orgLen);
        return decompressedOutput;
    }
}
