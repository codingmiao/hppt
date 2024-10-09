package org.wowtools.hppt.run.sc.common;


import org.wowtools.hppt.common.client.ClientSession;

/**
 * @author liuyu
 * @date 2024/9/27
 */
public sealed interface Receiver permits PortReceiver, SsReceiver {
    void receiveServerBytes(byte[] bytes) throws Exception;

    void closeClientSession(ClientSession clientSession);

    void exit();

    boolean notUsed();
}
