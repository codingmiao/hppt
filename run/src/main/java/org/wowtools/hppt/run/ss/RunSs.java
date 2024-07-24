package org.wowtools.hppt.run.ss;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.config.Configurator;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.file.FileServerSessionService;
import org.wowtools.hppt.run.ss.hppt.HpptServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.post.PostServerSessionService;
import org.wowtools.hppt.run.ss.rhppt.RHpptServerSessionService;
import org.wowtools.hppt.run.ss.rpost.RPostServerSessionService;
import org.wowtools.hppt.run.ss.websocket.WebsocketServerSessionService;

import java.io.File;

/**
 * @author liuyu
 * @date 2024/1/24
 */
@Slf4j
public class RunSs {


    static {
        Configurator.reconfigure(new File(ResourcesReader.getRootPath(RunSs.class) + "/log4j2.xml").toURI());
    }


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
            ServerSessionService<?> sessionService = null;
            try {
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
                final ServerSessionService<?> fs = sessionService;
                Thread.startVirtualThread(() -> {
                    try {
                        fs.init(config);
                    } catch (Exception e) {
                        onErr(e, fs);
                    }
                });
                log.info("ServerSessionService init success {}", sessionService);

                sessionService.sync();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                }
            } catch (Exception e) {
                onErr(e, sessionService);
            }
        }

    }

    private static void onErr(Exception e, ServerSessionService<?> sessionService) {
        log.info("----------------------销毁当前Service,10秒后重启", e);
        try {
            sessionService.exit();
        } catch (Exception ex) {
            log.warn("ServerSessionService exit err", ex);
        }
    }
}
