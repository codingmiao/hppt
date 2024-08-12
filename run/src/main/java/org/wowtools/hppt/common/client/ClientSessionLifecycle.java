package org.wowtools.hppt.common.client;

/**
 * ClientSession的生命周期，包含ClientSession从创建、交互、销毁各过程的触发事件
 *
 * @author liuyu
 * @date 2024/1/4
 */
public interface ClientSessionLifecycle {

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
     * 发送字节给目标端口前触发
     *
     * @param clientSession ClientSession
     * @param bytes         发送的字节
     *                      return 修改后的字节，若不需要修改直接返回bytes，返回null则表示忽略掉此字节
     */
    default byte[] beforeSendToTarget(ClientSession clientSession, byte[] bytes) {
        return bytes;
    }

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
