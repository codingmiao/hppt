package org.wowtools.hppt.run.sc.util;

import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.client.ClientBytesSender;
import org.wowtools.hppt.common.client.ClientSessionLifecycle;
import org.wowtools.hppt.common.client.ClientSessionManager;
import org.wowtools.hppt.common.client.ClientSessionManagerBuilder;
import org.wowtools.hppt.common.util.NettyObjectBuilder;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

/**
 * @author liuyu
 * @date 2024/3/12
 */
@Slf4j
public class ScUtil {

    //建立ClientSessionManager，并绑上配置的端口
    public static ClientSessionManager createClientSessionManager(ScConfig config, ClientSessionLifecycle lifecycle, ClientBytesSender clientBytesSender) {

        ClientSessionManager clientSessionManager = new ClientSessionManagerBuilder()
                .setBufferSize(config.maxSendBodySize * 2)
                .setLifecycle(lifecycle)
                .setWorkerGroup(NettyObjectBuilder.buildEventLoopGroup(config.workerGroupNum))
                .setClientBytesSender(clientBytesSender)
                .build();
        if (null != config.forwards) {
            for (ScConfig.Forward forward : config.forwards) {
                String localHost = forward.localHost;
                if (StringUtil.isNullOrEmpty(localHost)) {
                    localHost = config.localHost;
                }
                if (StringUtil.isNullOrEmpty(localHost)) {
                    localHost = null;
                }

                boolean res = clientSessionManager.bindPort(localHost, forward.localPort);
                log.info("bind port {} {} -> {}:{}", res ? "success" : "fail",
                        forward.localPort, forward.remoteHost, forward.remotePort);
            }
        }
        return clientSessionManager;
    }
}
