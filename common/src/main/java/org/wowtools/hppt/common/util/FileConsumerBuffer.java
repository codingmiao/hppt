package org.wowtools.hppt.common.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * 以文件做为缓冲池的生产者-消费者模式中的消费者
 */
public class FileConsumerBuffer implements AutoCloseable {
    private final RandomAccessFile raf;
    private final FileChannel channel;

    /**
     * @param file 缓冲池文件
     */
    public FileConsumerBuffer(File file) {
        try {
            raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 取出缓冲池中的数据
     *
     * @param maxBytesSize 最多取多少字节
     * @return bytes
     */
    public byte[] poll(int maxBytesSize) {
        try (FileLock fileLock = channel.lock()) {
            if (raf.length() == 0) {
                return null;
            }

            raf.seek(0);
            int length = (int) Math.min(raf.length(), maxBytesSize);
            byte[] data = new byte[length];
            raf.readFully(data);

            long pointer = raf.getFilePointer();
            byte[] remainingBytes = new byte[(int) (raf.length() - pointer)];
            raf.readFully(remainingBytes);
            raf.setLength(0);
            raf.seek(0);
            raf.write(remainingBytes);
            return data;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 取出缓冲池中的数据，最多65536个字节
     *
     * @return bytes
     */
    public byte[] poll() {
        return poll(65536);
    }

    @Override
    public void close() throws Exception {
        channel.close();
        raf.close();
    }
}

