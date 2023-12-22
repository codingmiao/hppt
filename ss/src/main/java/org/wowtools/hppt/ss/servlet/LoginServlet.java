package org.wowtools.hppt.ss.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.ss.service.ClientService;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author liuyu
 * @date 2023/12/15
 */
@Slf4j
public class LoginServlet extends HttpServlet {
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String code = req.getParameter("c");
        code = code.replace(" ", "+");
        boolean success = ClientService.login(code);
        if (!success) {
            log.warn("无效的登录信息 {}", code);
        }
        resp.setHeader("Server", "hppt");
        try (PrintWriter write = resp.getWriter()) {
            write.write(success ? "1" : "0");
        }
    }
}
