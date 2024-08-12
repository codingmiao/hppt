package org.wowtools.hppt.run.transmit.transport;

import jakarta.servlet.annotation.MultipartConfig;
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
import org.wowtools.hppt.common.util.JsonConfig;
import org.wowtools.hppt.run.ss.post.PostServerSessionService;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author liuyu
 * @date 2024/4/8
 */
public class PostServerTransport extends Transport {
    final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();

    public PostServerTransport(String receiveId, String sendId, JsonConfig.MapConfig config, Cb cb) {
        super(receiveId, sendId, config, cb);
        int port = config.value("port");
        HttpServerManager.bind(port, this);
        cb.end();
    }


    @Override
    public void send(byte[] bytes) {
        //发到缓冲池，等客户端发起请求取走
        sendQueue.add(bytes);
    }
}

class HttpServerManager {
    private static final Map<Integer, HttpServer> serverMap = new HashMap<>(1);

    public static synchronized void bind(int port, PostServerTransport postServerTransport) {
        HttpServer httpServer = serverMap.computeIfAbsent(port, p -> {
            try {
                return new HttpServer(p);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        httpServer.postServerTransportMap.put(postServerTransport.getSendId(), postServerTransport);
    }
}

@Slf4j
class HttpServer {
    protected final Map<String, PostServerTransport> postServerTransportMap = new HashMap<>();//<sendId,PostServerTransport>


    public HttpServer(int port) throws Exception {
        log.info("*********");
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(server, "/");
        context.addServlet(new ServletHolder(new SendServlet(this)), "/talk");
        context.addServlet(new ServletHolder(new ErrorServlet()), "/err");

        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.setShowServlet(false);
        errorHandler.setShowStacks(false);
        errorHandler.addErrorPage(400, 599, "/err");
        errorHandler.setServer(new Server());

        context.setErrorHandler(errorHandler);
        context.addFilter(DisableTraceFilter.class, "/*", null);

        server.start();
//        server.join();
        log.info("HttpServer启动完成 端口 {}", port);

    }
}

@Slf4j
class SendServlet extends HttpServlet {


    private final HttpServer httpServer;

    public SendServlet(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Server", "");
        String c = req.getParameter("c");//TODO 解密
        PostServerTransport postServerTransport = httpServer.postServerTransportMap.get(c);
        if (null == postServerTransport) {
            return;
        }
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
                postServerTransport.receive(sub);
            }
        }

        //取缓冲区中的数据返回
        {
            byte[] rBytes = postServerTransport.sendQueue.poll();
            if (null != rBytes) {
                List<byte[]> bytesList = new LinkedList<>();
                bytesList.add(rBytes);
                postServerTransport.sendQueue.drainTo(bytesList);
                rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                log.debug("向客户端发送字节 {}", rBytes.length);
                try (OutputStream os = resp.getOutputStream()) {
                    os.write(rBytes);
                }
            }
        }

    }
}

@MultipartConfig
class ErrorServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws IOException {
        try (PrintWriter write = response.getWriter()) {
            write.write("error " + response.getStatus());
            response.setHeader("Server", "hppt");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doPost(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doPost(req, resp);
    }
}
