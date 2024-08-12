package test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class TCPForwardingServer {

    Bootstrap bootstrap;
    ServerBootstrap server;

    NioEventLoopGroup bossGroup;
    NioEventLoopGroup workGroup;

    public static void main(String[] args) {
        TCPForwardingServer TCPForwardingServer = new TCPForwardingServer();
        TCPForwardingServer.init();
    }

    class DataHandler extends ChannelInboundHandlerAdapter {

        private Channel channel;

        public DataHandler(Channel channel) {
            this.channel = channel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf readBuffer = (ByteBuf) msg;
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
        }

    }

    void init() {
        this.bossGroup = new NioEventLoopGroup();
        this.workGroup = new NioEventLoopGroup();
        this.server = new ServerBootstrap();
        this.bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(bossGroup);
        this.server.group(bossGroup, workGroup);


        server.channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>(
                ) {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast("serverHandler", new DataHandler(getClientChannel(socketChannel)));
                    }
                }).option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_RCVBUF, 16 * 1024);

        server.bind(11001).syncUninterruptibly().addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                System.out.println("forward server start success");
            } else {
                System.out.println("forward server start failed");
            }
        });
    }

    private Channel getClientChannel(SocketChannel ch) throws InterruptedException {
        this.bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
                socketChannel.pipeline().addLast("clientHandler", new DataHandler(ch));
            }
        });
        // 目标地址
        ChannelFuture sync = bootstrap.connect("localhost", 7779).sync();
        return sync.channel();
    }

}
