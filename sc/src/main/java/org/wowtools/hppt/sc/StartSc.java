package org.wowtools.hppt.sc;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.wowtools.common.utils.ResourcesReader;
import org.wowtools.hppt.common.util.AesCipherUtil;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.sc.pojo.ScConfig;
import org.wowtools.hppt.sc.service.ClientPort;
import org.wowtools.hppt.sc.service.ClientSession;
import org.wowtools.hppt.sc.service.ClientSessionManager;
import org.wowtools.hppt.sc.service.ClientSessionService;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * @author liuyu
 * @date 2023/11/25
 */
@Slf4j
public class StartSc {

    public static final ScConfig config;

    public static final AesCipherUtil aesCipherUtil;

    public static final String loginCode;

    static {
        Configurator.reconfigure(new File(ResourcesReader.getRootPath(StartSc.class) + "/log4j2.xml").toURI());
        try {
            config = Constant.ymlMapper.readValue(ResourcesReader.readStr(StartSc.class, "sc.yml"), ScConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件异常", e);
        }

        AesCipherUtil _aesCipherUtil = login();
        if (null == _aesCipherUtil) {
            //排除整点附近登录失败的情况
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            _aesCipherUtil = login();
        }
        if (null == _aesCipherUtil) {
            throw new RuntimeException("登录失败");
        }
        aesCipherUtil = _aesCipherUtil;
        loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(StartSc.config.clientId.getBytes(StandardCharsets.UTF_8)));
        log.info("登录成功");
    }

    //获取服务端时间-本地时间的差值
    private static long getDt() {
        long localTs = System.currentTimeMillis();
        String res;
        try (Response response = HttpUtil.doPost(StartSc.config.serverUrl + "/time")) {
            res = response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("获取服务器时间异常", e);
        }
        long serverTs = Long.parseLong(res);
        return serverTs - localTs;
    }

    private static AesCipherUtil login() {
        long dt = getDt();
        AesCipherUtil aesCipherUtil = new AesCipherUtil(StartSc.config.clientId, System.currentTimeMillis() + dt);
        String loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(StartSc.config.clientId.getBytes(StandardCharsets.UTF_8)));

        String res;
        try (Response response = HttpUtil.doPost(StartSc.config.serverUrl + "/login?c="
                + URLEncoder.encode(loginCode, StandardCharsets.UTF_8))) {
            res = response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("获取服务器时间异常", e);
        }
        if ("1".equals(res)) {
            return aesCipherUtil;
        } else {
            log.warn("登录失败 " + res);
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        for (ScConfig.Forward forward : config.forwards) {
            new ClientPort(forward.localPort, (_clientPort, channelHandlerContext) -> {
                /* 1、用户发起连接，ClientPort收到连接信息后向ServerPort发起连接请求，ServerPort返回一个唯一的sessionId; */
                //去服务端拿sessionId
                int sessionId;
                try {
                    sessionId = ClientSessionService.initServerSession(forward.remoteHost, forward.remotePort);
                    log.info("新会话建立 {}  {} --> {} --> {}:{}",
                            sessionId, channelHandlerContext.channel().remoteAddress(),
                            forward.localPort,
                            forward.remoteHost, forward.remotePort);
                } catch (Exception e) {
                    log.info("建立会话异常，主动关闭  {} --> {} --> {}:{}",
                            channelHandlerContext.channel().remoteAddress(),
                            forward.localPort,
                            forward.remoteHost, forward.remotePort);
                    channelHandlerContext.close();
                    return;
                }
                /* 2、ClientPort根据sessionId新建一个ClientSession，ServerPort根据sessionId新建一个ServerSession；*/
                ClientSession clientSession = ClientSessionManager.createClientSession(sessionId, _clientPort, channelHandlerContext);
                /* 3、用户->ClientSession->HTTP服务 通信->ServerSession->真实端口的通道建立完成。*/
            });
            log.info("端口映射 {} --> {}:{}", forward.localPort, forward.remoteHost, forward.remotePort);
        }
        log.warn("----------------启动完成");

        Thread.sleep(Long.MAX_VALUE);//虚拟线程可能退出，所以这里睡眠掉主线程

    }
}
