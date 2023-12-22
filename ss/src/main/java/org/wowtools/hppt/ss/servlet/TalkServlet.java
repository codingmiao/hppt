package org.wowtools.hppt.ss.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.ss.StartSs;
import org.wowtools.hppt.ss.service.ClientService;
import org.wowtools.hppt.ss.service.ServerSessionService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author liuyu
 * @date 2023/11/5
 */
@Slf4j
public class TalkServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String loginCode = req.getParameter("c");
        loginCode = loginCode.replace(" ", "+");
        ClientService.Client client = ClientService.getClient(loginCode);
        if (null == client) {
            throw new RuntimeException("not login: " + loginCode);
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
        if (StartSs.config.enableEncrypt) {
            bytes = client.aesCipherUtil.descriptor.decrypt(bytes);
        }
        if (StartSs.config.enableCompress) {
            bytes = BytesUtil.decompress(bytes);
        }

        ProtoMessage.MessagePb inputMessage = ProtoMessage.MessagePb.parseFrom(bytes);
        ProtoMessage.MessagePb outputMessage = ServerSessionService.talk(clientId, inputMessage);
        byte[] rBytes = outputMessage.toByteArray();
        //压缩、加密
        if (StartSs.config.enableCompress) {
            rBytes = BytesUtil.compress(rBytes);
        }
        if (StartSs.config.enableEncrypt) {
            rBytes = client.aesCipherUtil.encryptor.encrypt(rBytes);
        }
        log.debug("返回客户端字节数 {}", rBytes.length);


        resp.setHeader("Server", "hppt");
        try (OutputStream os = resp.getOutputStream()) {
            os.write(rBytes);
        }
    }
}
