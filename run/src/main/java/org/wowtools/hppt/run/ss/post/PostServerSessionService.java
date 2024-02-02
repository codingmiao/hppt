package org.wowtools.hppt.run.ss.post;

import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionLifecycle;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.common.server.ServerSessionManagerBuilder;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

/**
 * @author liuyu
 * @date 2024/1/24
 */
@Slf4j
public class PostServerSessionService {

    private final ServerSessionManager serverSessionManager;
    private final LoginClientService loginClientService;


    public PostServerSessionService(SsConfig ssConfig) throws Exception {
        serverSessionManager = new ServerSessionManagerBuilder()
                .setLifecycle(buildServerSessionLifecycle(ssConfig))
                .build();
        loginClientService = new LoginClientService(ssConfig.clientIds);

        log.info("*********");
        Server server = new Server(ssConfig.port);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        context.addServlet(new ServletHolder(new TimeServlet()), "/time");
        context.addServlet(new ServletHolder(new LoginServlet(loginClientService)), "/login");
        context.addServlet(new ServletHolder(new TalkServlet(ssConfig, serverSessionManager, loginClientService)), "/talk");
        context.addServlet(new ServletHolder(new ErrorServlet()), "/err");

        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.setShowServlet(false);
        errorHandler.setShowStacks(false);
        errorHandler.addErrorPage(400, 599, "/err");
        errorHandler.setServer(new Server());

        context.setErrorHandler(errorHandler);

        log.info("服务端启动完成 端口 {}", ssConfig.port);
        server.start();
        server.join();
    }

    protected ServerSessionLifecycle buildServerSessionLifecycle(SsConfig ssConfig) {
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
