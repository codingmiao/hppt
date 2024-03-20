package org.wowtools.hppt.run.sc;

import org.apache.logging.log4j.core.config.Configurator;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.sc.post.PostClientSessionService;
import org.wowtools.hppt.run.sc.websocket.WebSocketClientSessionService;
import org.wowtools.hppt.run.ss.websocket.WebsocketServerSessionService;

import java.io.File;

/**
 * @author liuyu
 * @date 2024/1/30
 */
public class RunSc {
    static {
        Configurator.reconfigure(new File(ResourcesReader.getRootPath(RunSc.class) + "/log4j2.xml").toURI());
    }

    public static void main(String[] args) throws Exception {
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

        switch (config.type) {
            case "post":
                new PostClientSessionService(config);
                break;
            case "websocket":
                new WebSocketClientSessionService(config);
                break;
            case "hppt":
                //TODO
                break;
            default:
                throw new IllegalStateException("Unexpected config.type: " + config.type);
        }
        System.out.println("-------------exit");
    }
}
