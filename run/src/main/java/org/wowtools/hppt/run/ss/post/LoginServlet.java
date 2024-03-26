//package org.wowtools.hppt.run.ss.post;
//
//import jakarta.servlet.http.HttpServlet;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.extern.slf4j.Slf4j;
//import org.wowtools.hppt.common.server.LoginClientService;
//
//import java.io.IOException;
//import java.io.PrintWriter;
//
///**
// * @author liuyu
// * @date 2023/12/15
// */
//@Slf4j
//public class LoginServlet extends HttpServlet {
//
//    private final LoginClientService loginClientService;
//
//    public LoginServlet(LoginClientService loginClientService) {
//        this.loginClientService = loginClientService;
//    }
//
//    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
//        String code = req.getParameter("c");
//        code = code.replace(" ", "+");
//        boolean success = loginClientService.login(code);
//        if (!success) {
//            log.warn("无效的登录信息 {}", code);
//        }
//        resp.setHeader("Server", "");
//        try (PrintWriter write = resp.getWriter()) {
//            write.write(success ? "1" : "0");
//        }
//    }
//}
