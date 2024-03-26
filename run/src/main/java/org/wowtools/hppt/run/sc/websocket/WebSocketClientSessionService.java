package org.wowtools.hppt.run.sc.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.net.URI;

/**
 * @author liuyu
 * @date 2024/3/12
 */
@Slf4j
public class WebSocketClientSessionService extends ClientSessionService {
    private Channel wsChannel;

    private EventLoopGroup group;

    public WebSocketClientSessionService(ScConfig config) throws Exception {
        super(config);
    }

    @Override
    protected void connectToServer(ScConfig config, Cb cb) throws Exception {
        final URI webSocketURL = new URI(config.websocket.serverUrl);
        group = new NioEventLoopGroup();
        Bootstrap boot = new Bootstrap();
        boot.option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel sc) throws Exception {
                        int bodySize = config.maxSendBodySize * 2;
                        if (bodySize < 0) {
                            bodySize = Integer.MAX_VALUE;
                        }
                        ChannelPipeline pipeline = sc.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new ChunkedWriteHandler());
                        pipeline.addLast(new HttpObjectAggregator(bodySize));
                        pipeline.addLast(new WebSocketClientProtocolHandler(WebSocketClientHandshakerFactory.newHandshaker(webSocketURL, WebSocketVersion.V13, null, false, new DefaultHttpHeaders(), bodySize)));
                        pipeline.addLast(new SimpleChannelInboundHandler<BinaryWebSocketFrame>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
                                receiveServerBytes(BytesUtil.byteBuf2bytes(msg.content()));
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                super.exceptionCaught(ctx, cause);
                                ctx.close();
                                wsChannel.close();
                                group.shutdownGracefully();
                                exit();
                            }

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE.equals(evt)) {
//                                    //每10秒发一个空字节做为心跳包防止websocket断开
//                                    Thread.startVirtualThread(() -> {
//                                        byte[] empty = new byte[0];
//                                        while (actived) {
//                                            try {
//                                                Thread.sleep(10000);
//                                            } catch (InterruptedException e) {
//                                                throw new RuntimeException(e);
//                                            }
//                                            sendBytesToServer(empty);
//                                        }
//                                    });

                                    cb.end();
                                }
                                super.userEventTriggered(ctx, evt);
                            }
                        });
                    }
                });
        ChannelFuture cf = boot.connect(webSocketURL.getHost(), webSocketURL.getPort()).sync();
        wsChannel = cf.channel();
    }

    @Override
    protected void sendBytesToServer(byte[] bytes) {
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(BytesUtil.bytes2byteBuf(wsChannel, bytes));
        wsChannel.writeAndFlush(frame);
    }

}
