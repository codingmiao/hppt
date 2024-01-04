package org.wowtools.hppt.common.client;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.net.InetSocketAddress;
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
    public final Map<Integer, ClientSession> clientSessionMap = new ConcurrentHashMap<>();

    private final ServerBootstrap serverBootstrap = new ServerBootstrap();
    private final ClientSessionLifecycle lifecycle;

    ClientSessionManager(ClientSessionManagerBuilder builder) {
        lifecycle = builder.lifecycle;
        serverBootstrap.group(builder.bossGroup, builder.workerGroup)
                .channel(NioServerSocketChannel.class)
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

    public void bindPort(int port) {
        serverBootstrap.bind(port).channel().closeFuture();
        log.debug("bindPort {} success", port);
    }

    public void disposeClientSession(ClientSession clientSession, String type) {
        clientSession.close();
        log.info("ClientSession {} close,type [{}]", clientSession.getSessionId(), type);
        if (null != clientSessionMap.remove(clientSession.getSessionId())) {
            lifecycle.closed(clientSession);
        }
    }


    private final Map<ChannelHandlerContext, ClientSession> clientSessionMapByCtx = new ConcurrentHashMap<>();

    private final class SimpleHandler extends ByteToMessageDecoder {

        @Override
        public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
            log.debug("client channelActive {}", channelHandlerContext.hashCode());
            super.channelActive(channelHandlerContext);
            Channel channel = channelHandlerContext.channel();
            InetSocketAddress localAddress = (InetSocketAddress) channel.localAddress();
            int localPort = localAddress.getPort();
            //用户发起新连接 新建一个ClientSession
            int sessionId = lifecycle.connected(localPort, channelHandlerContext);
            ClientSession clientSession = new ClientSession(sessionId, channelHandlerContext, lifecycle);
            log.debug("ClientSession {} 初始化完成 {}", clientSession.getSessionId(), channelHandlerContext.hashCode());
            clientSessionMapByCtx.put(channelHandlerContext, clientSession);
            lifecycle.created(clientSession);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.debug("client channelInactive {}", ctx.hashCode());
            super.channelInactive(ctx);
            ClientSession clientSession = clientSessionMapByCtx.get(ctx);
            if (null != clientSession) {
                disposeClientSession(clientSession, "client channelInactive");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("client exceptionCaught {}", ctx.hashCode(), cause);
            ClientSession clientSession = clientSessionMapByCtx.get(ctx);
            if (null != clientSession) {
                disposeClientSession(clientSession, "client exceptionCaught");
            }
        }

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
            byte[] bytes = BytesUtil.byteBuf2bytes(byteBuf);
            ClientSession clientSession = clientSessionMapByCtx.get(channelHandlerContext);
            if (null != clientSession) {
                //触发数据回调事件 转发数据到真实端口
                log.debug("ClientSession {} 收到用户端字节 {}", clientSession.getSessionId(), bytes.length);
                lifecycle.sendToTarget(clientSession, bytes);
                lifecycle.afterSendToTarget(clientSession, bytes);
            }
        }
    }
}
