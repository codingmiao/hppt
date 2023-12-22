package org.wowtools.hppt.cc;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.config.Configurator;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.cc.pojo.CcConfig;
import org.wowtools.hppt.cc.service.ClientSessionService;
import org.wowtools.hppt.common.util.AesCipherUtil;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.Constant;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * @author liuyu
 * @date 2023/12/20
 */
@Slf4j
public class StartCc {
    public static CcConfig config;

    public static AesCipherUtil aesCipherUtil;

    public static final String loginCode;

    static {
        Configurator.initialize(null, ResourcesReader.getRootPath(StartCc.class) + "/log4j2.xml");
        try {
            config = Constant.ymlMapper.readValue(ResourcesReader.readStr(StartCc.class, "cc.yml"), CcConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件异常", e);
        }

        if (!login()) {
            //排除整点附近登录失败的情况
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (!login()) {
                throw new RuntimeException("登录失败");
            }
        }
        loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(StartCc.config.clientId.getBytes(StandardCharsets.UTF_8)));
        log.info("登录成功");
    }

    //获取服务端时间-本地时间的差值
    private static long getDt() {
        long localTs = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StartCc.config.serverUrl + "/time"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = null;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("获取时间戳异常", e);
        }
        long serverTs = Long.parseLong(response.body());
        return serverTs - localTs;
    }

    public static boolean login() {
        long dt = getDt();
        aesCipherUtil = new AesCipherUtil(StartCc.config.clientId, System.currentTimeMillis() + dt);
        String loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(StartCc.config.clientId.getBytes(StandardCharsets.UTF_8)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StartCc.config.serverUrl + "/login?c=" + URLEncoder.encode(loginCode, StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = null;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("获取sessionId异常", e);
        }
        if ("1".equals(response.body())) {
            return true;
        } else {
            log.warn("登录失败 " + response.body());
            return false;
        }
    }

    public static void main(String[] args) {
        ClientSessionService.start();
    }
}
