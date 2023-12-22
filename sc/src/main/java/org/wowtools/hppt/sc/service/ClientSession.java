package org.wowtools.hppt.sc.service;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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

    private final BlockingQueue<byte[]> clientSessionSendQueue = new ArrayBlockingQueue<>(1024);


    public ClientSession(int sessionId, ChannelHandlerContext channelHandlerContext) {
        this.sessionId = sessionId;
        this.channelHandlerContext = channelHandlerContext;
        ClientSessionService.awakenSendThread();
    }

    /**
     * 发bytes到客户端
     *
     * @param bytes bytes
     */
    public void putBytes(byte[] bytes) {
        log.debug("ClientSession {} 收到服务端发来的字节数 {}", sessionId, bytes.length);
        ByteBuf msg = Unpooled.copiedBuffer(bytes);
        channelHandlerContext.writeAndFlush(msg);
    }

    /**
     * 发消息到真实端口 实际上是发到队列中，等待服http发送线程取走，所以会发生阻塞
     *
     * @param bytes bytes
     */
    public void sendBytes(byte[] bytes) {
        try {
            clientSessionSendQueue.put(bytes);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ClientSessionService.awakenSendThread();
    }

    public int getSessionId() {
        return sessionId;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    public void close() {
        channelHandlerContext.close();
    }

    /**
     * 取出需要发送到服务端的SessionBytes
     *
     * @return 无数据则返回null
     */
    public byte[] fetchSendSessionBytes() {
        List<byte[]> list = new LinkedList<>();
        clientSessionSendQueue.drainTo(list);
        if (list.isEmpty()) {
            return null;
        }
        return BytesUtil.merge(list);
    }
}
