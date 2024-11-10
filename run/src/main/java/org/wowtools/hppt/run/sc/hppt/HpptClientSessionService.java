package org.wowtools.hppt.run.sc.hppt;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.NettyObjectBuilder;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

/**
 * @author liuyu
 * @date 2024/3/12
 */
@Slf4j
public class HpptClientSessionService extends ClientSessionService {


    private ChannelHandlerContext _ctx;

    public HpptClientSessionService(ScConfig config) throws Exception {
        super(config);
    }

    @Override
    public void connectToServer(ScConfig config, Cb cb) {
        Thread.startVirtualThread(() -> {
            EventLoopGroup workerGroup = NettyObjectBuilder.buildEventLoopGroup(config.hppt.workerGroupNum);
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(workerGroup)
                        .channel(NettyObjectBuilder.getSocketChannelClass())
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                int len = config.hppt.lengthFieldLength;
                                int maxFrameLength = (int) (Math.pow(256, len) - 1);
                                if (maxFrameLength <= 0) {
                                    maxFrameLength = Integer.MAX_VALUE;
                                }
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, len, 0, len));
                                pipeline.addLast(new LengthFieldPrepender(len));
                                pipeline.addLast(new MessageHandler(cb));
                            }
                        });
                bootstrap.connect(config.hppt.host, config.hppt.port).sync().channel().closeFuture().sync();
            } catch (Exception e) {
                log.warn("netty err", e);
                exit();
            } finally {
                try {
                    workerGroup.shutdownGracefully();
                } catch (Exception e) {
                    log.warn("workerGroup.shutdownGracefully err", e);
                }
            }
        });
    }

    @Override
    public void sendBytesToServer(byte[] bytes) {
        Throwable e = BytesUtil.writeToChannelHandlerContext(_ctx, bytes);
        if (null != e) {
            log.warn("sendBytesToServer err", e);
            exit();
        }
    }

    private class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final Cb cb;

        public MessageHandler(Cb cb) {
            this.cb = cb;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            // 处理接收到的消息
            byte[] bytes = BytesUtil.byteBuf2bytes(msg);
            receiveServerBytes(bytes);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            _ctx = ctx;
            cb.end(null);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            exit();
        }
    }

    @Override
    protected void doClose() throws Exception {
        try {
            _ctx.close();
        } catch (Exception e) {

        }
        try {
            _ctx.channel().close();
        } catch (Exception e) {

        }
    }
}
