package org.wowtools.hppt.run.ss.post;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wowtools.common.utils.LruCache;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author liuyu
 * @date 2024/1/24
 */
@Slf4j
public class PostServerSessionService extends ServerSessionService<PostCtx> {


    public PostServerSessionService(SsConfig ssConfig) throws Exception {
        super(ssConfig);

        log.info("*********");
        Server server = new Server(ssConfig.port);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        context.addServlet(new ServletHolder(new MyServlet()), "/talk");
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

    private final Map<String, PostCtx> ctxMap = LruCache.buildCache(128, 8);

    @Override
    protected void sendBytesToClient(PostCtx ctx, byte[] bytes) {
        ctx.sendQueue.add(bytes);
    }

    @Override
    protected void closeCtx(PostCtx ctx) {
        ctxMap.remove(ctx.cookie);
    }

    private final class MyServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setHeader("Server", "");
            String cookie = req.getParameter("c");
            PostCtx ctx = ctxMap.computeIfAbsent(cookie, (c) -> new PostCtx(cookie));
            //读请求体里带过来的bytes并接收
            {
                byte[] bytes;
                try (InputStream inputStream = req.getInputStream(); ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead);
                    }
                    bytes = byteArrayOutputStream.toByteArray();
                }

                List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(bytes);
                for (byte[] sub : bytesList) {
                    receiveClientBytes(ctx, sub);
                }
            }

            //取缓冲区中的数据返回
            {
                byte[] rBytes = ctx.sendQueue.poll();
                if (null != rBytes) {
                    List<byte[]> bytesList = new LinkedList<>();
                    bytesList.add(rBytes);
                    ctx.sendQueue.drainTo(bytesList);
                    rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                    log.debug("向客户端发送字节 {}", rBytes.length);
                    try (OutputStream os = resp.getOutputStream()) {
                        os.write(rBytes);
                    }
                }
            }

        }
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
