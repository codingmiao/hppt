package org.wowtools.hppt.common.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

/**
 * @author liuyu
 * @date 2023/11/27
 */
public class BytesUtil {
    /**
     * 按每个数组的最大长度限制，将一个数组拆分为多个
     *
     * @param originalArray 原数组
     * @param maxChunkSize  最大长度限制
     * @return 拆分结果
     */
    public static byte[][] splitBytes(byte[] originalArray, int maxChunkSize) {
        int length = originalArray.length;
        int numOfChunks = (int) Math.ceil((double) length / maxChunkSize);
        byte[][] splitArrays = new byte[numOfChunks][];

        for (int i = 0; i < numOfChunks; i++) {
            int start = i * maxChunkSize;
            int end = Math.min((i + 1) * maxChunkSize, length);
            int chunkSize = end - start;

            byte[] chunk = new byte[chunkSize];
            System.arraycopy(originalArray, start, chunk, 0, chunkSize);

            splitArrays[i] = chunk;
        }

        return splitArrays;
    }

    /**
     * 合并字节集合为一个byte[]
     *
     * @param collection 字节集合
     * @return byte[]
     */
    public static byte[] merge(Collection<byte[]> collection) {
        byte[] bytes;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            for (byte[] byteArray : collection) {
                byteArrayOutputStream.write(byteArray);
            }
            bytes = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bytes;
    }

    /**
     * bytes转base64字符串
     *
     * @param bytes bytes
     * @return base64字符串
     */
    public static String bytes2base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * base64字符串转bytes
     *
     * @param base64 base64字符串
     * @return bytes
     */
    public static byte[] base642bytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    // 使用GZIP压缩字节数组
    public static byte[] compress(byte[] input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
            gzipOutputStream.write(input);
        }
        return baos.toByteArray();
    }

    // 使用GZIP解压缩字节数组
    public static byte[] decompress(byte[] compressed) throws IOException {
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

    public static ByteBuf bytes2byteBuf(ChannelHandlerContext ctx, byte[] bytes) {
        ByteBuf byteBuf = ctx.alloc().buffer(bytes.length, bytes.length);
        byteBuf.writeBytes(bytes);
        return byteBuf;
    }

    public static ByteBuf bytes2byteBuf(Channel ctx, byte[] bytes) {
        ByteBuf byteBuf = ctx.alloc().buffer(bytes.length, bytes.length);
        byteBuf.writeBytes(bytes);
        return byteBuf;
    }

    //把字节写入ChannelHandlerContext
    public static void writeToChannelHandlerContext(ChannelHandlerContext ctx, byte[] bytes) {
        ByteBuf byteBuf = bytes2byteBuf(ctx, bytes);
        ctx.writeAndFlush(byteBuf);
    }

    //把字节写入Channel
    public static void writeToChannel(Channel channel, byte[] bytes) {
        ByteBuf byteBuf = bytes2byteBuf(channel, bytes);
        channel.writeAndFlush(byteBuf);
    }

    public static byte[] byteBuf2bytes(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        return bytes;
    }


}
