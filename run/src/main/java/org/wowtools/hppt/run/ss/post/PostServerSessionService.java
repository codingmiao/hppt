package org.wowtools.hppt.run.ss.post;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wowtools.common.utils.LruCache;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.io.IOException;
import java.util.Map;

/**
 * @author liuyu
 * @date 2024/1/24
 */
@Slf4j
public class PostServerSessionService extends ServerSessionService<PostCtx> {

    private Server server;

    public PostServerSessionService(SsConfig ssConfig) throws Exception {
        super(ssConfig);

    }

    @Override
    public void init(SsConfig ssConfig) throws Exception {
        log.info("*********");
        server = new Server(ssConfig.port);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        context.addServlet(new ServletHolder(new SendServlet(this)), "/s");
        context.addServlet(new ServletHolder(new ReplyServlet(this, ssConfig.post.waitResponseTime)), "/r");
        context.addServlet(new ServletHolder(new ErrorServlet()), "/e");

        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.setShowServlet(false);
        errorHandler.setShowStacks(false);
        errorHandler.addErrorPage(400, 599, "/e");
        errorHandler.setServer(new Server());

        context.setErrorHandler(errorHandler);
        context.addFilter(DisableTraceFilter.class, "/*", null);

        log.info("服务端启动完成 端口 {}", ssConfig.port);
        server.start();
        server.join();
    }

    protected final Map<String, PostCtx> ctxMap = LruCache.buildCache(128, 8);

    @Override
    protected void sendBytesToClient(PostCtx ctx, byte[] bytes) {
        ctx.sendQueue.add(bytes);
    }

    @Override
    protected void closeCtx(PostCtx ctx) {
        ctxMap.remove(ctx.cookie);
    }

    @Override
    public void doClose() throws Exception {
        server.stop();
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
