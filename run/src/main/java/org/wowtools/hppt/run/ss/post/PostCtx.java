package org.wowtools.hppt.run.ss.post;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author liuyu
 * @date 2024/3/20
 */
class PostCtx {
    final String cookie;
    final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();

    public PostCtx(String cookie) {
        this.cookie = cookie;
    }

    Thread waitResponseThread;

}
