package org.wowtools.hppt.common.client;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.DebugConfig;
import org.wowtools.hppt.common.util.NettyObjectBuilder;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientSession管理器
 *
 * @author liuyu
 * @date 2023/11/18
 */
@Slf4j
public class ClientSessionManager {
    private final Map<Integer, ClientSession> clientSessionMap = new ConcurrentHashMap<>();

    private final ServerBootstrap serverBootstrap = new ServerBootstrap();
    private final ClientSessionLifecycle lifecycle;
    private final ClientBytesSender clientBytesSender;

    private final List<Channel> channels = new LinkedList<>();
    private final ClientSessionManagerBuilder builder;


    ClientSessionManager(ClientSessionManagerBuilder builder) {
        this.builder = builder;
        lifecycle = builder.lifecycle;
        if (null == lifecycle) {
            throw new RuntimeException("lifecycle不能为空");
        }
        clientBytesSender = builder.clientBytesSender;
        if (null == clientBytesSender) {
            throw new RuntimeException("clientBytesSender不能为空");
        }
        serverBootstrap.group(builder.bossGroup, builder.workerGroup)
                .channel(NettyObjectBuilder.getServerSocketChannelClass())
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_SNDBUF, builder.bufferSize)
                .childOption(ChannelOption.SO_RCVBUF, builder.bufferSize)
//                        .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
//                                ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(new SimpleHandler());
                        ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(builder.bufferSize));
                        ch.config().setSendBufferSize(builder.bufferSize);
                    }
                });
    }

    public boolean bindPort(int port) {
        synchronized (channels) {
            try {
                Channel channel = serverBootstrap.bind(port).sync().channel();
                channel.closeFuture();
                channels.add(channel);
                log.debug("bindPort {} success", port);
                return true;
            } catch (Exception e) {
                log.error("Failed to bind port {}.", port, e);
                return false;
            }
        }

    }

    public void disposeClientSession(ClientSession clientSession, String type) {
        clientSession.close();
        log.info("ClientSession {} close,type [{}]", clientSession.getSessionId(), type);
        if (null != clientSessionMap.remove(clientSession.getSessionId())) {
            lifecycle.closed(clientSession);
        }
    }

    public ClientSession getClientSessionBySessionId(int sessionId) {
        return clientSessionMap.get(sessionId);
    }

    public int getSessionNum() {
        return clientSessionMap.size();
    }


    public void close() {
        synchronized (channels) {
            for (Channel channel : channels) {
                channel.close();
            }
        }
        builder.bossGroup.shutdownGracefully();
        builder.workerGroup.shutdownGracefully();
    }

    private final Map<ChannelHandlerContext, ClientSession> clientSessionMapByCtx = new ConcurrentHashMap<>();

    private final class SimpleHandler extends ByteToMessageDecoder {
        @Override
        public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
            super.channelActive(channelHandlerContext);
            Channel channel = channelHandlerContext.channel();
            InetSocketAddress localAddress = (InetSocketAddress) channel.localAddress();
            int localPort = localAddress.getPort();
            log.debug("client channelActive {}, port:{}", channelHandlerContext.hashCode(), localPort);
            //用户发起新连接 新建一个ClientSession
            ClientBytesSender.SessionIdCallBack cb = new ClientBytesSender.SessionIdCallBack(channelHandlerContext) {
                @Override
                public void cb(int sessionId) {
                    ClientSession clientSession = new ClientSession(sessionId, channelHandlerContext, lifecycle);
                    log.debug("ClientSession {} 初始化完成 {}", clientSession.getSessionId(), channelHandlerContext.hashCode());
                    clientSessionMapByCtx.put(channelHandlerContext, clientSession);
                    clientSessionMap.put(sessionId, clientSession);
                    lifecycle.created(clientSession);
                }
            };
            clientBytesSender.connected(localPort, channelHandlerContext, cb);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("client channelInactive {}", ctx.hashCode());
            super.channelInactive(ctx);
            ClientSession clientSession = clientSessionMapByCtx.get(ctx);
            if (null != clientSession) {
                disposeClientSession(clientSession, "client channelInactive");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.info("client exceptionCaught {}", ctx.hashCode(), cause);
            ClientSession clientSession = clientSessionMapByCtx.get(ctx);
            if (null != clientSession) {
                disposeClientSession(clientSession, "client exceptionCaught");
            }
        }

        @Override
        protected synchronized void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
            byte[] bytes = BytesUtil.byteBuf2bytes(byteBuf);
            ClientSession clientSession = null;
            for (int i = 0; i < 1000; i++) {
                clientSession = clientSessionMapByCtx.get(channelHandlerContext);
                if (null != clientSession) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    continue;
                }
            }
            if (null != clientSession) {
//                if (log.isDebugEnabled()) {
//                    log.debug(new String(bytes, StandardCharsets.UTF_8));
//                }
                //触发数据回调事件 转发数据到真实端口
                if (log.isDebugEnabled()) {
                    log.debug("ClientSession {} 收到用户端字节 {}", clientSession.getSessionId(), bytes.length);
                }
                bytes = lifecycle.beforeSendToTarget(clientSession, bytes);
                if (null == bytes) {
                    return;
                }
                SessionBytes sessionBytes = new SessionBytes(clientSession.getSessionId(), bytes);
                if (DebugConfig.OpenSerialNumber) {
                    log.debug("用户端发来字节 >sessionBytes-SerialNumber {}", sessionBytes.getSerialNumber());
                }
                clientBytesSender.sendToTarget(clientSession, sessionBytes);
                lifecycle.afterSendToTarget(clientSession, bytes);
            } else {
                log.warn("找不到channelHandlerContext对应的client session");
            }
        }
    }


}
