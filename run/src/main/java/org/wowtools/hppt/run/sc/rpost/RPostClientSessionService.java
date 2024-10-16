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
                            ch.pipeline().addLast(new PostHandler());
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

    private class PostHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        public static final byte[] emptyBytes = new byte[0];

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            HttpResponse response;
            String uri = req.uri();
            if ("/s".equals(uri)) {
                try {
                    response = sendResponse();
                } catch (Exception e) {
                    log.error("sendResponse error", e);
                    response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
            } else if ("/r".equals(uri)) {
                try {
                    response = receiveBytes(req);
                } catch (Exception e) {
                    log.error("receiveBytes error", e);
                    response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            }
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private HttpResponse sendResponse() throws Exception {
            byte[] rBytes = sendQueue.poll(config.rpost.waitResponseTime, TimeUnit.MILLISECONDS);
            if (null != rBytes) {
                List<byte[]> bytesList = new LinkedList<>();
                bytesList.add(rBytes);
                sendQueue.drainTo(bytesList);
                rBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                log.debug("向客户端发送字节 {}", rBytes.length);

                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.content().writeBytes(rBytes);
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, rBytes.length);
                return response;
            } else {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
                return response;
            }

        }

        private HttpResponse receiveBytes(FullHttpRequest req) throws Exception {
            byte[] bytes = BytesUtil.byteBuf2bytes(req.content());
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
            return response;
        }
    }
}
