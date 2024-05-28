package t1;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ClientPort {

    @FunctionalInterface
    public interface OnClientConn {
        void on(ClientPort clientPort, ChannelHandlerContext channelHandlerContext);
    }

    private final int localPort;
    private final OnClientConn onClientConn;

    private final ClientPort clientPort = this;

    public ClientPort(int localPort, OnClientConn onClientConn) throws Exception {
        this.localPort = localPort;
        this.onClientConn = onClientConn;

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        new Thread(() -> {
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
                                ch.pipeline().addLast(new SimpleHandler());
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
        }).start();
    }

    private final Map<ChannelHandlerContext, ClientSession> clientSessionMapByCtx = new ConcurrentHashMap<>();

    private class SimpleHandler extends ByteToMessageDecoder {

        @Override
        public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
            log.debug("client channelActive {}", channelHandlerContext.hashCode());
            super.channelActive(channelHandlerContext);
            //用户发起新连接 触发onClientConn事件
            onClientConn.on(clientPort, channelHandlerContext);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.debug("client channelInactive {}", ctx.hashCode());
            super.channelInactive(ctx);
            ClientSession clientSession = clientSessionMapByCtx.get(ctx);
            if (null != clientSession) {
                ClientSessionManager.disposeClientSession(clientSession, "client channelInactive");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.debug("client exceptionCaught {}", ctx.hashCode(), cause);
            ClientSession clientSession = clientSessionMapByCtx.get(ctx);
            if (null != clientSession) {
                ClientSessionManager.disposeClientSession(clientSession, "client exceptionCaught");
            }
        }

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
            int n = byteBuf.readableBytes();
            byte[] bytes = new byte[n];
            byteBuf.readBytes(bytes);
            ClientSession clientSession = clientSessionMapByCtx.get(channelHandlerContext);
            if (null != clientSession) {
                //触发数据回调事件
                log.debug("ClientSession {} 收到客户端发送字节数 {}", clientSession.getSessionId(), n);
                ClientSessionManager.clientSessionSendQueue.put(new SessionBytes(clientSession.getSessionId(), bytes));
            }
        }
    }

    public ClientSession createClientSession(int sessionId, ChannelHandlerContext channelHandlerContext) {
        ClientSession clientSession = new ClientSession(sessionId, channelHandlerContext);
        log.debug("ClientSession {} 初始化完成 {}", clientSession.getSessionId(), channelHandlerContext.hashCode());
        clientSessionMapByCtx.put(channelHandlerContext, clientSession);
        return clientSession;
    }
}
