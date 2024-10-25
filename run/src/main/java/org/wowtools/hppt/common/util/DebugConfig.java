package org.wowtools.hppt.common.util;

import io.netty.util.ResourceLeakDetector;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.run.Run;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试配置信息
 *
 * @author liuyu
 * @date 2024/10/23
 */
@Slf4j
public class DebugConfig {
    //netty内存泄露检查级别
    public static final ResourceLeakDetector.Level NettyResourceLeakDetectorLevel;
    //是否开启消息流水号
    public static final boolean OpenSerialNumber;

    static {
        ResourceLeakDetector.Level _NettyResourceLeakDetectorLevel = ResourceLeakDetector.Level.DISABLED;
        boolean _OpenSerialNumber = false;
        try {
            String str = ResourcesReader.readStr(Run.class, "debug.ini");
            Map<String, String> configs = new HashMap<>();
            for (String line : str.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                log.debug(line);
                String[] kv = line.split("=", 2);
                if (kv.length == 2) {
                    configs.put(kv[0].trim(), kv[1].trim());
                }
            }

            _NettyResourceLeakDetectorLevel = switch (configs.get("NettyResourceLeakDetectorLevel")) {
                case "0" -> ResourceLeakDetector.Level.DISABLED;
                case "1" -> ResourceLeakDetector.Level.SIMPLE;
                case "2" -> ResourceLeakDetector.Level.ADVANCED;
                case "3" -> ResourceLeakDetector.Level.PARANOID;
                default -> ResourceLeakDetector.Level.DISABLED;
            };

            _OpenSerialNumber = "1".equals(configs.get("OpenSerialNumber"));

        } catch (Exception e) {
            log.debug("不开启调试模式，原因 {}", e.getMessage());
        }

        NettyResourceLeakDetectorLevel = _NettyResourceLeakDetectorLevel;
        OpenSerialNumber = _OpenSerialNumber;
    }
}
