package org.wowtools.hppt.run.ss;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.ResourcesReader;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.file.FileServerSessionService;
import org.wowtools.hppt.run.ss.hppt.HpptServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.post.PostServerSessionService;
import org.wowtools.hppt.run.ss.rhppt.RHpptServerSessionService;
import org.wowtools.hppt.run.ss.rpost.RPostServerSessionService;
import org.wowtools.hppt.run.ss.websocket.WebsocketServerSessionService;

/**
 * @author liuyu
 * @date 2024/1/24
 */
@Slf4j
public class RunSs {

    public static void main(String[] args) throws Exception {

        String configPath;
        if (args.length <= 1) {
            configPath = "ss.yml";
        } else {
            configPath = args[1];
        }
        SsConfig config;
        try {
            config = Constant.ymlMapper.readValue(ResourcesReader.readStr(RunSs.class, configPath), SsConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件异常", e);
        }
        while (true) {
            ServerSessionService<?> sessionService;
            log.info("type {}", config.type);
            sessionService = switch (config.type) {
                case "post" -> new PostServerSessionService(config);
                case "websocket" -> new WebsocketServerSessionService(config);
                case "hppt" -> new HpptServerSessionService(config);
                case "rhppt" -> new RHpptServerSessionService(config);
                case "rpost" -> new RPostServerSessionService(config);
                case "file" -> new FileServerSessionService(config);
                default -> throw new IllegalStateException("Unexpected config.type: " + config.type);
            };
            sessionService.syncStart(config);

            log.warn("ServerSessionService exit {}", sessionService);
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e1) {
            }
        }

    }

}
