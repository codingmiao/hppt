package org.wowtools.hppt.run;

import org.wowtools.hppt.run.sc.RunSc;
import org.wowtools.hppt.run.ss.RunSs;

/**
 * @author liuyu
 * @date 2024/1/5
 */
public class Run {
    public static void main(String[] args)  throws Exception{
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
