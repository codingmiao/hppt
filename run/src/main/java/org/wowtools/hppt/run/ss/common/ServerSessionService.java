package org.wowtools.hppt.run.ss.common;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.common.utils.LruCache;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.common.server.ServerTalker;
import org.wowtools.hppt.common.util.GridAesCipherUtil;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.util.SsUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
        volatile boolean actived = true;
        CTX ctx;
    }

    private final Map<CTX, ClientCell> ctxClientCellMap = LruCache.buildCache(128, 8);

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
        //TODO 锁ctx异步
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
                    String loginCode = cmd[1];
                    log.debug("请求login {}", loginCode);
                    if (!loginClientService.login(loginCode)) {
                        log.warn("登录失败 {}", loginCode);
                        byte[] login = ("login 0").getBytes(StandardCharsets.UTF_8);
                        login = GridAesCipherUtil.encrypt(login);
                        sendBytesToClient(ctx, login);
                    } else {
                        LoginClientService.Client client = loginClientService.getClient(loginCode);
                        clientCell = new ClientCell();
                        clientCell.client = client;
                        clientCell.ctx = ctx;
                        ctxClientCellMap.put(ctx, clientCell);
                        startSendThread(clientCell);
                        log.info("客户端接入成功 {}", clientCell.client.clientId);
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
        //接消息
        try {
            ServerTalker.receiveClientBytes(ssConfig, serverSessionManager, client, bytes);
        } catch (Exception e) {
            log.warn("接收客户端消息异常", e);
            removeCtx(ctx);
        } catch (Throwable t) {
            log.error("接收客户端消息错误", t);
            exit();
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
            cell.actived = false;
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
            while (cell.actived) {
                try {
                    byte[] bytes = ServerTalker.replyToClient(ssConfig, serverSessionManager, client, -1, true);
                    if (null != bytes) {
                        sendBytesToClient(cell.ctx, bytes);
                    }
                } catch (Exception e) {
                    log.warn("向用户端发送消息异常", e);
                    removeCtx(cell.ctx);
                }
            }
        });
    }
}
