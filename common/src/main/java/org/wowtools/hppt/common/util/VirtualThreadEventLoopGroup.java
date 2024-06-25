package org.wowtools.hppt.common.util;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;

/**
 * @author liuyu
 * @date 2024/6/25
 */
@Slf4j
public class VirtualThreadEventLoopGroup {

    public static EventLoopGroup build(int nThread) {
        EventLoopGroup eventLoopGroup;

        if (Epoll.isAvailable()) {
            log.info("use EpollEventLoopGroup");
            eventLoopGroup = new EpollEventLoopGroup(nThread, new VirtualThreadFactory());
        } else {
            log.info("use NioEventLoopGroup");
            eventLoopGroup = new NioEventLoopGroup(nThread, new VirtualThreadFactory());
        }

        return eventLoopGroup;
    }

    public static EventLoopGroup build() {
        EventLoopGroup eventLoopGroup;

        if (Epoll.isAvailable()) {
            // 使用 EpollEventLoopGroup
            log.info("使用EpollEventLoopGroup");
            eventLoopGroup = new EpollEventLoopGroup(new VirtualThreadFactory());
        } else {
            // 使用 NioEventLoopGroup
            log.info("使用NioEventLoopGroup");
            eventLoopGroup = new NioEventLoopGroup(new VirtualThreadFactory());
        }

        return eventLoopGroup;
    }

    private static final class VirtualThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            return Thread.ofVirtual().unstarted(r);
        }
    }
}
