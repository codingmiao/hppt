package org.wowtools.hppt.ss.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.ss.service.ClientService;
import org.wowtools.hppt.ss.service.ServerSessionService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author liuyu
 * @date 2023/11/5
 */
@Slf4j
public class InitServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder requestBody = new StringBuilder();
        BufferedReader reader = req.getReader();

        String line;
        while ((line = reader.readLine()) != null) {
            requestBody.append(line);
        }

        reader.close();

        resp.setHeader("Server", "hppt");
        String body = requestBody.toString();
        String[] param = body.split(":");//client:remoteHost:remotePort

        String loginCode = param[0];
        ClientService.Client client = ClientService.getClient(loginCode);
        if (null == client) {
            throw new RuntimeException("not login: " + loginCode);
        }
        String clientId = client.clientId;

        int sessionId = ServerSessionService.initSession(clientId, param[1], Integer.parseInt(param[2]));
        try (PrintWriter write = resp.getWriter()) {
            write.write(String.valueOf(sessionId));
        }
    }
}
