package org.wowtools.hppt.common.util;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 粗粒度的时间戳获取工具，由于系统频繁地System.currentTimeMillis()，做一个定时器统一获取时间减少性能开销
 * 5秒更新一次时间
 *
 * @author liuyu
 * @date 2023/3/7
 */
public class RoughTimeUtil {
    private static volatile long timestamp = System.currentTimeMillis();

    static {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                timestamp = System.currentTimeMillis();
            }
        };
        timer.schedule(task, 0, 5000);
    }

    public static long getTimestamp() {
        return timestamp;
    }
}
