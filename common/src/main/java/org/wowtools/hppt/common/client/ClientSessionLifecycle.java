package org.wowtools.hppt.common.client;

import io.netty.channel.ChannelHandlerContext;

/**
 * ClientSession的生命周期，包含ClientSession从创建、交互、销毁各过程的触发事件
 *
 * @author liuyu
 * @date 2024/1/4
 */
public interface ClientSessionLifecycle {
    /**
     * 用户建立连接后，准备新建一个ClientSession前触发
     *
     * @param port 本地端口
     * @param ctx  建立连接对应的netty ctx
     * @return 生成一个唯一的sessionId返回，一般需要借助服务端接口来分配id
     */
    int connected(int port, ChannelHandlerContext ctx);

    /**
     * ClientSession新建完成后触发
     *
     * @param clientSession ClientSession
     */
    default void created(ClientSession clientSession) {

    }

    /**
     * 发送字节给用户前触发
     *
     * @param clientSession ClientSession
     * @param bytes         发送的字节
     * @return 修改后的字节，若不需要修改直接返回bytes，返回null则表示忽略掉此字节
     */
    default byte[] beforeSendToUser(ClientSession clientSession, byte[] bytes) {
        return bytes;
    }

    /**
     * 发送字节给用户后触发
     *
     * @param clientSession ClientSession
     * @param bytes         发送的字节
     */
    default void afterSendToUser(ClientSession clientSession, byte[] bytes) {
    }

    /**
     * 发送字节给目标端口的具体操作，例如通过http post发送
     *
     * @param clientSession ClientSession
     * @param bytes         发送的字节
     */
    void sendToTarget(ClientSession clientSession, byte[] bytes);

    /**
     * 发送字节给目标端口后触发
     *
     * @param clientSession ClientSession
     * @param bytes         发送的字节
     */
    default void afterSendToTarget(ClientSession clientSession, byte[] bytes) {
    }

    /**
     * 关闭后触发
     *
     * @param clientSession ClientSession
     */
    default void closed(ClientSession clientSession) {

    }
}
