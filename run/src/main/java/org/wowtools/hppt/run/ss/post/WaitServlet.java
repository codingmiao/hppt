package org.wowtools.hppt.run.ss.post;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 挂起等待的servlet。
 * 挂起一个请求，当服务端有返回，或客户端有输入是打断
 *
 * @author liuyu
 * @date 2024/5/30
 */
@Slf4j
public class WaitServlet extends HttpServlet {
    private final PostServerSessionService postServerSessionService;

    public WaitServlet(PostServerSessionService postServerSessionService) {
        this.postServerSessionService = postServerSessionService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Server", "");
        String cookie = req.getParameter("c");
        PostCtx ctx = postServerSessionService.ctxMap.get(cookie);
        if (null != ctx) {
            Thread t = ctx.waitResponseThread;
            if (null != t) {
                t.interrupt();
                log.debug("打断waitResponseThread");
            }
        }

    }
}
