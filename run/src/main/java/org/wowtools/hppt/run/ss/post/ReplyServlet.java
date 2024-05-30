package org.wowtools.hppt.run.ss.post;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 消息交互servlet
 *
 * @author liuyu
 * @date 2024/5/30
 */
@Slf4j
final class ReplyServlet extends HttpServlet {
    private static final byte[] emptyBytes = new byte[0];
    private final PostServerSessionService postServerSessionService;
    private final long waitResponseTime;

    public ReplyServlet(PostServerSessionService postServerSessionService, long waitResponseTime) {
        this.postServerSessionService = postServerSessionService;
        this.waitResponseTime = waitResponseTime;
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Server", "");
        String cookie = req.getParameter("c");
        PostCtx ctx = postServerSessionService.ctxMap.get(cookie);
        if (null != ctx) {
            write(ctx, resp);
        }
    }

    //取缓冲区中的数据返回
    private void write(PostCtx ctx, HttpServletResponse resp) throws IOException {
        List<byte[]> bytesList = new LinkedList<>();
        byte[] rBytes;
        try {
            rBytes = ctx.sendQueue.poll(waitResponseTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            rBytes = null;
        }
        if (null != rBytes) {
            bytesList.add(rBytes);
            ctx.sendQueue.drainTo(bytesList);
        }
        rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
        log.debug("向客户端发送字节 body {}", rBytes.length);
        try (OutputStream os = resp.getOutputStream()) {
            os.write(rBytes);
        }
    }
}
