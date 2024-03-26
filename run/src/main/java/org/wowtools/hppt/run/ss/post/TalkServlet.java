//package org.wowtools.hppt.run.ss.post;
//
//import jakarta.servlet.http.HttpServlet;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.extern.slf4j.Slf4j;
//import org.wowtools.hppt.common.server.LoginClientService;
//import org.wowtools.hppt.common.server.ServerSessionManager;
//import org.wowtools.hppt.common.server.ServerTalker;
//import org.wowtools.hppt.run.ss.pojo.SsConfig;
//
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//
///**
// * @author liuyu
// * @date 2024/1/24
// */
//@Slf4j
//public class TalkServlet extends HttpServlet {
//    private final SsConfig ssConfig;
//    private final ServerSessionManager serverSessionManager;
//    private final LoginClientService loginClientService;
//
//    public TalkServlet(SsConfig ssConfig, ServerSessionManager serverSessionManager, LoginClientService loginClientService) {
//        this.ssConfig = ssConfig;
//        this.serverSessionManager = serverSessionManager;
//        this.loginClientService = loginClientService;
//    }
//
//    @Override
//    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
//        String loginCode = req.getParameter("c");
//        loginCode = loginCode.replace(" ", "+");
//        LoginClientService.Client client = loginClientService.getClient(loginCode);
//        if (null == client) {
//            throw new RuntimeException("not login: " + loginCode);
//        }
//
//        //读请求体里带过来的bytes
//        byte[] bytes;
//        try (InputStream inputStream = req.getInputStream(); ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
//            byte[] buffer = new byte[1024];
//            int bytesRead;
//            while ((bytesRead = inputStream.read(buffer)) != -1) {
//                byteArrayOutputStream.write(buffer, 0, bytesRead);
//            }
//            bytes = byteArrayOutputStream.toByteArray();
//        }
//
//        //接消息
//        try {
//            ServerTalker.receiveClientBytes(ssConfig, serverSessionManager, client, bytes);
//        } catch (Exception e) {
//            log.warn("接收客户端消息异常", e);
//            throw new RuntimeException(e);
//        }
//
//
//        //取消息
//        byte[] rBytes;
//        try {
//            rBytes = ServerTalker.replyToClient(ssConfig, serverSessionManager, client, ssConfig.websocket.maxReturnBodySize, false);
//        } catch (Exception e) {
//            log.warn("取消息异常", e);
//            throw new RuntimeException(e);
//        }
//
//
//        resp.setHeader("Server", "");
//        if (null != rBytes) {
//            log.debug("返回客户端字节数 {}", rBytes.length);
//            try (OutputStream os = resp.getOutputStream()) {
//                os.write(rBytes);
//            }
//        }
//    }
//}
