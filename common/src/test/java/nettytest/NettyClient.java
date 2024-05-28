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
                            int maxFrameLength = (int) (Math.pow(256, len) - 1);
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, len, 0, len));
                            pipeline.addLast(new LengthFieldPrepender(len));
                            pipeline.addLast(new MessageHandler());
                        }
                    });

            bootstrap.connect("localhost", 20871).sync().channel().closeFuture().sync();
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
            ByteBuf message1 = Unpooled.wrappedBuffer("hello1".getBytes());
            ctx.writeAndFlush(message1).addListener((f) -> {
                System.out.println(f);
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}

