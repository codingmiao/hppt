package org.wowtools.hppt.run.sc;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.file.FileClientSessionService;
import org.wowtools.hppt.run.sc.hppt.HpptClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.sc.post.PostClientSessionService;
import org.wowtools.hppt.run.sc.rhppt.RHpptClientSessionService;
import org.wowtools.hppt.run.sc.rpost.RPostClientSessionService;
import org.wowtools.hppt.run.sc.websocket.WebSocketClientSessionService;

/**
 * @author liuyu
 * @date 2024/10/8
 */
@Slf4j
public class ClientSessionServiceBuilder {

    public static ClientSessionService build(ScConfig config) throws Exception {
        log.info("type {}", config.type);
        return switch (config.type) {
            case "post" -> new PostClientSessionService(config);
            case "websocket" -> new WebSocketClientSessionService(config);
            case "hppt" -> new HpptClientSessionService(config);
            case "rhppt" -> new RHpptClientSessionService(config);
            case "rpost" -> new RPostClientSessionService(config);
            case "file" -> new FileClientSessionService(config);
            default -> throw new IllegalStateException("Unexpected config.type: " + config.type);
        };
    }
}
