package org.wowtools.hppt.run.ss.post;

import org.wowtools.hppt.common.util.BufferPool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author liuyu
 * @date 2024/3/20
 */
public class PostCtx {
    final String cookie;
    final BufferPool<byte[]> sendQueue = new BufferPool<>(">PostCtx-sendQueue");

    public PostCtx(String cookie) {
        this.cookie = cookie;
    }

}
