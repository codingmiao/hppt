package org.wowtools.hppt.run.ss.util;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.common.utils.LruCache;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.common.server.ServerTalker;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 ChannelHandlerContext 的ss端消息交互工具
 *
 * @author liuyu
 * @date 2024/3/12
 */
@Slf4j
public abstract class SsChannelTalker {

    private static final class ClientCell {
        LoginClientService.Client client;
        volatile boolean actived = true;
    }

    private final SsConfig ssConfig;
    private final ServerSessionManager serverSessionManager;
    private final LoginClientService loginClientService;

    public SsChannelTalker(SsConfig ssConfig, ServerSessionManager serverSessionManager, LoginClientService loginClientService) {
        this.ssConfig = ssConfig;
        this.serverSessionManager = serverSessionManager;
        this.loginClientService = loginClientService;
    }

    private final Map<ChannelHandlerContext, ClientCell> ctxClientCellMap = LruCache.buildCache(128, 8);

    private ClientCell getClient(ChannelHandlerContext ctx) {
        return ctxClientCellMap.get(ctx);
    }

    public void removeClient(ChannelHandlerContext ctx) {
        ClientCell clientCell = ctxClientCellMap.remove(ctx);
        if (null == clientCell) {
            return;
        }
        clientCell.actived = false;
        log.info("客户端断开 {}", clientCell.client.clientId);
    }

    private void startSendThread(ChannelHandlerContext ctx, ClientCell cell) {
        LoginClientService.Client client = cell.client;
        Thread.startVirtualThread(() -> {
            while (cell.actived) {
                try {
                    byte[] bytes = ServerTalker.replyToClient(ssConfig, serverSessionManager, client, -1, true);
                    if (null != bytes) {
                        ctx.channel().writeAndFlush(buildWriteAndFlushObj(ctx, bytes));
                    }
                } catch (Exception e) {
                    log.warn("向用户端发送消息异常", e);
                }
            }
        });
    }

    public void doChannelRead0(ChannelHandlerContext ctx, byte[] bytes) {


        // 若客户端为空,则进行对时或登录
        ClientCell clientCell = getClient(ctx);
        if (null == clientCell) {
            String[] cmd = new String(bytes, StandardCharsets.UTF_8).split(" ", 2);
            switch (cmd[0]) {
                case "dt":
                    byte[] dt = ("dt " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
                    ctx.channel().writeAndFlush(buildWriteAndFlushObj(ctx, dt));
                    break;
                case "login":
                    String loginCode = cmd[1];
                    if (!loginClientService.login(loginCode)) {
                        log.warn("登录失败 {}", loginCode);
                        byte[] login = ("login 0").getBytes(StandardCharsets.UTF_8);
                        ctx.channel().writeAndFlush(buildWriteAndFlushObj(ctx, login));
                    } else {
                        LoginClientService.Client client = loginClientService.getClient(loginCode);
                        clientCell = new ClientCell();
                        clientCell.client = client;
                        ctxClientCellMap.put(ctx, clientCell);
                        startSendThread(ctx, clientCell);
                        log.info("客户端接入成功 {}", clientCell.client.clientId);
                        byte[] login = ("login 1").getBytes(StandardCharsets.UTF_8);
                        ctx.channel().writeAndFlush(buildWriteAndFlushObj(ctx, login));
                    }
                    break;
                default:
                    log.warn("未知命令 {} {}", cmd[0], cmd[1]);
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
            ctx.close();
        }

    }

    protected abstract Object buildWriteAndFlushObj(ChannelHandlerContext ctx, byte[] bytes);
}
