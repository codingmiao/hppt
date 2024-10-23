package org.wowtools.hppt.common.client;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 客户端会话
 *
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ClientSession {
    private final int sessionId;
    private final ChannelHandlerContext channelHandlerContext;

    private final BlockingQueue<byte[]> sendToUserBytesQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;

    ClientSession(int sessionId, ChannelHandlerContext channelHandlerContext, ClientSessionLifecycle lifecycle) {
        this.sessionId = sessionId;
        this.channelHandlerContext = channelHandlerContext;
        Thread.startVirtualThread(() -> {
            while (running) {
                byte[] bytes;
                try {
                    bytes = sendToUserBytesQueue.poll(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    continue;
                }
                if (null == bytes) {
                    continue;
                }
                bytes = lifecycle.beforeSendToUser(this, bytes);
                if (null != bytes) {
                    log.debug("ClientSession {} 向用户发送字节 {}", sessionId, bytes.length);
                    Throwable e = BytesUtil.writeToChannelHandlerContext(channelHandlerContext, bytes);
                    if (null != e) {
                        log.warn("向用户发送字节异常",e);
                        close();
                    }else if (log.isDebugEnabled()){
                        log.debug("ClientSession {} 向用户发送字节完成 {}", sessionId, bytes.length);
                    }
                    lifecycle.afterSendToUser(this, bytes);
                }
            }
            log.debug("ClientSession {} 接收线程结束", sessionId);
        });
    }

    /**
     * 发bytes到用户
     *
     * @param bytes bytes
     */
    public void sendToUser(byte[] bytes) {
        sendToUserBytesQueue.add(bytes);
    }


    public int getSessionId() {
        return sessionId;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    void close() {
        running = false;
        channelHandlerContext.close();
    }


}
