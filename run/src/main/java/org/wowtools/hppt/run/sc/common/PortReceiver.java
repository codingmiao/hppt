package org.wowtools.hppt.run.sc.common;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.client.ClientBytesSender;
import org.wowtools.hppt.common.client.ClientSession;
import org.wowtools.hppt.common.client.ClientSessionManager;
import org.wowtools.hppt.common.client.ClientTalker;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.util.*;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.sc.util.ScUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyu
 * @date 2024/9/27
 */
@Slf4j
 final class PortReceiver implements Receiver {
    protected final ScConfig config;
    protected final ClientSessionManager clientSessionManager;
    private final ClientSessionService clientSessionService;


    private final Map<Integer, ClientBytesSender.SessionIdCallBack> sessionIdCallBackMap = new ConcurrentHashMap<>();//<newSessionFlag,cb>
    private AesCipherUtil aesCipherUtil;

    private Long dt;

    private boolean firstLoginErr = true;
    protected boolean noLogin = true;

    protected volatile boolean running = true;


    public PortReceiver(ScConfig config,ClientSessionService clientSessionService) throws Exception {
        this.config = config;
        this.clientSessionService = clientSessionService;
        clientSessionManager = ScUtil.createClientSessionManager(config,
                clientSessionService.buildClientSessionLifecycle(), buildClientBytesSender());
        buildSendThread().start();
        clientSessionService.connectToServer(config, () -> {
            log.info("连接建立完成");
            Thread.startVirtualThread(() -> {
                //建立连接后，获取时间戳
                clientSessionService.sendBytesToServer(GridAesCipherUtil.encrypt("dt".getBytes(StandardCharsets.UTF_8)));
                //等待时间戳返回
                int n = 0;
                while (null == dt) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        continue;
                    }
                    n++;
                    if (n > 100) {
                        //等超过10秒依然没有收到dt，重发一次
                        clientSessionService.sendBytesToServer(GridAesCipherUtil.encrypt("dt".getBytes(StandardCharsets.UTF_8)));
                        n = 0;
                    }
                }
                //登录
                sendLoginCommand();
                checkSessionInit();
            });
        });
    }

    @Override
    public void receiveServerBytes(byte[] bytes) throws Exception {
        if (noLogin) {
            bytes = GridAesCipherUtil.decrypt(bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            String[] cmd = s.split(" ", 2);
            switch (cmd[0]) {
                case "dt":
                    synchronized (this) {
                        if (null == dt) {
                            long localTs = System.currentTimeMillis();
                            long serverTs = Long.parseLong(cmd[1]);
                            dt = serverTs - localTs;
                            log.info("dt {} ms", dt);
                        }
                    }
                    break;
                case "login":
                    String code = cmd[1];
                    if ("0".equals(code)) {
                        noLogin = false;
                        log.info("登录成功");
                    } else if (firstLoginErr) {
                        firstLoginErr = false;
                        log.warn("第一次登录失败 {} ，重试", code);
                        Thread.sleep(10000);
                        sendLoginCommand();
                    } else {
                        log.error("登录失败 {}", code);
                        System.exit(0);
                    }
                    break;
                default:
                    log.warn("未知命令 {}", s);
            }
        } else {
            ClientTalker.receiveServerBytes(config, bytes, clientSessionManager, aesCipherUtil, clientSessionService.sendCommandQueue, sessionIdCallBackMap);
        }
    }

    @Override
    public void exit() {
        running = false;
        clientSessionManager.close();
    }

    @Override
    public boolean notUsed() {
        return !noLogin && clientSessionManager.getSessionNum() == 0;
    }

    private void sendLoginCommand() {
        aesCipherUtil = new AesCipherUtil(config.clientPassword, System.currentTimeMillis() + dt);
        String loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(config.clientPassword.getBytes(StandardCharsets.UTF_8)));
        clientSessionService.sendBytesToServer(GridAesCipherUtil.encrypt(("login " + config.clientUser + " " + loginCode).getBytes(StandardCharsets.UTF_8)));
    }

    //起一个线程定时检测是否有SessionIdCallBack长期未得到响应，若是则说明连接故障，重启ClientSessionService
    private void checkSessionInit() {
        Thread.startVirtualThread(() -> {
            while (running) {
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    continue;
                }
                for (Map.Entry<Integer, ClientBytesSender.SessionIdCallBack> entry : sessionIdCallBackMap.entrySet()) {
                    if (RoughTimeUtil.getTimestamp() - entry.getValue().createTime > 60000) {
                        log.warn("session长期未连接成功，疑似连接故障，重启");
                        clientSessionService.exit();
                        return;
                    }
                }
            }
        });
    }

    private Thread buildSendThread() {
        return new Thread(() -> {
            while (running) {
                try {
                    byte[] sendBytes = ClientTalker.buildSendToServerBytes(config, config.maxSendBodySize, clientSessionService.sendCommandQueue, clientSessionService.sendBytesQueue, aesCipherUtil, true);
                    if (null != sendBytes) {
                        log.debug("sendBytes {}", sendBytes.length);
                        clientSessionService.sendBytesToServer(sendBytes);
                    }
                } catch (Exception e) {
                    log.warn("发送消息异常", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

        });
    }

    private ClientBytesSender buildClientBytesSender() {
        return new ClientBytesSender() {
            private final AtomicInteger newSessionFlagIdx = new AtomicInteger();

            @Override
            public void connected(int port, ChannelHandlerContext ctx, SessionIdCallBack cb) {
                for (ScConfig.Forward forward : config.forwards) {
                    if (forward.localPort == port) {
                        int newSessionFlag = newSessionFlagIdx.addAndGet(1);
                        String cmd = Constant.SsCommands.CreateSession + forward.remoteHost + Constant.sessionIdJoinFlag + forward.remotePort + Constant.sessionIdJoinFlag + newSessionFlag;
                        clientSessionService.sendCommandQueue.add(cmd);
                        log.debug("connected command: {}", cmd);
                        sessionIdCallBackMap.put(newSessionFlag, cb);
                        log.info("建立连接 {}: {}->{}:{}", ctx.hashCode(), forward.localPort, forward.remoteHost, forward.remotePort);
                        try {
                            clientSessionService.newConnected();
                        } catch (Exception e) {
                            log.warn("newConnected Exception", e);
                        }
                        return;
                    }
                }
                throw new RuntimeException("未知 localPort " + port);
            }

            @Override
            public void sendToTarget(ClientSession clientSession, byte[] bytes) {
                clientSessionService.sendBytesQueue.add(new SessionBytes(clientSession.getSessionId(), bytes));
            }
        };
    }
}
