package org.wowtools.hppt.cs.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.cs.StartCs;
import org.wowtools.hppt.cs.service.ClientService;
import org.wowtools.hppt.cs.service.ServerSessionService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author liuyu
 * @date 2023/12/20
 */
@Slf4j
public class TalkServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Server", "hppt");
        String loginCode = req.getParameter("c");
        loginCode = loginCode.replace(" ", "+");
        ClientService.Client client = ClientService.getClient(loginCode);
        if (null == client) {
            log.warn("not_login: {}", loginCode);
            resp.setHeader("err", "not_login");
            return;
        }
        String clientId = client.clientId;

        //读请求体里带过来的bytes
        byte[] bytes;
        try (InputStream inputStream = req.getInputStream(); ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            bytes = byteArrayOutputStream.toByteArray();
        }
        //解密、解压
        if (StartCs.config.enableEncrypt) {
            bytes = client.aesCipherUtil.descriptor.decrypt(bytes);
        }
        if (StartCs.config.enableCompress) {
            bytes = BytesUtil.decompress(bytes);
        }

        ProtoMessage.MessagePb inputMessage = ProtoMessage.MessagePb.parseFrom(bytes);
        ProtoMessage.MessagePb outputMessage = ServerSessionService.talk(clientId, inputMessage);
        byte[] rBytes = outputMessage.toByteArray();
        //压缩、加密
        if (StartCs.config.enableCompress) {
            rBytes = BytesUtil.compress(rBytes);
        }
        if (StartCs.config.enableEncrypt) {
            rBytes = client.aesCipherUtil.encryptor.encrypt(rBytes);
        }
        log.debug("返回客户端字节数 {}", rBytes.length);


        try (OutputStream os = resp.getOutputStream()) {
            os.write(rBytes);
        }
    }
}
