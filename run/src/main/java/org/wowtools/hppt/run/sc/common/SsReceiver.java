package org.wowtools.hppt.run.sc.common;

import org.wowtools.hppt.common.client.ClientSession;
import org.wowtools.hppt.common.util.RoughTimeUtil;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author liuyu
 * @date 2024/9/27
 */
public final class SsReceiver implements Receiver {
    public final BlockingQueue<byte[]> serverBytesQueue = new LinkedBlockingQueue<>();

    private long lastUsedTime = RoughTimeUtil.getTimestamp();

    public SsReceiver(ScConfig config, ClientSessionService clientSessionService) throws Exception {
    }

    @Override
    public void receiveServerBytes(byte[] bytes) throws Exception {
        lastUsedTime = RoughTimeUtil.getTimestamp();
        serverBytesQueue.add(bytes);
    }

    @Override
    public void closeClientSession(ClientSession clientSession) {

    }

    @Override
    public void exit() {

    }

    @Override
    public boolean notUsed() {
        return RoughTimeUtil.getTimestamp() - lastUsedTime > 60_000L;
    }
}
