package org.wowtools.hppt.common.client;

import io.netty.channel.EventLoopGroup;
import org.wowtools.hppt.common.util.NettyChannelTypeChecker;

/**
 * @author liuyu
 * @date 2024/1/4
 */
public class ClientSessionManagerBuilder {
    protected int bufferSize;
    protected EventLoopGroup bossGroup;
    protected EventLoopGroup workerGroup;

    protected ClientSessionLifecycle lifecycle;
    protected ClientBytesSender clientBytesSender;

    public ClientSessionManagerBuilder setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        return this;
    }

    public ClientSessionManagerBuilder setBossGroup(EventLoopGroup bossGroup) {
        this.bossGroup = bossGroup;
        return this;
    }

    public ClientSessionManagerBuilder setWorkerGroup(EventLoopGroup workerGroup) {
        this.workerGroup = workerGroup;
        return this;
    }

    public ClientSessionManagerBuilder setLifecycle(ClientSessionLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        return this;
    }

    public ClientSessionManagerBuilder setClientBytesSender(ClientBytesSender clientBytesSender) {
        this.clientBytesSender = clientBytesSender;
        return this;
    }

    public ClientSessionManager build() {
        if (bufferSize <= 0) {
            bufferSize = 10240;
        }
        if (bossGroup == null) {
            bossGroup = NettyChannelTypeChecker.buildVirtualThreadEventLoopGroup();
        }
        if (workerGroup == null) {
            workerGroup = NettyChannelTypeChecker.buildVirtualThreadEventLoopGroup();
        }
        if (lifecycle == null) {
            throw new RuntimeException("lifecycle不能为空");
        }

        return new ClientSessionManager(this);
    }


}
