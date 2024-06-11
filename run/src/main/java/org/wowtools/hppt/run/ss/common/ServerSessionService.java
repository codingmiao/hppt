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
 * ServerSessionService抽象类
 * 注意，编写实现类时，不要在构造方法里做会阻塞的事情(比如起一个端口)，丢到init方法里做
 *
 * @param <CTX> 实际和客户端连接的上下文，如ChannelHandlerContext等
 */
@Slf4j
public abstract class ServerSessionService<CTX> {
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
    protected final SsConfig ssConfig;
    protected final ServerSessionManager serverSessionManager;
    protected final LoginClientService loginClientService;

    public ServerSessionService(SsConfig ssConfig) {
        this.ssConfig = ssConfig;
        loginClientService = new LoginClientService(ssConfig.clientIds);
        serverSessionManager = SsUtil.createServerSessionManagerBuilder(ssConfig).build();
    }

    /**
     * 初始化操作，允许阻塞/挂起方法
     *
     * @param ssConfig
     */
    public abstract void init(SsConfig ssConfig) throws Exception;

    /**
     * 发送字节到客户端的具体方法
     *
     * @param ctx   实际和客户端连接的上下文
     * @param bytes bytes
     */
    protected abstract void sendBytesToClient(CTX ctx, byte[] bytes);

    /**
     * 收到客户端传过来的字节时，主动调用此方法进行接收操作
     *
     * @param ctx   实际和客户端连接的上下文
     * @param bytes bytes
     * @throws Exception Exception
     */
    public void receiveClientBytes(CTX ctx, byte[] bytes) {
        if (null == bytes || bytes.length == 0) {
            return;
        }
        log.debug("收到客户端字节数 {}", bytes.length);
        // 若客户端为空,则进行对时或登录
        ClientCell clientCell = ctxClientCellMap.get(ctx);
        if (null == clientCell) {
            bytes = GridAesCipherUtil.decrypt(bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            String[] cmd = s.split(" ", 2);
            switch (cmd[0]) {
                case "dt":
                    log.debug("请求dt");
                    byte[] dt = ("dt " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
                    dt = GridAesCipherUtil.encrypt(dt);
                    sendBytesToClient(ctx, dt);
                    break;
                case "login":
                    clientCell = new ClientCell();
                    LoginClientService.Client client;
                    String loginCode = cmd[1];
                    log.debug("请求login {}", loginCode);
                    client = loginClientService.login(loginCode, clientCell.clientActiveWatcher);
                    if (client == null) {
                        log.warn("登录失败 {}", loginCode);
                        byte[] login = ("login 0").getBytes(StandardCharsets.UTF_8);
                        login = GridAesCipherUtil.encrypt(login);
                        sendBytesToClient(ctx, login);
                    } else {
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
                        log.info("客户端接入成功 {}", clientCell.client.clientId);
                        startSendThread(clientCell);
                        byte[] login = ("login 1").getBytes(StandardCharsets.UTF_8);
                        login = GridAesCipherUtil.encrypt(login);
                        sendBytesToClient(ctx, login);
                    }
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

    /**
     * 关闭上下文
     *
     * @param ctx ctx
     */
    protected abstract void closeCtx(CTX ctx) throws Exception;

    /**
     * 关闭当前服务时需要做的事情
     */
    protected abstract void doClose() throws Exception;

    /**
     * 当发生难以修复的异常等情况时，主动调用此方法结束当前服务，以便后续自动重启等操作
     */
    public void exit() {
        log.warn("ServerSessionService exit {}", this);
        serverSessionManager.close();
        try {
            doClose();
        } catch (Exception e) {
            log.warn("doClose error ", e);
        }
        synchronized (this) {
            this.notify();
        }
    }

    /**
     * 阻塞直到exit方法被调用
     */
    public void sync() {
        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("sync interrupted");
        }
    }

    /**
     * 移除无用的上下文，在有异常、上下文关闭等情况下主动调用
     *
     * @param ctx
     */
    protected void removeCtx(CTX ctx) {
        ClientCell cell = ctxClientCellMap.remove(ctx);
        if (null != cell) {
            cell.running = false;
        }
        try {
            closeCtx(ctx);
        } catch (Exception e) {
            log.warn("closeCtx error", e);
        }
    }

    private void startSendThread(ClientCell cell) {
        LoginClientService.Client client = cell.client;
        Thread.startVirtualThread(() -> {
            while (cell.running) {
                try {
                    byte[] bytes = ServerTalker.replyToClient(ssConfig, serverSessionManager, client, -1, true);
                    if (null != bytes) {
                        sendBytesToClient(cell.ctx, bytes);
                    } else if (!cell.actived) {
                        synchronized (cell.clientActiveWatcher) {
                            log.info("客户端 {} 非活跃，挂起回复消息线程", cell.client.clientId);
                            try {
                                cell.clientActiveWatcher.wait();
                            } catch (InterruptedException e) {
                                log.info("客户端 {} 活跃，恢复回复消息线程", cell.client.clientId);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("向用户端发送消息异常", e);
                    removeCtx(cell.ctx);
                }
            }
            log.info("回复消息线程结束 {} {}", cell.client.clientId, cell.ctx);
        });
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
                        ServerTalker.receiveClientBytes(ssConfig, serverSessionManager, client, bytes);
                    } catch (Exception e) {
                        log.warn("接收客户端消息异常", e);
                        removeCtx(cell.ctx);
                    } catch (Throwable t) {
                        log.error("接收客户端消息错误", t);
                        exit();
                    }
                } else if (!cell.actived) {
                    synchronized (cell.clientActiveWatcher) {
                        log.info("客户端 {} 非活跃，挂起接收消息线程", cell.client.clientId);
                        try {
                            cell.clientActiveWatcher.wait();
                        } catch (InterruptedException e) {
                            log.info("客户端 {} 活跃，恢复接收消息线程", cell.client.clientId);
                        }
                    }
                }
            }
            log.info("接收消息线程结束 {} {}", cell.client.clientId, cell.ctx);
        });
    }
}
