package org.wowtools.hppt.ss.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author liuyu
 * @date 2023/12/15
 */
public class TimeServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (PrintWriter write = resp.getWriter()) {
            write.write(String.valueOf(System.currentTimeMillis()));
        }
    }
}
