package org.wowtools.hppt.common.server;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.util.BufferPool;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.DebugConfig;
import org.wowtools.hppt.common.util.RoughTimeUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

    private final BufferPool<SessionBytes> sendBytesQueue = new BufferPool<>(">ServerSession-sendBytesQueue");
    //上次活跃时间
    private long activeTime;

    private volatile boolean running = true;


    ServerSession(long sessionTimeout, int sessionId, LoginClientService.Client client, ServerSessionLifecycle lifecycle, Channel channel) {
        this.sessionId = sessionId;
        this.channel = channel;
        this.sessionTimeout = sessionTimeout;
        this.lifecycle = lifecycle;
        this.client = client;
        activeSession();
        startSendThread();
        client.addSession(this);
    }

    private void startSendThread() {
        Thread.startVirtualThread(() -> {
            while (running) {
                try {
                    SessionBytes sessionBytes = sendBytesQueue.poll(10, TimeUnit.SECONDS);
                    if (null == sessionBytes) {
                        continue;
                    }
                    if (DebugConfig.OpenSerialNumber) {
                        log.debug("取出session待发送缓冲区数据 >sessionBytes-SerialNumber {}", sessionBytes.getSerialNumber());
                    }
                    byte[] bytes = sessionBytes.getBytes();

                    bytes = lifecycle.beforeSendToTarget(this, bytes);

                    if (bytes != null) {
                        Throwable e = BytesUtil.writeToChannel(channel, bytes);
                        if (null != e) {
                            log.warn("BytesUtil.writeToChannel err", e);
                            throw e;
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("向目标端口发送字节 {}", bytes.length);
                        }
                        lifecycle.afterSendToTarget(this, bytes);
                    }
                } catch (Throwable e) {
                    log.warn("SendThread err", e);
                    close();
                }
            }
            log.info("{} sendThread stop", this);
        });

    }

    /**
     * 向目标端口发送字节
     *
     * @param bytes bytes
     */
    public void sendToTarget(SessionBytes bytes) {
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
        running = false;
        channel.close();
        client.removeSession(this);
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
