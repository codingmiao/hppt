package org.wowtools.hppt.ss;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.ss.pojo.SsConfig;
import org.wowtools.hppt.ss.servlet.*;

import java.io.File;

/**
 * @author liuyu
 * @date 2023/11/25
 */
@Slf4j
public class StartSs {
    public static final SsConfig config;

    static {
        Configurator.reconfigure(new File(ResourcesReader.getRootPath(StartSs.class) + "/log4j2.xml").toURI());

        try {
            config = Constant.ymlMapper.readValue(ResourcesReader.readStr(StartSs.class, "ss.yml"), SsConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件异常", e);
        }
    }

    public static void main(String[] args) throws Exception {
        log.info("*********");
        Server server = new Server(config.port);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        context.addServlet(new ServletHolder(new TimeServlet()), "/time");
        context.addServlet(new ServletHolder(new LoginServlet()), "/login");
        context.addServlet(new ServletHolder(new InitServlet()), "/init");
        context.addServlet(new ServletHolder(new TalkServlet()), "/talk");
//        context.addServlet(new ServletHolder(new UpFileServlet()), "/up");
//        context.addServlet(new ServletHolder(new DownFileServlet()), "/down");
        context.addServlet(new ServletHolder(new ErrorServlet()), "/err");

        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.setShowServlet(false);
        errorHandler.setShowStacks(false);
        errorHandler.addErrorPage(400, 599, "/err");
        errorHandler.setServer(new Server());

        context.setErrorHandler(errorHandler);

        log.info("服务端启动完成 端口 {}", config.port);
        server.start();
        server.join();
    }
}
