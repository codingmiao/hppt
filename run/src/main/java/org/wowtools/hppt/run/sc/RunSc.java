package org.wowtools.hppt.run.sc;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.config.Configurator;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.run.sc.file.FileClientSessionService;
import org.wowtools.hppt.run.sc.hppt.HpptClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.sc.post.PostClientSessionService;
import org.wowtools.hppt.run.sc.rhppt.RHpptClientSessionService;
import org.wowtools.hppt.run.sc.rpost.RPostClientSessionService;
import org.wowtools.hppt.run.sc.websocket.WebSocketClientSessionService;

import java.io.File;

/**
 * @author liuyu
 * @date 2024/1/30
 */
@Slf4j
public class RunSc {
    static {
        Configurator.reconfigure(new File(ResourcesReader.getRootPath(RunSc.class) + "/log4j2.xml").toURI());
    }

    public static void main(String[] args) {
        String configPath;
        if (args.length <= 1) {
            configPath = "sc.yml";
        } else {
            configPath = args[1];
        }
        ScConfig config;
        try {
            config = Constant.ymlMapper.readValue(ResourcesReader.readStr(RunSc.class, configPath), ScConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件异常", e);
        }
        while (true) {
            log.info("type {}", config.type);
            try {
                switch (config.type) {
                    case "post":
                        new PostClientSessionService(config).sync();
                        break;
                    case "websocket":
                        new WebSocketClientSessionService(config).sync();
                        break;
                    case "hppt":
                        new HpptClientSessionService(config).sync();
                        break;
                    case "rhppt":
                        new RHpptClientSessionService(config).sync();
                        break;
                    case "rpost":
                        new RPostClientSessionService(config).sync();
                        break;
                    case "file":
                        new FileClientSessionService(config).sync();
                        break;
                    default:
                        throw new IllegalStateException("Unexpected config.type: " + config.type);
                }
            } catch (Exception e) {
                log.warn("服务异常", e);
            }
            log.warn("----------------------销毁当前Service,10秒后重启");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }
        }
    }
}
