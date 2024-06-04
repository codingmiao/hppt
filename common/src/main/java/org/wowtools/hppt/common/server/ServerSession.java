package org.wowtools.hppt.common.server;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.RoughTimeUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ServerSession {

    private final LoginClientService.Client client;
    private final Channel channel;
    private final int sessionId;

    private final long sessionTimeout;

    private final ServerSessionLifecycle lifecycle;

    private final BlockingQueue<byte[]> sendBytesQueue = new LinkedBlockingQueue<>();
    //上次活跃时间
    private long activeTime;

    private final Thread sendThread;

    ServerSession(long sessionTimeout, int sessionId, LoginClientService.Client client, ServerSessionLifecycle lifecycle, Channel channel) {
        this.sessionId = sessionId;
        this.channel = channel;
        this.sessionTimeout = sessionTimeout;
        this.lifecycle = lifecycle;
        this.client = client;
        activeSession();
        sendThread = startSendThread();
    }

    private Thread startSendThread() {
        return Thread.startVirtualThread(() -> {
            while (true) {
                byte[] bytes;
                try {
                    bytes = sendBytesQueue.take();
                } catch (InterruptedException e) {
                    log.info("{} sendThread stop",this);
                    break;
                }
                bytes = lifecycle.beforeSendToTarget(this, bytes);
                if (bytes != null) {
                    BytesUtil.writeToChannel(channel, bytes);
                    lifecycle.afterSendToTarget(this, bytes);
                }
            }
        });

    }

    /**
     * 向目标端口发送字节
     *
     * @param bytes bytes
     */
    public void sendToTarget(byte[] bytes) {
        activeSession();
        if (bytes != null) {
            sendBytesQueue.add(bytes);
        }
    }

    /**
     * 保持会话活跃
     */
    public void activeSession() {
        activeTime = RoughTimeUtil.getTimestamp();
    }

    //是否需要向用户侧确认session是否存活
    public boolean isNeedCheckActive() {
        return activeTime + sessionTimeout <= RoughTimeUtil.getTimestamp();
    }

    //session是否超时
    public boolean isTimeOut() {
        return activeTime + (sessionTimeout * 2) <= RoughTimeUtil.getTimestamp();
    }

    public int getSessionId() {
        return sessionId;
    }


    void close() {
        sendThread.interrupt();
        channel.close();
    }

    @Override
    public String toString() {
        return "("
                + client.clientId + " "
                + sessionId + (isTimeOut() ? " timeout)" : " active" +
                ")");
    }

    public Channel getChannel() {
        return channel;
    }

    public LoginClientService.Client getClient() {
        return client;
    }
}
