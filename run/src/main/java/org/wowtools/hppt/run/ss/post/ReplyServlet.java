package org.wowtools.hppt.run.ss.post;

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
    private final PostServerSessionService postServerSessionService;
    private final long waitResponseTime;
    private final long replyDelayTime;

    public ReplyServlet(PostServerSessionService postServerSessionService, long waitResponseTime,long replyDelayTime) {
        this.postServerSessionService = postServerSessionService;
        this.waitResponseTime = waitResponseTime;
        this.replyDelayTime = replyDelayTime;
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
        if (replyDelayTime > 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        List<byte[]> bytesList = new LinkedList<>();
        byte[] rBytes;
        try {
            rBytes = ctx.sendQueue.poll(waitResponseTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            rBytes = null;
        }
        if (null == rBytes) {
            return;
        }
        bytesList.add(rBytes);

        ctx.sendQueue.drainTo(bytesList);
        rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
        log.debug("向客户端发送字节 bytesList {} body {}", bytesList.size(), rBytes.length);
        try (OutputStream os = resp.getOutputStream()) {
            os.write(rBytes);
        }
    }
}
