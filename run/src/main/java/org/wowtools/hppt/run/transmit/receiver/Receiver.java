package org.wowtools.hppt.run.transmit.receiver;

/**
 * @author liuyu
 * @date 2024/4/7
 */
public interface Receiver {
    /**
     * 接收字节
     *
     * @param bytes bytes
     */
    void receive(byte[] bytes);

    /**
     * 把字节发送出去远端
     *
     * @param bytes bytes
     */
    void send(byte[] bytes);
}
