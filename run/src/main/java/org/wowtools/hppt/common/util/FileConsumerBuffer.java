package org.wowtools.hppt.common.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
     * @return bytes
     */
    public byte[] poll() {
        try (FileLock fileLock = channel.lock()) {
            if (raf.length() == 0) {
                return null;
            }
            //TODO 写入的文件win-linux是空白，分析一下
            byte[] data;
            while (true) {
                try {
                    raf.seek(0);
                    int length = (int) raf.length();
                    if (length == 0) {
                        return null;
                    }
                    data = new byte[length];
                    raf.readFully(data);
                    break;
                } catch (Exception e) {
                }
            }


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


    @Override
    public void close() throws Exception {
        channel.close();
        raf.close();
    }
}

