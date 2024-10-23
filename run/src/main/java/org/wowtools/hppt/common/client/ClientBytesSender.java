package org.wowtools.hppt.common.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.wowtools.hppt.common.util.RoughTimeUtil;

/**
 * @author liuyu
 * @date 2024/2/1
 */
public interface ClientBytesSender {

    /**
     * sessionId回调函数
     */
    public static abstract class SessionIdCallBack {
        public final long createTime = RoughTimeUtil.getTimestamp();
        public final ChannelHandlerContext channelHandlerContext;

        public SessionIdCallBack(ChannelHandlerContext channelHandlerContext) {
            this.channelHandlerContext = channelHandlerContext;
        }

        public abstract void cb(int sessionId);
    }

    /**
     * 用户建立连接后，准备新建一个ClientSession前触发
     *
     * @param port 本地端口
     * @param ctx  建立连接对应的netty ctx
     * @param cb   生成一个唯一的sessionId返回，一般需要借助服务端接口来分配id
     */
    void connected(int port, ChannelHandlerContext ctx, SessionIdCallBack cb);

    /**
     * 向目标发送字节的具体方式，如post请求，websocket等
     *
     * @param clientSession clientSession
     * @param bytes         bytes
     */
    void sendToTarget(ClientSession clientSession, byte[] bytes);
}
