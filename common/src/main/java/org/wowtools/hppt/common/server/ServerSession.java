package org.wowtools.hppt.common.server;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.RoughTimeUtil;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ServerSession {


    private final Channel channel;
    private final int sessionId;
    private final String clientId;

    private final long sessionTimeout;

    private final ServerSessionLifecycle lifecycle;


    //上次活跃时间
    private long activeTime;

    ServerSession(long sessionTimeout, int sessionId, String clientId, ServerSessionLifecycle lifecycle, Channel channel) {
        activeSession();
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.channel = channel;
        this.sessionTimeout = sessionTimeout;
        this.lifecycle = lifecycle;

    }


    /**
     * 向目标端口发送字节
     *
     * @param bytes bytes
     */
    public void sendToTarget(byte[] bytes) {
        activeSession();
        bytes = lifecycle.beforeSendToTarget(this, bytes);
        if (bytes != null) {
            BytesUtil.writeToChannel(channel, bytes);
            lifecycle.afterSendToTarget(this, bytes);
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
        channel.close();
    }

    @Override
    public String toString() {
        return "("
                + clientId + " "
                + sessionId + (isTimeOut() ? " timeout)" : " active" +
                ")");
    }

    public Channel getChannel() {
        return channel;
    }

    public String getClientId() {
        return clientId;
    }
}
