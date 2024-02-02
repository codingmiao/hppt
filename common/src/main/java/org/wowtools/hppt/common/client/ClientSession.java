package org.wowtools.hppt.common.client;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

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
    private final ClientSessionLifecycle lifecycle;

    ClientSession(int sessionId, ChannelHandlerContext channelHandlerContext, ClientSessionLifecycle lifecycle) {
        this.sessionId = sessionId;
        this.channelHandlerContext = channelHandlerContext;
        this.lifecycle = lifecycle;
    }

    /**
     * 发bytes到用户
     *
     * @param bytes bytes
     */
    public void sendToUser(byte[] bytes) {
        bytes = lifecycle.beforeSendToUser(this, bytes);
        if (null != bytes) {
            log.debug("ClientSession {} 向用户发送字节 {}", sessionId, bytes.length);
            BytesUtil.writeToChannelHandlerContext(channelHandlerContext, bytes);
            lifecycle.afterSendToUser(this, bytes);
        }
    }


    public int getSessionId() {
        return sessionId;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    void close() {
        channelHandlerContext.close();
    }


}
