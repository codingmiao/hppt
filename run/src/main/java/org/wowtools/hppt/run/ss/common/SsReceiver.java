package org.wowtools.hppt.run.ss.common;

/**
 * 中继模式下，数据被发送到另一个ss
 * @author liuyu
 * @date 2024/9/26
 */
final class SsReceiver<CTX> implements Receiver<CTX> {
    @Override
    public void receiveClientBytes(CTX ctx, byte[] bytes) {

    }

    @Override
    public void removeCtx(CTX ctx) {

    }

    @Override
    public void exit() {

    }
}
