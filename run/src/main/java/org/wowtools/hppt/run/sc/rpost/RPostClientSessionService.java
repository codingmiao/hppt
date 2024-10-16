package org.wowtools.hppt.run.sc.rpost;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.NettyChannelTypeChecker;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RPostClientSessionService extends ClientSessionService {

    private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public RPostClientSessionService(ScConfig config) throws Exception {
        super(config);
    }

    @Override
    public void connectToServer(ScConfig config, Cb cb) {
        bossGroup = NettyChannelTypeChecker.buildVirtualThreadEventLoopGroup(config.rpost.bossGroupNum);
        workerGroup = NettyChannelTypeChecker.buildVirtualThreadEventLoopGroup(config.rpost.workerGroupNum);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(104857600)); // 100 MB
                            ch.pipeline().addLast(new SendHandler());
                            ch.pipeline().addLast(new ReceiveHandler());
                        }
                    });
            int port = config.rpost.port;
            ChannelFuture f = bootstrap.bind(port).sync();
            channel = f.channel();
            log.info("Netty服务端启动完成，端口 {}", port);
            cb.end();
        } catch (Exception e) {
            log.warn("start err", e);
            exit();
        }
    }

    @Override
    public void sendBytesToServer(byte[] bytes) {
        sendQueue.add(bytes);
    }

    @Override
    protected void doClose() {
        if (channel.isOpen()) {
            try {
                channel.close();
            } catch (Exception e) {
                log.warn("channel.shutdownGracefully() err", e);
            }
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

    private class SendHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            if ("/s".equals(req.uri())) {
                try {
                    sendResponse(ctx);
                } catch (Exception e) {
                    log.error("SendHandler error", e);
                }
            } else {
                ctx.fireChannelRead(req.retain());
            }
        }

        private void sendResponse(ChannelHandlerContext ctx) throws Exception {
            byte[] rBytes = sendQueue.poll(config.rpost.waitResponseTime, TimeUnit.MILLISECONDS);
            if (rBytes != null) {
                List<byte[]> bytesList = new LinkedList<>();
                bytesList.add(rBytes);
                sendQueue.drainTo(bytesList);
                rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                log.debug("向客户端发送字节 {}", rBytes.length);

                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.content().writeBytes(rBytes);
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, rBytes.length);
                ctx.writeAndFlush(response);
            } else {
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT));
            }
        }
    }

    private class ReceiveHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            if ("/r".equals(req.uri())) {
                try {
                    receiveBytes(ctx, req);
                } catch (Exception e) {
                    log.error("ReceiveHandler error", e);
                }
            } else {
                ctx.fireChannelRead(req.retain());
            }
        }

        private void receiveBytes(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            byte[] bytes = new byte[req.content().readableBytes()];
            req.content().readBytes(bytes);

            List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(bytes);
            for (byte[] sub : bytesList) {
                try {
                    receiveServerBytes(sub);
                } catch (Exception e) {
                    log.warn("接收字节异常", e);
                    exit();
                }
            }

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response);
        }
    }
}
