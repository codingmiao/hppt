package org.wowtools.hppt.cs.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author liuyu
 * @date 2023/12/20
 */
@Slf4j
public class ServerPort {


    public ServerPort(String clientId, int localPort, String remoteHost, int remotePort) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Thread.startVirtualThread(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 128)
//                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
//                                ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                                ch.pipeline().addLast(new SimpleHandler(clientId, remoteHost, remotePort));
                            }
                        });

                // 绑定端口，启动服务器
                b.bind(localPort).sync().channel().closeFuture().sync();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        });
    }

    private static final class SimpleHandler extends ByteToMessageDecoder {
        private final String clientId;
        private final String remoteHost;
        private final int remotePort;

        public SimpleHandler(String clientId, String remoteHost, int remotePort) {
            this.clientId = clientId;
            this.remoteHost = remoteHost;
            this.remotePort = remotePort;
        }

        @Override
        public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
            log.debug("server channelActive {}", channelHandlerContext.hashCode());
            super.channelActive(channelHandlerContext);
            //用户发起新连接
            ServerSessionManager.createServerSession(channelHandlerContext, clientId, remoteHost, remotePort);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.debug("server channelInactive {}", ctx.hashCode());
            super.channelInactive(ctx);
            ServerSession session = ServerSessionManager.getServerSession(ctx);
            if (null != session) {
                ServerSessionManager.disposeServerSession(session, "用户端主动关闭");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("server exceptionCaught {}", ctx.hashCode(), cause);
            ServerSession session = ServerSessionManager.getServerSession(ctx);
            if (null != session) {
                ServerSessionManager.disposeServerSession(session, "server异常关闭");
            }
        }

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
            int n = byteBuf.readableBytes();
            byte[] bytes = new byte[n];
            byteBuf.readBytes(bytes);
            ServerSession session = ServerSessionManager.getServerSession(channelHandlerContext);
            if (null != session) {
                log.debug("ServerSession {} 收到用户端发送字节数 {}", session.getSessionId(), n);
                session.sendBytes(bytes);
            }

        }
    }
}
