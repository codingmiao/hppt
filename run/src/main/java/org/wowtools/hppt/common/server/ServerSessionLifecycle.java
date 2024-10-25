package org.wowtools.hppt.common.server;


import org.wowtools.hppt.common.pojo.SendAbleSessionBytes;
import org.wowtools.hppt.common.pojo.SessionBytes;

/**
 * ServerSession的生命周期，包含ServerSession从创建、交互、销毁各过程的触发事件
 *
 * @author liuyu
 * @date 2024/1/4
 */
public interface ServerSessionLifecycle {

    /**
     * ServerSession新建完成后触发
     *
     * @param serverSession ServerSession
     */
    default void created(ServerSession serverSession) {

    }

    /**
     * 发送字节给目标端口前触发
     *
     * @param serverSession ServerSession
     * @param bytes         发送的字节
     * @return 修改后的字节，若不需要修改直接返回bytes，返回null则表示忽略掉此字节
     */
    default byte[] beforeSendToTarget(ServerSession serverSession, byte[] bytes) {
        return bytes;
    }

    /**
     * 发送字节给目标端口后触发
     *
     * @param serverSession ServerSession
     * @param bytes         发送的字节
     */
    default void afterSendToTarget(ServerSession serverSession, byte[] bytes) {
    }

    /**
     * 发送字节给客户端缓冲区
     *
     * @param sessionBytes  发送的字节
     * @param callBack      回调，not null
     */
    default void sendToClientBuffer(SessionBytes sessionBytes, LoginClientService.Client client, SendAbleSessionBytes.CallBack callBack) {
        client.addBytes(sessionBytes, callBack);
    }

    /**
     * 发送字节给用户后触发
     *
     * @param serverSession ServerSession
     * @param bytes         发送的字节
     */
    default void afterSendToUser(ServerSession serverSession, byte[] bytes) {
    }


    /**
     * 关闭后触发
     *
     * @param serverSession ServerSession
     */
    default void closed(ServerSession serverSession) {

    }
}
