package org.wowtools.hppt.common.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.wowtools.hppt.common.util.Constant.commandParamJoinFlag;

/**
 * @author liuyu
 * @date 2023/11/18
 */
@Slf4j
public class ServerSessionManager {

    private final AtomicInteger sessionIdBuilder = new AtomicInteger();

    //<sessionId,session>
    private final Map<Integer, ServerSession> serverSessionMap = new ConcurrentHashMap<>();
    //<channel,session>
    private final Map<Channel, ServerSession> channelServerSessionMap = new ConcurrentHashMap<>();
    //<clientId,Map<sessionId,session>>
    private final Map<String, Map<Integer, ServerSession>> clientIdServerSessionMap = new ConcurrentHashMap<>();

    private final Bootstrap bootstrap = new Bootstrap();
    private final ServerSessionLifecycle lifecycle;
    private final long sessionTimeout;

    ServerSessionManager(ServerSessionManagerBuilder builder) {
        lifecycle = builder.lifecycle;
        sessionTimeout = builder.sessionTimeout;
        bootstrap.group(builder.group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new SimpleHandler());
                    }
                });
        //定期检查超时session
        Thread.startVirtualThread(() -> {
            while (true) {
                try {
                    Thread.sleep(sessionTimeout);
                } catch (InterruptedException e) {
                    continue;
                }
                log.info("check session: serverSessionMap {} channelServerSessionMap {} ", serverSessionMap.size(), channelServerSessionMap.size());
                HashSet<ServerSession> needClosedSessions = new HashSet<>();
                serverSessionMap.forEach((id, session) -> {
                    if (session.isTimeOut()) {
                        needClosedSessions.add(session);
                    } else if (session.isNeedCheckActive()) {
                        lifecycle.checkActive(session);
                    }
                });
                channelServerSessionMap.forEach((c, session) -> {
                    if (session.isTimeOut()) {
                        needClosedSessions.add(session);
                    }
                });
                for (ServerSession session : needClosedSessions) {
                    channelServerSessionMap.remove(session.getChannel());
                    disposeServerSession(session, "超时关闭");
                }
            }

        });
    }

    public ServerSession createServerSession(String commandParam) {
        String[] commandParams = commandParam.split(commandParamJoinFlag);
        String clientId = commandParams[0];
        String host = commandParams[1];
        int port = Integer.parseInt(commandParams[3]);
        return createServerSession(clientId, host, port);
    }

    public ServerSession createServerSession(String clientId, String host, int port) {
        int sessionId = sessionIdBuilder.addAndGet(1);
        log.info("new ServerSession {} {}:{} from {}", sessionId, host, port, clientId);
        Channel channel = bootstrap.connect(host, port).channel();
        ServerSession serverSession = new ServerSession(sessionTimeout, sessionId, clientId, lifecycle, channel);
        channelServerSessionMap.put(channel, serverSession);
        serverSessionMap.put(sessionId, serverSession);
        Map<Integer, ServerSession> clientSessions = clientIdServerSessionMap.computeIfAbsent(clientId, (id) -> new ConcurrentHashMap<>());
        clientSessions.put(sessionId, serverSession);
        return serverSession;
    }

    public void disposeServerSession(ServerSession serverSession, String type) {
        try {
            serverSession.close();
        } catch (Exception e) {
            log.warn("close session error", e);
        }
        log.info("serverSession {} close,type [{}]", serverSession.getSessionId(), type);
        if (null != serverSessionMap.remove(serverSession.getSessionId())) {
            lifecycle.closed(serverSession);
        }
        clientIdServerSessionMap.get(serverSession.getClientId()).remove(serverSession.getSessionId());
    }

    private class SimpleHandler extends ChannelInboundHandlerAdapter {

        private ServerSession getServeSession(ChannelHandlerContext ctx) {
            return channelServerSessionMap.get(ctx.channel());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ServerSession session = getServeSession(ctx);
            log.info("serverSession channelActive {}", session);
            if (session != null) {
                lifecycle.created(session);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            ServerSession session = channelServerSessionMap.remove(ctx.channel());
            if (null == session) {
                log.warn("channelInactive session不存在");
                return;
            }
            log.info("serverSession channelInactive {}", session);
            disposeServerSession(session, "channelInactive");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            byte[] bytes = BytesUtil.byteBuf2bytes((ByteBuf) msg);
            ServerSession session = getServeSession(ctx);
            if (null == session) {
                log.warn("channelRead session不存在");
                return;
            }
            session.activeSession();
            log.debug("serverSession {} 收到目标端口字节 {}", session, bytes.length);
            lifecycle.sendToUser(session, bytes);
            lifecycle.afterSendToTarget(session, bytes);

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ServerSession session = getServeSession(ctx);
            if (null == session) {
                log.warn("channelRead session不存在");
                return;
            }
            log.warn("{} exceptionCaught", session, cause);
            disposeServerSession(session, "exceptionCaught");
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
        }
    }

}