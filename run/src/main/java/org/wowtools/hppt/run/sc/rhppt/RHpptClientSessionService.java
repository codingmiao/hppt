package org.wowtools.hppt.run.sc.rhppt;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.NettyChannelTypeChecker;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

/**
 * @author liuyu
 * @date 2024/4/9
 */
@Slf4j
public class RHpptClientSessionService extends ClientSessionService {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private ChannelHandlerContext _ctx;

    public RHpptClientSessionService(ScConfig config) throws Exception {
        super(config);
    }

    @Override
    protected void connectToServer(ScConfig config, Cb cb) throws Exception {
        startServer(config, cb);
    }

    private void startServer(ScConfig config, Cb cb) throws Exception {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        bossGroup = NettyChannelTypeChecker.buildVirtualThreadEventLoopGroup();
        workerGroup = NettyChannelTypeChecker.buildVirtualThreadEventLoopGroup();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NettyChannelTypeChecker.getServerSocketChannelClass())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        int len = config.rhppt.lengthFieldLength;
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

        serverBootstrap.bind(config.rhppt.port).sync().channel().closeFuture().sync();
    }

    @Override
    protected void doClose() throws Exception {
        if (null != _ctx) {
            _ctx.close();
        }
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
        private final Cb cb;

        public MessageHandler(Cb cb) {
            this.cb = cb;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            _ctx = ctx;
            cb.end();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            // 处理接收到的消息
            byte[] content = BytesUtil.byteBuf2bytes(msg);
            try {
                receiveServerBytes(content);
            } catch (Exception e) {
                log.warn("接收消息异常", e);
                exit();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            exit();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            exit();
        }
    }

    @Override
    protected void sendBytesToServer(byte[] bytes) {
        if (!BytesUtil.writeToChannelHandlerContext(_ctx, bytes)) {
            exit();
        }
    }
}
