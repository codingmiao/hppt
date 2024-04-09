package org.wowtools.hppt.run.ss;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.config.Configurator;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.run.ss.hppt.HpptServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.post.PostServerSessionService;
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
        log.info("type {}",config.type);
        switch (config.type) {
            case "post":
                new PostServerSessionService(config);
                break;
            case "websocket":
                new WebsocketServerSessionService(config);
                break;
            case "hppt":
                new HpptServerSessionService(config);
                break;
            default:
                throw new IllegalStateException("Unexpected config.type: " + config.type);
        }
    }
}
