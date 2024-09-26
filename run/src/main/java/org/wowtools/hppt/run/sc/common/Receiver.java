package org.wowtools.hppt.run.sc.common;


/**
 * @author liuyu
 * @date 2024/9/27
 */
sealed interface Receiver permits PortReceiver, SsReceiver {
    void receiveServerBytes(byte[] bytes) throws Exception;
    void exit();
    boolean notUsed();
}
