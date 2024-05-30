package org.wowtools.hppt.run.ss.post;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 发消息servlet
 *
 * @author liuyu
 * @date 2024/5/30
 */
@Slf4j
final class SendServlet extends HttpServlet {
    private final PostServerSessionService postServerSessionService;

    public SendServlet(PostServerSessionService postServerSessionService) {
        this.postServerSessionService = postServerSessionService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Server", "");
        String cookie = req.getParameter("c");
        PostCtx ctx = postServerSessionService.ctxMap.computeIfAbsent(cookie, (c) -> new PostCtx(cookie));
        receive(ctx, req);
    }

    //读请求体里带过来的bytes并接收
    private void receive(PostCtx ctx, HttpServletRequest req) throws IOException {
        byte[] bytes;
        try (InputStream inputStream = req.getInputStream(); ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            bytes = byteArrayOutputStream.toByteArray();
        }
        log.debug("收到请求body {}", bytes.length);

        List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(bytes);
        for (byte[] sub : bytesList) {
            postServerSessionService.receiveClientBytes(ctx, sub);
        }
    }

}
