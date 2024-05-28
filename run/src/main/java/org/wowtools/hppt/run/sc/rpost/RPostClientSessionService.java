package org.wowtools.hppt.run.sc.rpost;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.ss.post.ErrorServlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2024/4/16
 */
@Slf4j
public class RPostClientSessionService extends ClientSessionService {

    private static final byte[] empty = new byte[0];

    private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
    private Server server;

    public RPostClientSessionService(ScConfig config) throws Exception {
        super(config);
    }

    @Override
    protected void connectToServer(ScConfig config, Cb cb) throws Exception {
        log.info("*********");
        server = new Server(config.rpost.port);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        context.addServlet(new ServletHolder(new TalkServlet()), "/t");
        context.addServlet(new ServletHolder(new InterruptServlet()), "/i");
        context.addServlet(new ServletHolder(new ErrorServlet()), "/err");

        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.setShowServlet(false);
        errorHandler.setShowStacks(false);
        errorHandler.addErrorPage(400, 599, "/err");
        errorHandler.setServer(new Server());

        context.setErrorHandler(errorHandler);
        context.addFilter(DisableTraceFilter.class, "/*", null);

        log.info("服务端启动完成 端口 {}", config.rpost.port);
        server.start();
        cb.end();
    }

    @Override
    protected void sendBytesToServer(byte[] bytes) {
        sendQueue.add(bytes);
    }

    @Override
    protected void doClose() throws Exception {
        server.stop();
    }

    private final class InterruptServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            sendQueue.add(empty);
            log.debug("收到打断等待请求");
        }
    }

    private final class TalkServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            try {
                r(req, resp);
            } catch (Exception e) {
                log.error("doPost err", e);
                exit();
            }
        }

        private void r(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            resp.setHeader("Server", "");
            //读请求体里带过来的bytes并接收
            boolean reqEmpty;
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
                reqEmpty = bytesList.isEmpty();
                for (byte[] sub : bytesList) {
                    try {
                        receiveServerBytes(sub);
                    } catch (Exception e) {
                        log.warn("接收字节异常", e);
                        exit();
                    }
                }
            }

            //取缓冲区中的数据返回
            {
                byte[] rBytes;
                if (reqEmpty) {
                    //请求体非空的话立即返回，否则等待用户侧输入
                    try {
                        rBytes = sendQueue.poll(config.rpost.waitBytesTime, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        rBytes = null;
                    }
                } else {
                    rBytes = sendQueue.poll();
                }

                if (null != rBytes && empty != rBytes) {
                    List<byte[]> bytesList = new LinkedList<>();
                    bytesList.add(rBytes);
                    sendQueue.drainTo(bytesList);
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
