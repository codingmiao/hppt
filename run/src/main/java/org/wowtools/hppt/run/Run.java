package org.wowtools.hppt.run;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.LoggerFactory;
import org.wowtools.hppt.common.util.ResourcesReader;
import org.wowtools.hppt.run.sc.RunSc;
import org.wowtools.hppt.run.ss.RunSs;

import java.io.File;

/**
 * @author liuyu
 * @date 2024/1/5
 */
public class Run {

    public static void main(String[] args) throws Exception {
        System.setProperty("logFileName", args.length > 0 ? String.join("-", args).replace(".", "_") : "hppt");
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            File externalConfigFile = new File(ResourcesReader.getRootPath(Run.class) + "/logback.xml");
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(externalConfigFile);
            StatusPrinter.printInCaseOfErrorsOrWarnings(context);
        } catch (Exception e) {
            System.out.println("未加载到根目录下logback.xml文件，使用默认配置 " + e.getMessage());
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
