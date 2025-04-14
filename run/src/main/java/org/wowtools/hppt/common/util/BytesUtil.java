package org.wowtools.hppt.common.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.BytesList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * @author liuyu
 * @date 2023/11/27
 */
@Slf4j
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
        if (input.length == 0) {
            return input;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
                gzipOutputStream.write(input);
            }
            return baos.toByteArray();
        }
    }

    // 使用GZIP解压缩字节数组
    public static byte[] decompress(byte[] compressed) throws IOException {
        if (compressed.length == 0) {
            return compressed;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             java.util.zip.GZIPInputStream gzipInputStream = new java.util.zip.GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
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

    private static Throwable afterWrite(ChannelFuture future, Object msg) {
        boolean completed = future.awaitUninterruptibly(10, TimeUnit.SECONDS); // 同步等待完成
        if (completed) {
            if (future.isSuccess()) {
                return null;
            }
        }
        Throwable cause = future.cause();
        if (null == cause) {
            cause = new RuntimeException("写入消息未成功");
        }
        log.warn("写入消息未成功!!! timeout? {}", !completed, cause);
        ReferenceCountUtil.safeRelease(msg);
        return cause;
    }

    private static Throwable waitChannelWritable(Channel channel) {
        int i = 0;
        while (!channel.isWritable() && channel.isOpen()) {
            i++;
            if (i > 3000) {
                return new RuntimeException("waitChannelWritable timeout");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        return null;
    }

    //把字节写入ChannelHandlerContext 如果有异常则返回异常
    public static Throwable writeToChannelHandlerContext(ChannelHandlerContext ctx, byte[] bytes) {
        Throwable e = waitChannelWritable(ctx.channel());
        if (null != e) {
            return e;
        }
        if (!ctx.channel().isOpen()) {
            return new RuntimeException("channel已关闭");
        }
        ByteBuf byteBuf = bytes2byteBuf(ctx, bytes);
        ChannelFuture future = ctx.writeAndFlush(byteBuf);
        return afterWrite(future, byteBuf);
    }

    //把字节写入Channel 如果有异常则返回异常
    public static Throwable writeToChannel(Channel channel, byte[] bytes) {
        Throwable e = waitChannelWritable(channel);
        if (null != e) {
            return e;
        }
        if (!channel.isOpen()) {
            return new RuntimeException("channel已关闭");
        }
        ByteBuf byteBuf = bytes2byteBuf(channel, bytes);
        ChannelFuture future = channel.writeAndFlush(byteBuf);
        return afterWrite(future, byteBuf);
    }

    //把对象写入Channel 如果有异常则返回异常
    public static Throwable writeObjToChannel(Channel channel, Object obj) {
        Throwable e = waitChannelWritable(channel);
        if (null != e) {
            return e;
        }
        if (!channel.isOpen()) {
            return new RuntimeException("channel已关闭");
        }
        ChannelFuture future = channel.writeAndFlush(obj);
        return afterWrite(future, obj);
    }

    public static byte[] byteBuf2bytes(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        return bytes;
    }


    //把bytes集合转成BytesListPb对应的bytes
    public static byte[] bytesCollection2PbBytes(Collection<byte[]> bytesCollection) {
        return new BytesList(bytesCollection).toProto().build().toByteArray();
    }

    //BytesListPb对应的bytes转成list
    public static BytesList pbBytes2BytesList(byte[] pbBytes) {
        return new BytesList(pbBytes);
    }

}
