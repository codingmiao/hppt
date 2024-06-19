package org.wowtools.hppt.common.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
/**
 * 以文件做为缓冲池的生产者-消费者模式中的生产者
 */
public class FileProducerBuffer implements AutoCloseable{

    private final RandomAccessFile raf;
    private final FileChannel channel;

    /**
     * @param file 缓冲池文件
     */
    public FileProducerBuffer(File file) {
        try {
            raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(byte[] data) {
        try (FileLock fileLock = channel.lock()) {
            //FileLock 确保多个进程在读写文件时互斥访问
            raf.seek(raf.length());
            channel.write(ByteBuffer.wrap(data));
            channel.force(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        channel.close();
        raf.close();
    }
}

