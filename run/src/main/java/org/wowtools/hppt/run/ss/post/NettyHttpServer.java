package org.wowtools.hppt.run.ss.post;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.BytesList;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.NettyObjectBuilder;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
class NettyHttpServer {
    public static final byte[] emptyBytes = new byte[0];
    private final int port;
    private final PostServerSessionService postServerSessionService;
    private final SsConfig ssConfig;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public NettyHttpServer(int port, PostServerSessionService postServerSessionService, SsConfig ssConfig) {
        this.port = port;
        this.ssConfig = ssConfig;
        this.postServerSessionService = postServerSessionService;
    }

    public void start() throws InterruptedException {
        bossGroup = NettyObjectBuilder.buildEventLoopGroup(ssConfig.post.bossGroupNum);
        workerGroup = NettyObjectBuilder.buildEventLoopGroup(ssConfig.post.workerGroupNum);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.config().setOption(ChannelOption.TCP_NODELAY, true);
                            ch.config().setOption(ChannelOption.SO_KEEPALIVE, true);
                            ch.config().setOption(ChannelOption.SO_RCVBUF, 1048576); // 接收缓冲区大小
                            ch.config().setOption(ChannelOption.SO_SNDBUF, 1048576); // 发送缓冲区大小
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(104857600)); // 100 MB
                            ch.pipeline().addLast(new HttpRequestHandler(postServerSessionService, ssConfig));
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            log.info("服务端启动完成 端口 {}", port);
            channel = f.channel();
        } catch (Exception e) {
            log.warn("start err", e);
            stop();
        }
    }

    public void stop() {
        if (null != channel && channel.isOpen()) {
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

}

@Slf4j
// 处理请求
class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final class ChannelReader implements Runnable {
        private final ChannelHandlerContext ctx;
        private final FullHttpRequest req;

        public ChannelReader(ChannelHandlerContext ctx, FullHttpRequest req) {
            this.ctx = ctx;
            this.req = req;
        }

        @Override
        public void run() {
            try {
                HttpResponse response;
                try {
                    String uri = req.uri();
                    String[] arr = uri.split("\\?", 2);
                    String path = arr[0];
                    String cookie = arr[1].substring(2);
                    if (path.equals("/s") && req.method() == HttpMethod.POST) {
                        response = handleSend(req, cookie);
                    } else if (path.equals("/r") && req.method() == HttpMethod.POST) {
                        response = handleReply(req, cookie);
                    } else {
                        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                    }
                } catch (Exception e) {
                    log.warn("channelRead0 err ", e);
                    response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
                // Write the response
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            } finally {
                req.release();
            }
        }
    }

    private final PostServerSessionService postServerSessionService;
    private final long replyDelayTime;
    private final long waitResponseTime;

    public HttpRequestHandler(PostServerSessionService postServerSessionService, SsConfig ssConfig) {
        this.postServerSessionService = postServerSessionService;
        replyDelayTime = ssConfig.post.replyDelayTime;
        waitResponseTime = ssConfig.post.waitResponseTime;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        req.retain();
        Thread.startVirtualThread(new ChannelReader(ctx, req));
    }

    private FullHttpResponse handleSend(FullHttpRequest req, String cookie) {
        PostCtx ctx = postServerSessionService.ctxMap.computeIfAbsent(cookie, (c) -> new PostCtx(cookie));
        receive(ctx, req);
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    private void receive(PostCtx ctx, FullHttpRequest req) {
        // 获取请求体的内容
        ByteBuf content = req.content();
        byte[] bytes = BytesUtil.byteBuf2bytes(content);
        log.debug("收到请求body {}", bytes.length);

        BytesList bytesList = BytesUtil.pbBytes2BytesList(bytes);
        for (byte[] sub : bytesList.getBytes()) {
            postServerSessionService.receiveClientBytes(ctx, sub);
        }
    }

    private FullHttpResponse handleReply(FullHttpRequest req, String cookie) {
        PostCtx ctx = postServerSessionService.ctxMap.get(cookie);
        if (ctx != null) {
            return write(ctx);
        }
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
    }

    private FullHttpResponse write(PostCtx ctx) {
        if (replyDelayTime > 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        List<byte[]> bytesList = new LinkedList<>();
        byte[] rBytes = ctx.sendQueue.poll(waitResponseTime, TimeUnit.MILLISECONDS);
        if (null == rBytes) {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(NettyHttpServer.emptyBytes));
        }
        bytesList.add(rBytes);

        ctx.sendQueue.drainToList(bytesList);
        rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
        log.debug("向客户端发送字节 bytesList {} body {}", bytesList.size(), rBytes.length);
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(rBytes));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
