package org.wowtools.hppt.run.ss.post;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.util.SsUtil;

import java.io.IOException;

/**
 * @author liuyu
 * @date 2024/1/24
 */
@Slf4j
public class PostServerSessionService {

    private final ServerSessionManager serverSessionManager;
    private final LoginClientService loginClientService;


    public PostServerSessionService(SsConfig ssConfig) throws Exception {
        serverSessionManager = SsUtil.createServerSessionManagerBuilder(ssConfig).build();
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
        context.addFilter(DisableTraceFilter.class, "/*", null);

        log.info("服务端启动完成 端口 {}", ssConfig.port);
        server.start();
        server.join();
    }


    //禁用TRACE、TRACK
    public static final class DisableTraceFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String m = httpRequest.getMethod();
            if ("TRACE".equalsIgnoreCase(m) || "TRACK".equalsIgnoreCase(m)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            chain.doFilter(request, response);
        }
    }

}
