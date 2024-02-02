package org.wowtools.hppt.run.sc.post;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.wowtools.hppt.common.client.*;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.util.AesCipherUtil;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyu
 * @date 2024/1/31
 */
@Slf4j
public class PostClientSessionService {

    private final ScConfig config;

    private final AesCipherUtil aesCipherUtil;

    private final String loginCode;

    private final BlockingQueue<String> sendCommandQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<SessionBytes> sendBytesQueue = new LinkedBlockingQueue<>();

    private final AtomicInteger newSessionFlagIdx = new AtomicInteger();
    private final Map<Integer, ClientBytesSender.SessionIdCallBack> sessionIdCallBackMap = new HashMap<>();//<newSessionFlag,cb>

    private final ClientSessionManager clientSessionManager;

    private final Thread sendThread;
    private long sleepTime;
    private final AtomicBoolean sleeping = new AtomicBoolean(false);

    public PostClientSessionService(ScConfig config) {
        this.config = config;
        //登录
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
        loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(config.clientId.getBytes(StandardCharsets.UTF_8)));

        //建立ClientSessionManager，并绑上配置的端口
        clientSessionManager = new ClientSessionManagerBuilder()
                .setBufferSize(config.maxSendBodySize * 2)
                .setLifecycle(buildClientSessionLifecycle(config))
                .setClientBytesSender(new PostClientBytesSender(config))
                .build();
        for (ScConfig.Forward forward : config.forwards) {
            clientSessionManager.bindPort(forward.localPort);
        }

        //循环发起post请求发送数据
        sleepTime = config.initSleepTime - config.addSleepTime;
        sendThread = buildSendThread();
        sendThread.start();
    }


    protected void awakenSendThread() {
        if (sleeping.get()) {
            try {
                sleepTime = config.initSleepTime;
                sendThread.interrupt();
                log.info("唤醒发送线程");
            } catch (Exception e) {
                log.warn("唤醒线程异常", e);
            }
        }
    }

    private Thread buildSendThread() {
        return new Thread(() -> {
            while (true) {
                //发请求
                boolean isEmpty = true;
                try {
                    byte[] sendBytes = ClientTalker.buildSendToServerBytes(config, config.maxSendBodySize, sendCommandQueue, sendBytesQueue, aesCipherUtil);
                    if (null != sendBytes) {
                        isEmpty = false;
                    }
                    byte[] responseBytes;
                    try (Response response = HttpUtil.doPost(config.serverUrl + "/talk?c=" + loginCode, sendBytes)) {
                        ResponseBody body = response.body();
                        responseBytes = null == body ? null : body.bytes();
                    }
                    if (null != responseBytes) {
                        boolean r = ClientTalker.receiveServerBytes(config, responseBytes, clientSessionManager, aesCipherUtil, sendCommandQueue, sessionIdCallBackMap);
                        if (!r) {
                            isEmpty = false;
                        }
                    }
                } catch (Exception e) {
                    log.warn("与服务端交互异常", e);
                }
                //确定休眠时间并休眠
                if (clientSessionManager.getSessionNum() == 0
                        && sendBytesQueue.isEmpty()
                        && sendCommandQueue.isEmpty()
                ) {
                    //无客户端，长睡直到被唤醒
                    log.info("无客户端连接，且无命令要发送，睡眠发送线程");
                    sleepTime = Long.MAX_VALUE;
                } else if (isEmpty) {
                    //收发数据包都为空，逐步增加睡眠时间
                    if (sleepTime < config.maxSleepTime) {
                        sleepTime += config.addSleepTime;
                    }
                    log.debug("收发数据包都为空，逐步增加睡眠时间 {}", sleepTime);
                } else {
                    sleepTime = config.initSleepTime;
                    log.debug("正常包 {}", sleepTime);
                }

                if (sleepTime > 0) {
                    sleeping.set(true);
                    try {
                        log.debug("sleep {}", sleepTime);
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        log.info("发送进程被唤醒");
                    } finally {
                        sleeping.set(false);
                    }
                }
            }
        });
    }

    //获取服务端时间-本地时间的差值
    private long getDt() {
        long localTs = System.currentTimeMillis();
        String res;
        try (Response response = HttpUtil.doPost(config.serverUrl + "/time")) {
            assert response.body() != null;
            res = response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("获取服务器时间异常", e);
        }
        long serverTs = Long.parseLong(res);
        return serverTs - localTs;
    }

    private AesCipherUtil login() {
        long dt = getDt();
        AesCipherUtil aesCipherUtil = new AesCipherUtil(config.clientId, System.currentTimeMillis() + dt);
        String loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(config.clientId.getBytes(StandardCharsets.UTF_8)));

        String res;
        try (Response response = HttpUtil.doPost(config.serverUrl + "/login?c="
                + URLEncoder.encode(loginCode, StandardCharsets.UTF_8))) {
            assert response.body() != null;
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

    private ClientSessionLifecycle buildClientSessionLifecycle(ScConfig scConfig) {
        if (StringUtil.isNullOrEmpty(scConfig.lifecycle)) {
            return new ClientSessionLifecycle() {
                @Override
                public void created(ClientSession clientSession) {
                    awakenSendThread();
                }

                @Override
                public void afterSendToTarget(ClientSession clientSession, byte[] bytes) {
                    awakenSendThread();
                }

                @Override
                public void afterSendToUser(ClientSession clientSession, byte[] bytes) {
                    awakenSendThread();
                }

                @Override
                public void closed(ClientSession clientSession) {
                    sendCommandQueue.add(String.valueOf(Constant.SsCommands.CloseSession) + clientSession.getSessionId());
                }
            };
        } else {
            try {
                Class<? extends ClientSessionLifecycle> clazz = (Class<? extends ClientSessionLifecycle>) Class.forName(scConfig.lifecycle);
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }


    private final class PostClientBytesSender implements ClientBytesSender {
        private final ScConfig config;

        public PostClientBytesSender(ScConfig config) {
            this.config = config;
        }


        @Override
        public void connected(int port, ChannelHandlerContext ctx, SessionIdCallBack cb) {
            ScConfig.Forward forward = null;
            for (ScConfig.Forward fw : config.forwards) {
                if (port == fw.localPort) {
                    forward = fw;
                    break;
                }
            }
            if (null == forward) {
                log.warn("找不到forward port {}", port);
                return;
            }
            int newSessionFlag = newSessionFlagIdx.addAndGet(1);
            sendCommandQueue.add(Constant.SsCommands.CreateSession +
                    forward.remoteHost + Constant.sessionIdJoinFlag + forward.remotePort
                    + Constant.sessionIdJoinFlag + newSessionFlag);
            sessionIdCallBackMap.put(newSessionFlag, cb);
            awakenSendThread();
        }

        @Override
        public void sendToTarget(ClientSession clientSession, byte[] bytes) {
            sendBytesQueue.add(new SessionBytes(clientSession.getSessionId(), bytes));
        }
    }
}
