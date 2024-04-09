package org.wowtools.hppt.run.ss.hppt;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

/**
 * @author liuyu
 * @date 2024/4/9
 */
public class HpptServerSessionService extends ServerSessionService<ChannelHandlerContext> {
    public HpptServerSessionService(SsConfig ssConfig) throws Exception {
        super(ssConfig);
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
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

            serverBootstrap.bind(ssConfig.port).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    @Override
    protected void sendBytesToClient(ChannelHandlerContext ctx, byte[] bytes) {
        BytesUtil.writeToChannelHandlerContext(ctx, bytes);
    }

    @Override
    protected void closeCtx(ChannelHandlerContext ctx) throws Exception {
        ctx.close();
    }

    private class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            // 处理接收到的消息
            byte[] content = BytesUtil.byteBuf2bytes(msg);
            receiveClientBytes(ctx, content);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            removeCtx(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            removeCtx(ctx);
        }
    }
}
