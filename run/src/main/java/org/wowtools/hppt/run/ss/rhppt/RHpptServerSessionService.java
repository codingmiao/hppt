package org.wowtools.hppt.run.ss.rhppt;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.NettyObjectBuilder;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

/**
 * @author liuyu
 * @date 2024/4/15
 */
@Slf4j
public class RHpptServerSessionService extends ServerSessionService<ChannelHandlerContext> {

    private EventLoopGroup group;

    public RHpptServerSessionService(SsConfig ssConfig) {
        super(ssConfig);
    }

    @Override
    protected void init(SsConfig ssConfig) throws Exception {
        group = NettyObjectBuilder.buildEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NettyObjectBuilder.getSocketChannelClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            int len = ssConfig.rhppt.lengthFieldLength;
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

            bootstrap.connect(ssConfig.rhppt.host, ssConfig.rhppt.port).sync();
        } catch (Exception e) {
            group.shutdownGracefully();
            throw new RuntimeException(e);
        }
    }

    private class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            // 处理接收到的消息
            byte[] bytes = BytesUtil.byteBuf2bytes(msg);
            receiveClientBytes(ctx, bytes);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            exit("netty err:" + cause.getMessage());
        }
    }

    @Override
    protected void sendBytesToClient(ChannelHandlerContext ctx, byte[] bytes) {
        Throwable e = BytesUtil.writeToChannelHandlerContext(ctx, bytes);
        if (null != e) {
            log.warn("sendBytesToClient err", e);
            ctx.close();
        }
    }

    @Override
    protected void closeCtx(ChannelHandlerContext ctx) throws Exception {
        ctx.close();
        exit("netty closeCtx");
    }

    @Override
    public void onExit() throws Exception {
        Object c = group.shutdownGracefully().sync().get();
        log.info("onExit end {}", c);
    }
}
