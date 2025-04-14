package org.wowtools.hppt.run.ss.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.NettyObjectBuilder;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

/**
 * @author liuyu
 * @date 2024/2/7
 */
@Slf4j
public class WebsocketServerSessionService extends ServerSessionService<ChannelHandlerContext> {
    private EventLoopGroup boss;
    private EventLoopGroup worker;

    public WebsocketServerSessionService(SsConfig ssConfig) throws Exception {
        super(ssConfig);
    }

    @Override
    protected void init(SsConfig ssConfig) throws Exception {
        log.info("*********");
        boss = NettyObjectBuilder.buildEventLoopGroup(ssConfig.websocket.bossGroupNum);
        worker = NettyObjectBuilder.buildEventLoopGroup(ssConfig.websocket.workerGroupNum);

        ServerBootstrap serverBootstrap;

        serverBootstrap = new ServerBootstrap();
        serverBootstrap
                .group(boss, worker)
                .channel(NettyObjectBuilder.getServerSocketChannelClass())
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                                  @Override
                                  protected void initChannel(NioSocketChannel ch) throws Exception {
                                      ChannelPipeline pipeline = ch.pipeline();
                                      pipeline.addLast(new HttpServerCodec())
                                              .addLast(new ChunkedWriteHandler())
                                              .addLast(new HttpObjectAggregator(1024 * 1024 * 10))
                                              .addLast(new WebSocketServerProtocolHandler("/", null, false, 1024 * 1024 * 50, false, true, 10000L))
                                              .addLast(new MyHandler());
                                  }
                              }
                );
        serverBootstrap.bind(ssConfig.port).sync();
    }


    @ChannelHandler.Sharable
    private final class MyHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            removeCtx(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            removeCtx(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
            byte[] bytes = new byte[msg.content().readableBytes()];
            msg.content().readBytes(bytes);
            receiveClientBytes(ctx, bytes);
        }
    }

    @Override
    protected void sendBytesToClient(ChannelHandlerContext ctx, byte[] bytes) {
        BinaryWebSocketFrame f = new BinaryWebSocketFrame(BytesUtil.bytes2byteBuf(ctx, bytes));
        ctx.channel().writeAndFlush(f);
    }

    @Override
    protected void closeCtx(ChannelHandlerContext channelHandlerContext) {
        channelHandlerContext.close();
    }

    @Override
    public void onExit() {
        try {
            boss.shutdownGracefully();
        } catch (Exception e) {
            log.warn("boss.shutdownGracefully() err", e);
        }
        try {
            worker.shutdownGracefully();
        } catch (Exception e) {
            log.warn("worker.shutdownGracefully() err", e);
        }
    }
}
