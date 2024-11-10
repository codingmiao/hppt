package org.wowtools.hppt.run.sc;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.ResourcesReader;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

/**
 * @author liuyu
 * @date 2024/1/30
 */
@Slf4j
public class RunSc {

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
            try (ClientSessionService clientSessionService = ClientSessionServiceBuilder.build(config)){
                clientSessionService.sync();
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
