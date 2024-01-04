package org.wowtools.hppt.common.server;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.wowtools.hppt.common.client.ClientSessionLifecycle;
import org.wowtools.hppt.common.client.ClientSessionManager;

/**
 * @author liuyu
 * @date 2024/1/4
 */
public class ServerSessionManagerBuilder {
    protected EventLoopGroup group;

    protected ServerSessionLifecycle lifecycle;
    protected long sessionTimeout = 30000;

    public ServerSessionManagerBuilder setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    public ServerSessionManagerBuilder setLifecycle(ServerSessionLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        return this;
    }

    public ServerSessionManager build() {
        if (group == null) {
            group = new NioEventLoopGroup();
        }

        if (lifecycle == null) {
            throw new RuntimeException("lifecycle不能为空");
        }

        return new ServerSessionManager(this);
    }


}
