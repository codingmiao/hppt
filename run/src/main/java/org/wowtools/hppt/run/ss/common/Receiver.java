package org.wowtools.hppt.run.ss.common;

import lombok.Getter;
import lombok.Setter;

/**
 * @author liuyu
 * @date 2024/9/26
 */
@Getter
@Setter
sealed abstract class Receiver<CTX> permits PortReceiver, SsReceiver {
    public abstract void receiveClientBytes(CTX ctx, byte[] bytes) throws Exception;

    public abstract void removeCtx(CTX ctx);

    public abstract void exit();

    public abstract long getLastHeartbeatTime();
}
