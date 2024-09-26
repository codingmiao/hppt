package org.wowtools.hppt.run.ss.common;

/**
 * @author liuyu
 * @date 2024/9/26
 */
sealed interface Receiver<CTX> permits PortReceiver, SsReceiver {
    void receiveClientBytes(CTX ctx, byte[] bytes);

    void removeCtx(CTX ctx);

    void exit();
}
