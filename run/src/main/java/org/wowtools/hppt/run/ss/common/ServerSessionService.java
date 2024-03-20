package org.wowtools.hppt.run.ss.common;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.common.utils.LruCache;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.common.server.ServerTalker;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.util.SsUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ServerSessionService抽象类
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
    protected void receiveClientBytes(CTX ctx, byte[] bytes) throws Exception {
        // 若客户端为空,则进行对时或登录
        ClientCell clientCell = ctxClientCellMap.get(ctx);
        if (null == clientCell) {
            String[] cmd = new String(bytes, StandardCharsets.UTF_8).split(" ", 2);
            switch (cmd[0]) {
                case "dt":
                    byte[] dt = ("dt " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
                    sendBytesToClient(ctx, dt);
                    break;
                case "login":
                    String loginCode = cmd[1];
                    if (!loginClientService.login(loginCode)) {
                        log.warn("登录失败 {}", loginCode);
                        byte[] login = ("login 0").getBytes(StandardCharsets.UTF_8);
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
                        sendBytesToClient(ctx, login);
                    }
                    break;
                default:
                    log.warn("未知命令 {} {}", cmd[0], cmd[1]);
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
        }
    }

    /**
     * 关闭上下文
     *
     * @param ctx ctx
     */
    protected abstract void closeCtx(CTX ctx) throws Exception;

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
