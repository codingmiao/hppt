package org.wowtools.hppt.run.sc.rpost;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.DisableTraceFilter;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.ss.post.ErrorServlet;

import java.io.ByteArrayOutputStream;
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
        context.addServlet(new ServletHolder(new SendServlet()), "/s");
        context.addServlet(new ServletHolder(new ReceiveServlet()), "/r");
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

    private final class SendServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            try {
                s(req, resp);
            } catch (Exception e) {
                log.error("SendServlet err", e);
//                exit();
            }
        }

        private void s(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            if (config.rpost.replyDelayTime > 0) {
                Thread.sleep(config.rpost.replyDelayTime);
            }
            resp.setHeader("Server", "");
            byte[] rBytes;
            try {
                rBytes = sendQueue.poll(config.rpost.waitResponseTime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                rBytes = null;
            }
            if (null != rBytes) {
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

    private final class ReceiveServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            try {
                r(req, resp);
            } catch (Exception e) {
                log.error("ReceiveServlet err", e);
//                exit();
            }
        }

        private void r(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            resp.setHeader("Server", "");
            byte[] bytes;
            try (InputStream inputStream = req.getInputStream(); ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                bytes = byteArrayOutputStream.toByteArray();
            }
            if (null == bytes) {
                return;
            }

            List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(bytes);
            for (byte[] sub : bytesList) {
                try {
                    receiveServerBytes(sub);
                } catch (Exception e) {
                    log.warn("接收字节异常", e);
                    exit();
                }
            }
        }
    }

}
