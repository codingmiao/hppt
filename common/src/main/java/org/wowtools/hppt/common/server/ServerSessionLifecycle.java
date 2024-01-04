package org.wowtools.hppt.common.server;


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
     * 发送字节给用户的具体操作，例如通过http post发送
     *
     * @param serverSession ServerSession
     * @param bytes         发送的字节
     */
    void sendToUser(ServerSession serverSession, byte[] bytes);

    /**
     * 发送字节给用户后触发
     *
     * @param serverSession ServerSession
     * @param bytes         发送的字节
     */
    default void afterSendToUser(ServerSession serverSession, byte[] bytes) {
    }

    /**
     * 校验会话是否超时，一般需要发消息到客户端验证
     * @param serverSession ServerSession
     */
    default void checkActive(ServerSession serverSession) {
    }

    /**
     * 关闭后触发
     *
     * @param serverSession ServerSession
     */
    default void closed(ServerSession serverSession) {

    }
}
