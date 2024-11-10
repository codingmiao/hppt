package org.wowtools.hppt.run.ss.post;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class CombinedServer {

    private final PostServerSessionService postServerSessionService;
    private final long waitResponseTime;
    private final long replyDelayTime;

    public CombinedServer(PostServerSessionService postServerSessionService, long waitResponseTime, long replyDelayTime) {
        this.postServerSessionService = postServerSessionService;
        this.waitResponseTime = waitResponseTime;
        this.replyDelayTime = replyDelayTime;
    }

    public void start(int port) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new CombinedServerHandler(postServerSessionService, waitResponseTime, replyDelayTime));
                        }
                    });

            Channel ch = b.bind(port).sync().channel();

            log.info("Netty combined server started on port {}", port);

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        PostServerSessionService postServerSessionService = null;
        long waitResponseTime = 5000L;  // example value
        long replyDelayTime = 100L;  // example value
        new CombinedServer(postServerSessionService, waitResponseTime, replyDelayTime).start(8888);
    }

    @Slf4j
    private static class CombinedServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final PostServerSessionService postServerSessionService;
        private final long waitResponseTime;
        private final long replyDelayTime;

        public CombinedServerHandler(PostServerSessionService postServerSessionService, long waitResponseTime, long replyDelayTime) {
            this.postServerSessionService = postServerSessionService;
            this.waitResponseTime = waitResponseTime;
            this.replyDelayTime = replyDelayTime;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            String uri = req.uri();
            if (uri.startsWith("/s")) {
                handleSend(ctx, req);
            } else if (uri.startsWith("/r")) {
                handleReply(ctx, req);
            } else {
                sendResponse(ctx, req, HttpResponseStatus.NOT_FOUND);
            }
        }

        private void handleSend(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            if (req.method() == HttpMethod.POST) {
                String cookie = req.uri().substring(req.uri().indexOf("c=") + 2);
                PostCtx postCtx = postServerSessionService.ctxMap.computeIfAbsent(cookie, PostCtx::new);
                receive(postCtx, req);
                sendResponse(ctx, req, HttpResponseStatus.OK);
            } else {
                sendResponse(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED);
            }
        }

        private void handleReply(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            if (req.method() == HttpMethod.POST) {
                String cookie = req.uri().substring(req.uri().indexOf("c=") + 2);
                PostCtx postCtx = postServerSessionService.ctxMap.get(cookie);
                if (postCtx != null) {
                    write(postCtx, ctx, req);
                } else {
                    sendResponse(ctx, req, HttpResponseStatus.NO_CONTENT);
                }
            } else {
                sendResponse(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED);
            }
        }

        private void receive(PostCtx postCtx, FullHttpRequest req) {
            ByteBuf content = req.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.readBytes(bytes);

            log.debug("Received request body {}", bytes.length);

            Collection<byte[]> bytesList = BytesUtil.pbBytes2BytesList(bytes).getBytes();
            for (byte[] sub : bytesList) {
                postServerSessionService.receiveClientBytes(postCtx, sub);
            }
        }

        private void write(PostCtx postCtx, ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
            if (replyDelayTime > 0) {
                Thread.sleep(replyDelayTime);
            }

            List<byte[]> bytesList = new LinkedList<>();
            byte[] rBytes = postCtx.sendQueue.poll(waitResponseTime, TimeUnit.MILLISECONDS);
            if (rBytes == null) {
                sendResponse(ctx, req, HttpResponseStatus.NO_CONTENT);
                return;
            }
            bytesList.add(rBytes);

            postCtx.sendQueue.drainToList(bytesList);
            rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
            log.debug("Sending bytes to client bytesList {} body {}", bytesList.size(), rBytes.length);

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(rBytes));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, rBytes.length);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            if (HttpUtil.isKeepAlive(req)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Error handling request", cause);
            ctx.close();
        }
    }
}
