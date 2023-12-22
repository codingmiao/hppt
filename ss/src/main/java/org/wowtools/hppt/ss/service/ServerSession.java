package org.wowtools.hppt.ss.service;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.RoughTimeUtil;
import org.wowtools.hppt.ss.StartSs;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ServerSession {
    private final SimpleClientHandler simpleClientHandler;
    private final String clientId;
    private final int sessionId;

    private volatile boolean running = true;

    private final ServerSession serverSession = this;

    //真实端口发回来的消息，存在队列里等待回发客户端
    private final BlockingQueue<byte[]> serverSessionSendQueue = new ArrayBlockingQueue<>(StartSs.config.messageQueueSize);

    //在何时检查超时
    private long checkOutTime;
    //在何时超时
    private long outTime;

    public ServerSession(String host, int port, String clientId, int sessionId) {
        activeSession();
        this.clientId = clientId;
        this.sessionId = sessionId;
        simpleClientHandler = new SimpleClientHandler(sessionId);
        Thread.startVirtualThread(() -> {
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(simpleClientHandler);
                            }
                        });

                ChannelFuture future = bootstrap.connect(host, port).sync();
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                group.shutdownGracefully();
                log.debug("ServerSession group.shutdownGracefully();");
            }
        });


    }

    private class SimpleClientHandler extends ChannelInboundHandlerAdapter {
        private final int sessionId;

        private final BlockingQueue<byte[]> sendQueue = new ArrayBlockingQueue<>(StartSs.config.messageQueueSize);

        private Thread sendThread;
        private ChannelHandlerContext ctx;

        public SimpleClientHandler(int sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            //连接建立 从队列中取数据发送到目标端口
            if (null != sendThread) {
                throw new RuntimeException("逻辑错误 sendThread 重复构建");
            }
            sendThread = Thread.startVirtualThread(() -> {
                try {
                    while (running) {
                        byte[] bytes;
                        try {
                            bytes = sendQueue.poll(1, TimeUnit.MINUTES);
                        } catch (InterruptedException e) {
                            log.debug("sendThread {} Interrupted", sessionId);
                            break;
                        }
                        if (bytes == null) {
                            continue;
                        }
                        ByteBuf message = Unpooled.copiedBuffer(bytes);
                        ctx.writeAndFlush(message);
                        log.debug("serverSession {} 向目标端口发送字节 {}", sessionId, bytes.length);
                    }
                } catch (Exception e) {
                    ServerSessionManager.disposeServerSession(serverSession, "sendThread error");
                }
                log.debug("sendThread {} end", sessionId);
            });
            this.ctx = ctx;
            log.info("serverSession channelActive {}", sessionId);

        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("serverSession channelInactive {}", sessionId);
            super.channelInactive(ctx);
            ServerSessionManager.disposeServerSession(serverSession, "channelInactive");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf byteBuf = (ByteBuf) msg;
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            log.debug("serverSession {} 收到目标端口字节 {}", sessionId, bytes.length);
            try {
                serverSessionSendQueue.add(bytes);
            } catch (Exception e) {
                log.warn("serverSessionSendQueue 缓冲区已满，强制关闭session", e);
                ServerSessionManager.disposeServerSession(serverSession, "serverSessionSendQueue 缓冲区已满");
            }
            activeSession();

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("{} exceptionCaught", sessionId, cause);
            ServerSessionManager.disposeServerSession(serverSession, "exceptionCaught");
        }

        public void close() {
            try {
                sendThread.interrupt();
            } catch (Exception e) {
                log.warn("sendThread.interrupt() err {}", e.getMessage());
            }
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    /**
     * 向真实端口发送字节
     *
     * @param bytes bytes
     */
    public void putBytes(byte[] bytes) {
        activeSession();
        try {
            simpleClientHandler.sendQueue.add(bytes);
            log.debug("ServerSession {} 收到 Client发来的字节 {}", sessionId, bytes.length);
        } catch (Exception e) {
            log.warn("ServerSession {} simpleClientHandler.sendQueue.add(bytes) 异常，强制关闭", sessionId, e);
            ServerSessionManager.disposeServerSession(serverSession, "simpleClientHandler.sendQueue 已满");
        }
    }

    /**
     * 保持会话活跃
     */
    public void activeSession() {
        checkOutTime = RoughTimeUtil.getTimestamp() + StartSs.config.sessionTimeout;
        outTime = checkOutTime + StartSs.config.sessionTimeout;
    }

    public boolean isNeedCheckActive() {
        return checkOutTime <= RoughTimeUtil.getTimestamp();
    }

    public boolean isTimeOut() {
        return outTime <= RoughTimeUtil.getTimestamp();
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getClientId() {
        return clientId;
    }

    public void close() {
        running = false;
        simpleClientHandler.close();
    }

    /**
     * 取出需要发送回客户端的SessionBytes
     *
     * @return 无数据则返回null
     */
    public byte[] fetchSendSessionBytes() {
        List<byte[]> list = new LinkedList<>();
        serverSessionSendQueue.drainTo(list);
        if (list.isEmpty()) {
            return null;
        }
        return BytesUtil.merge(list);
    }

}
