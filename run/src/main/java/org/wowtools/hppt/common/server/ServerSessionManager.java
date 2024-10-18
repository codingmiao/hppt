package org.wowtools.hppt.common.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.NettyObjectBuilder;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyu
 * @date 2023/11/18
 */
@Slf4j
public class ServerSessionManager implements AutoCloseable {


    private volatile boolean running = true;
    private final AtomicInteger sessionIdBuilder = new AtomicInteger();

    //<sessionId,session>
    private final Map<Integer, ServerSession> serverSessionMap = new ConcurrentHashMap<>();
    //<channel,session>
    private final Map<Channel, ServerSession> channelServerSessionMap = new ConcurrentHashMap<>();
    //<clientId,Map<sessionId,session>>
    private final Map<String, Map<Integer, ServerSession>> clientIdServerSessionMap = new ConcurrentHashMap<>();

    private final Bootstrap bootstrap = new Bootstrap();//TODO 这里改到每个session里，减少eventloop数，实现阻塞等待，以此避免接收目标端数据过快

    private final ServerSessionLifecycle lifecycle;
    private final long sessionTimeout;

    ServerSessionManager(ServerSessionManagerBuilder builder) {
        lifecycle = builder.lifecycle;
        sessionTimeout = builder.sessionTimeout;
        bootstrap.group(builder.group)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) sessionTimeout)
                .channel(NettyObjectBuilder.getSocketChannelClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new SimpleHandler());
                    }
                });
        //定期检查超时session
        Thread.startVirtualThread(() -> {
            while (running) {
                try {
                    Thread.sleep(sessionTimeout);
                } catch (InterruptedException e) {
                    continue;
                }
                if (!running) {
                    return;
                }
                log.info("check session: serverSessionMap {} channelServerSessionMap {} ", serverSessionMap.size(), channelServerSessionMap.size());
                HashSet<ServerSession> needClosedSessions = new HashSet<>();
                serverSessionMap.forEach((id, session) -> {
                    if (session.isTimeOut()) {
                        needClosedSessions.add(session);
                    } else if (session.isNeedCheckActive()) {
                        //发送校验CheckActive命令
                        session.getClient().addCommand((String.valueOf(Constant.ScCommands.CheckSessionActive) + session.getSessionId()));
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

    @Override
    public void close() {
        running = false;
    }

    //新建一个session并返回sessionId
    public int createServerSession(LoginClientService.Client client, String host, int port, long timeoutMillis) {
        int sessionId = sessionIdBuilder.addAndGet(1);
        Map<Integer, ServerSession> clientSessions = clientIdServerSessionMap.computeIfAbsent(client.clientId, (id) -> new ConcurrentHashMap<>());

        class ChannelRes {
            Throwable cause;
            Channel channel;
        }
        CompletableFuture<ChannelRes> resFuture = new CompletableFuture<>();

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        // 添加超时处理
        future.addListener((ChannelFutureListener) f -> {
            ChannelRes res = new ChannelRes();
            try {
                if (!f.isSuccess()) {
                    try {
                        future.cancel(true); // 取消未完成的连接尝试
                    } catch (Exception e) {
                        log.warn("future.channel, sessionId {}", sessionId, e);
                    }
                    res.cause = f.cause();
                } else {
                    // 设置连接超时
                    boolean isConnected = future.awaitUninterruptibly(timeoutMillis, TimeUnit.MILLISECONDS);
                    if (!isConnected) {
                        try {
                            future.cancel(true); // 取消未完成的连接尝试
                        } catch (Exception e) {
                            log.warn("future.channel, sessionId {}", sessionId, e);
                        }
                    } else {
                        res.channel = future.channel();
                        log.info("new ServerSession {} {}:{} from {}", sessionId, host, port, client.clientId);
                    }
                }
            } catch (Exception e) {
                res.cause = e;
            } finally {
                resFuture.complete(res);
            }

        });

        ChannelRes res;
        try {
            res = resFuture.get(timeoutMillis, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("获取ChannelRes异常 sessionId {}", sessionId, e);
            return sessionId;
        }
        if (null == res) {
            log.warn("获取channel超时 sessionId {}", sessionId);
            return sessionId;
        }
        if (null != res.cause) {
            log.warn("获取channel异常 sessionId {}", sessionId, res.cause);
            return sessionId;
        }

        Channel channel = res.channel;
        if (null == channel) {
            log.warn("获取channel为空 sessionId {}", sessionId);
            return sessionId;
        }
        ServerSession serverSession = new ServerSession(sessionTimeout, sessionId, client, lifecycle, channel);
        channelServerSessionMap.put(channel, serverSession);
        serverSessionMap.put(sessionId, serverSession);
        clientSessions.put(sessionId, serverSession);
        return sessionId;
    }

    public void disposeServerSession(ServerSession serverSession, String type) {
        try {
            serverSession.close();
        } catch (Exception e) {
            log.warn("close session error", e);
        }
        log.info("serverSession {} close,type [{}]", serverSession.getSessionId(), type);
        if (null != serverSessionMap.remove(serverSession.getSessionId())) {
            serverSession.getClient().addCommand(String.valueOf(Constant.ScCommands.CloseSession) + serverSession.getSessionId());
            lifecycle.closed(serverSession);
        }
        clientIdServerSessionMap.get(serverSession.getClient().clientId).remove(serverSession.getSessionId());
    }

    public Map<Integer, ServerSession> getServerSessionMapByClientId(String clientId) {
        return clientIdServerSessionMap.get(clientId);
    }

    public ServerSession getServerSessionBySessionId(int sessionId) {
        return serverSessionMap.get(sessionId);
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
            ByteBuf buf = (ByteBuf) msg;
            byte[] bytes = null;
            try {
                bytes = BytesUtil.byteBuf2bytes(buf);
            }finally {
                //channelRead方法需要手动释放ByteBuf
                buf.release();
            }
            ServerSession session = getServeSession(ctx);
            if (null != session) {
                session.activeSession();
                log.debug("serverSession {} 收到目标端口字节 {}", session, bytes.length);
                lifecycle.sendToClientBuffer(session, bytes, session.getClient());
                lifecycle.afterSendToTarget(session, bytes);
            } else {
                log.warn("channelRead session不存在");
            }
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
