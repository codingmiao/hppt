package org.wowtools.hppt.run;

import io.netty.util.ResourceLeakDetector;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.run.sc.RunSc;
import org.wowtools.hppt.run.ss.RunSs;

/**
 * @author liuyu
 * @date 2024/1/5
 */
public class Run {

    public static void main(String[] args) throws Exception {
        try {
            if ("1".equals(ResourcesReader.readStr(Run.class, "/debug.txt").trim())) {
                ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
                System.out.println("开启调试");
            } else {
                System.out.println("不开启调试");
            }
        } catch (Exception e) {
            System.out.println("不开启调试");
        }
        String type = args[0];
        switch (type) {
            case "ss":
                RunSs.main(args);
                break;
            case "sc":
                RunSc.main(args);
                break;
            default:
                throw new IllegalStateException("Unexpected type: " + type);
        }
    }
}
