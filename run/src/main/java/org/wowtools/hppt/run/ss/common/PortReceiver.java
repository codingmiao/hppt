package org.wowtools.hppt.run.ss.common;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.common.server.ServerTalker;
import org.wowtools.hppt.common.util.GridAesCipherUtil;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.util.SsUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 普通模式下，数据被发送到真实端口
 *
 * @author liuyu
 * @date 2024/9/26
 */
@Slf4j
final class PortReceiver<CTX> extends Receiver<CTX> {
    private final ServerSessionService<CTX> serverSessionService;
    private final ServerSessionManager serverSessionManager;
    private final LoginClientService loginClientService;
    private final SsConfig ssConfig;

    public PortReceiver(SsConfig ssConfig, ServerSessionService<CTX> serverSessionService) {
        this.ssConfig = ssConfig;
        this.serverSessionService = serverSessionService;
        LoginClientService.Config lConfig = new LoginClientService.Config();
        for (SsConfig.Client client : ssConfig.clients) {
            lConfig.users.add(new String[]{client.user, client.password});
        }
        lConfig.passwordRetryNum = ssConfig.passwordRetryNum;
        loginClientService = new LoginClientService(lConfig);
        serverSessionManager = SsUtil.createServerSessionManagerBuilder(ssConfig).build();
    }

    private final class ClientCell {
        LoginClientService.Client client;
        volatile boolean running = true;
        volatile boolean actived = true;

        CTX ctx;
        final LoginClientService.ClientActiveWatcher clientActiveWatcher = new LoginClientService.ClientActiveWatcher() {
            @Override
            public void toInactivity() {
                actived = false;
            }

            @Override
            public void toActivity() {
                actived = true;
                synchronized (clientActiveWatcher) {
                    clientActiveWatcher.notifyAll();
                }
            }
        };
    }

    private final Map<CTX, ClientCell> ctxClientCellMap = new ConcurrentHashMap<>();

    @Override
    public void receiveClientBytes(CTX ctx, byte[] bytes) {
        // 若客户端为空,则进行对时或登录
        ClientCell clientCell = ctxClientCellMap.get(ctx);
        if (null == clientCell) {
            try {
                bytes = GridAesCipherUtil.decrypt(bytes);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("无效的字节，舍弃,ctx {}, bytes {}", ctx, new String(bytes, StandardCharsets.UTF_8), e);
                }
                return;
            }
            String s = new String(bytes, StandardCharsets.UTF_8);
            String[] cmd = s.split(" ", 2);
            switch (cmd[0]) {
                case "dt":
                    log.debug("请求dt");
                    byte[] dt = ("dt " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
                    dt = GridAesCipherUtil.encrypt(dt);
                    serverSessionService.sendBytesToClient(ctx, dt);
                    break;
                case "login":
                    clientCell = new ClientCell();
                    LoginClientService.Client client;
                    String loginCode = cmd[1];
                    log.debug("请求login {}", loginCode);
                    try {
                        client = loginClientService.login(loginCode, clientCell.clientActiveWatcher);
                    } catch (Exception e) {
                        log.warn("登录失败 {} {}", loginCode, e.getMessage());
                        byte[] login = ("login " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
                        login = GridAesCipherUtil.encrypt(login);
                        serverSessionService.sendBytesToClient(ctx, login);
                        break;
                    }
                    clientCell.client = client;
                    clientCell.ctx = ctx;
                    synchronized (ctxClientCellMap) {
                        ctxClientCellMap.forEach((oldCtx, oldCell) -> {
                            if (oldCell.client.clientId.equals(client.clientId)) {
                                log.info("重复登录，移除旧client {} {}", oldCell.client.clientId, ctx);
                                removeCtx(oldCtx);
                            }
                        });
                        ctxClientCellMap.put(ctx, clientCell);
                    }
                    log.info("客户端接入成功 user: {} ctx: {}", clientCell.client.clientId, ctx);
                    startSendThread(clientCell);
                    byte[] login = ("login 0").getBytes(StandardCharsets.UTF_8);
                    login = GridAesCipherUtil.encrypt(login);
                    serverSessionService.sendBytesToClient(ctx, login);
                    break;
                default:
                    log.warn("未知命令 {} ", s);
                    removeCtx(ctx);
            }
            return;
        }
        // 否则进行常规数据接收操作
        LoginClientService.Client client = clientCell.client;
        client.receiveClientBytes.add(bytes);
        if (!clientCell.actived) {
            clientCell.clientActiveWatcher.toActivity();
        }
    }

    private void startSendThread(ClientCell cell) {
        LoginClientService.Client client = cell.client;
        //回复消息到客户端的线程
        Thread.startVirtualThread(() -> {
            ServerTalker.Replier replier = (bytes) -> {
                try {
                    if (null != bytes) {
                        serverSessionService.sendBytesToClient(cell.ctx, bytes);
                    } else if (!cell.actived) {
                        synchronized (cell.clientActiveWatcher) {
                            log.info("客户端 {} 非活跃，挂起回复消息线程", cell.client.clientId);
                            do {
                                try {
                                    cell.clientActiveWatcher.wait(10_000);
                                } catch (InterruptedException ignored) {
                                }
                            }while (!cell.actived);
                            log.info("客户端 {} 活跃，恢复回复消息线程", cell.client.clientId);

                        }
                    }
                } catch (Exception e) {
                    log.warn("向用户端发送消息异常", e);
                    removeCtx(cell.ctx);
                    return false;
                }
                return true;
            };
            while (cell.running) {
                try {
                    ServerTalker.replyToClient(ssConfig, serverSessionManager, client, ssConfig.maxReturnBodySize, true, replier);
                } catch (Exception e) {
                    log.warn("向用户端发送消息异常", e);
                    removeCtx(cell.ctx);
                }
            }
            log.info("回复消息线程结束 {} {}", cell.client.clientId, cell.ctx);
        });
        //接收客户端消息的线程
        Thread.startVirtualThread(() -> {
            while (cell.running) {
                byte[] bytes;
                try {
                    bytes = client.receiveClientBytes.poll(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("e", e);
                    continue;
                }
                if (null != bytes) {
                    //接消息
                    try {
                        ServerTalker.receiveClientBytes(ssConfig, serverSessionManager, client, bytes, ssConfig.initSessionTimeout);
                    } catch (Exception e) {
                        log.warn("接收客户端消息异常", e);
                        removeCtx(cell.ctx);
                    } catch (Throwable t) {
                        log.error("接收客户端消息错误", t);
                        serverSessionService.exit("接收客户端消息错误");
                    }
                } else if (!cell.actived) {
                    synchronized (cell.clientActiveWatcher) {
                        log.info("客户端 {} 非活跃，挂起接收消息线程", cell.client.clientId);
                        do {
                            try {
                                cell.clientActiveWatcher.wait(10_000);
                            } catch (InterruptedException ignored) {
                            }
                        }while (!cell.actived);
                        log.info("客户端 {} 活跃，恢复接收消息线程", cell.client.clientId);

                    }
                }
            }
            log.info("接收消息线程结束 {} {}", cell.client.clientId, cell.ctx);
        });
    }

    public void removeCtx(CTX ctx) {
        ClientCell cell = ctxClientCellMap.remove(ctx);
        if (null != cell) {
            cell.running = false;
        }
    }

    @Override
    public void exit() {
        serverSessionManager.close();
    }

    @Override
    public long getLastHeartbeatTime() {
        return serverSessionManager.getLastHeartbeatTime();
    }
}
