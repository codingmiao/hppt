package org.wowtools.hppt.run.ss.util;

import io.netty.util.internal.StringUtil;
import org.wowtools.hppt.common.server.ServerSessionLifecycle;
import org.wowtools.hppt.common.server.ServerSessionManagerBuilder;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

/**
 * @author liuyu
 * @date 2024/3/12
 */
public class SsUtil {

    public static ServerSessionManagerBuilder createServerSessionManagerBuilder(SsConfig ssConfig) {
        return new ServerSessionManagerBuilder()
                .setLifecycle(buildServerSessionLifecycle(ssConfig));
    }

    private static ServerSessionLifecycle buildServerSessionLifecycle(SsConfig ssConfig) {
        if (StringUtil.isNullOrEmpty(ssConfig.lifecycle)) {
            return new ServerSessionLifecycle() {
            };
        } else {
            try {
                Class<? extends ServerSessionLifecycle> clazz = (Class<? extends ServerSessionLifecycle>) Class.forName(ssConfig.lifecycle);
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }
}
