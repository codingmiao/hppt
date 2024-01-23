package nettytest;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

public class NettyClient {

    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            int len = 2;
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, len, 0, len));
                            pipeline.addLast(new LengthFieldPrepender(len));
                            pipeline.addLast(new MessageHandler());
                        }
                    });

            bootstrap.connect("localhost", 8888).sync().channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    private static class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            // 处理接收到的消息
            byte[] content = new byte[msg.readableBytes()];
            msg.readBytes(content);
            System.out.println("Received message: " + new String(content));
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // 发送消息到服务器
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5000; i++) {
                sb.append("hello").append(i).append(" ");
            }
            ByteBuf message = Unpooled.wrappedBuffer(sb.toString().getBytes());
            ctx.writeAndFlush(message);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}

