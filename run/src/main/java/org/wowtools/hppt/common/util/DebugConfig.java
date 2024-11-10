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

    //是否开启缓冲池监控
    public static final boolean OpenBufferPoolDetector;

    //缓冲池高水位线，缓冲池中元素个数超过此值且继续向其中添加要素则触发日志
    public static final int BufferPoolWaterline;

    static {
        ResourceLeakDetector.Level _NettyResourceLeakDetectorLevel = ResourceLeakDetector.Level.DISABLED;
        boolean _OpenSerialNumber = false;
        boolean _OpenBufferPoolDetector = false;
        int _BufferPoolWaterline = 1000;
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

            _OpenBufferPoolDetector = "1".equals(configs.get("OpenBufferPoolDetector"));
            _BufferPoolWaterline = Integer.parseInt(configs.getOrDefault("BufferPoolWaterline", "1000"));

        } catch (Exception e) {
            log.debug("不开启调试模式 ", e);
        }

        NettyResourceLeakDetectorLevel = _NettyResourceLeakDetectorLevel;
        OpenSerialNumber = _OpenSerialNumber;

        OpenBufferPoolDetector = _OpenBufferPoolDetector;
        BufferPoolWaterline = _BufferPoolWaterline;
    }
}
