package org.wowtools.hppt.cs.service;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * @author liuyu
 * @date 2023/12/20
 */
@Slf4j
public class ServerSession {
    private final int sessionId;
    private final String clientId;
    private final String remoteHost;
    private final int remotePort;

    private final ChannelHandlerContext ctx;

    private final BlockingQueue<byte[]> sendQueue = new ArrayBlockingQueue<>(1024);

    public ServerSession(int sessionId, String clientId, String remoteHost, int remotePort, ChannelHandlerContext ctx) {
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.ctx = ctx;
    }

    /**
     * 发bytes到用户客户端
     *
     * @param bytes bytes
     */
    public void putBytes(byte[] bytes) {
        log.debug("ClientSession {} 收到服务端发来的字节数 {}", sessionId, bytes.length);
        BytesUtil.writeToChannelHandlerContext(ctx,bytes);
    }

    /**
     * 发消息到真实端口 实际上是发到队列中，等待客户端发来的http请求取走，所以会发生阻塞
     *
     * @param bytes bytes
     */
    public void sendBytes(byte[] bytes) {
        try {
            sendQueue.put(bytes);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return ctx;
    }

    public void close() {
        ctx.close();
    }

    /**
     * 取出需要发送回客户端的SessionBytes
     *
     * @return 无数据则返回null
     */
    public byte[] fetchSendSessionBytes() {
        List<byte[]> list = new LinkedList<>();
        sendQueue.drainTo(list);
        if (list.isEmpty()) {
            return null;
        }
        return BytesUtil.merge(list);
    }
}
