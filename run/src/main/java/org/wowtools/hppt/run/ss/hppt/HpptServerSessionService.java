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
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

/**
 * @author liuyu
 * @date 2024/4/9
 */
@Slf4j
public class HpptServerSessionService extends ServerSessionService<ChannelHandlerContext> {
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public HpptServerSessionService(SsConfig ssConfig) throws Exception {
        super(ssConfig);
    }

    @Override
    public void init(SsConfig ssConfig) {
        bossGroup = new NioEventLoopGroup(ssConfig.hppt.bossGroupNum);
        workerGroup = new NioEventLoopGroup(ssConfig.hppt.workerGroupNum);

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        int len = ssConfig.hppt.lengthFieldLength;
                        int maxFrameLength = (int) (Math.pow(256, len) - 1);
                        if (maxFrameLength <= 0) {
                            maxFrameLength = Integer.MAX_VALUE;
                        }
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, len, 0, len));
                        pipeline.addLast(new LengthFieldPrepender(len));
                        pipeline.addLast(new MessageHandler());
                    }
                });

        serverBootstrap.bind(ssConfig.port);
    }

    @Override
    protected void sendBytesToClient(ChannelHandlerContext ctx, byte[] bytes) {
        if (!BytesUtil.writeToChannelHandlerContext(ctx, bytes)) {
            exit();
        }
    }

    @Override
    protected void closeCtx(ChannelHandlerContext ctx) throws Exception {
        ctx.close();
    }

    @Override
    public void doClose() {
        try {
            bossGroup.shutdownGracefully();
        } catch (Exception e) {
            log.warn("bossGroup.shutdownGracefully() err", e);
        }
        try {
            workerGroup.shutdownGracefully();
        } catch (Exception e) {
            log.warn("workerGroup.shutdownGracefully() err", e);
        }
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
            super.exceptionCaught(ctx, cause);
            removeCtx(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            removeCtx(ctx);
        }
    }
}
