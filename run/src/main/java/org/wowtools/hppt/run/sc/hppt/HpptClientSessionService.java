package org.wowtools.hppt.run.sc.hppt;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.nio.charset.StandardCharsets;

/**
 * @author liuyu
 * @date 2024/3/12
 */
@Slf4j
public class HpptClientSessionService extends ClientSessionService {

    private EventLoopGroup group;

    private ChannelHandlerContext _ctx;

    public HpptClientSessionService(ScConfig config) throws Exception {
        super(config);
    }

    @Override
    protected void connectToServer(ScConfig config, Cb cb) throws Exception {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            int len = 2;
                            int maxFrameLength = (int) (Math.pow(256, len) - 1);
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, len, 0, len));
                            pipeline.addLast(new LengthFieldPrepender(len));
                            pipeline.addLast(new MessageHandler(cb));
                        }
                    });

            bootstrap.connect(config.hppt.host, config.hppt.port).sync();
        } catch (Exception e){
            group.shutdownGracefully();
            throw e;
        }
    }

    @Override
    protected void sendBytesToServer(byte[] bytes) {
        BytesUtil.writeToChannelHandlerContext(_ctx, bytes);
    }

    private class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final Cb cb;

        public MessageHandler(Cb cb) {
            this.cb = cb;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            // 处理接收到的消息
            receiveServerBytes(BytesUtil.byteBuf2bytes(msg));
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            _ctx = ctx;
            cb.end();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            super.exceptionCaught(ctx, cause);
            ctx.close();
            group.shutdownGracefully();
            exit();
        }
    }

}
