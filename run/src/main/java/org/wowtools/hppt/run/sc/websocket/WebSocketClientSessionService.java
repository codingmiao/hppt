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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.client.*;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.util.AesCipherUtil;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.sc.util.ScUtil;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyu
 * @date 2024/3/12
 */
@Slf4j
public class WebSocketClientSessionService {
    private final ScConfig config;
    private final Channel wsChannel;
    private final ClientSessionManager clientSessionManager;

    private final BlockingQueue<String> sendCommandQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<SessionBytes> sendBytesQueue = new LinkedBlockingQueue<>();

    private final Map<Integer, ClientBytesSender.SessionIdCallBack> sessionIdCallBackMap = new HashMap<>();//<newSessionFlag,cb>

    private AesCipherUtil aesCipherUtil;


    public WebSocketClientSessionService(ScConfig config) {
        this.config = config;
        try {
            wsChannel = dest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        clientSessionManager = ScUtil.createClientSessionManager(config,
                buildClientSessionLifecycle(), buildClientBytesSender());
        buildSendThread().start();
    }

    private void sendBytes(byte[] bytes) {
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(BytesUtil.bytes2byteBuf(wsChannel, bytes));
        wsChannel.writeAndFlush(frame);
    }

    private Thread buildSendThread() {
        return new Thread(() -> {
            while (true) {
                try {
                    byte[] sendBytes = ClientTalker.buildSendToServerBytes(config, config.maxSendBodySize, sendCommandQueue, sendBytesQueue, aesCipherUtil, true);
                    if (null != sendBytes) {
                        log.debug("sendBytes {}", sendBytes.length);
                        sendBytes(sendBytes);
                    }
                } catch (Exception e) {
                    log.warn("发送消息异常", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

        });
    }

    private Channel dest() throws Exception {
        final URI webSocketURL = new URI(config.websocket.serverUrl);

        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap boot = new Bootstrap();
        boot.option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .group(group)
                .handler(new LoggingHandler(LogLevel.INFO))
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel sc) throws Exception {
                        ChannelPipeline pipeline = sc.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new ChunkedWriteHandler());
                        pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                        pipeline.addLast(new WebSocketClientProtocolHandler(WebSocketClientHandshakerFactory.newHandshaker(webSocketURL, WebSocketVersion.V13, null, false, new DefaultHttpHeaders())));
                        pipeline.addLast(new SimpleChannelInboundHandler<BinaryWebSocketFrame>() {
                            private Long dt;

                            private boolean firstLoginErr = true;
                            private boolean noLogin = true;

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
                                if (noLogin) {
                                    String s = new String(BytesUtil.byteBuf2bytes(msg.content()), StandardCharsets.UTF_8);
                                    String[] cmd = s.split(" ", 2);
                                    switch (cmd[0]) {
                                        case "dt":
                                            long localTs = System.currentTimeMillis();
                                            long serverTs = Long.parseLong(cmd[1]);
                                            dt = serverTs - localTs;
                                            break;
                                        case "login":
                                            String state = cmd[1];
                                            if ("1".equals(state)) {
                                                noLogin = false;
                                                log.info("登录成功");
                                            } else if (firstLoginErr) {
                                                firstLoginErr = false;
                                                log.warn("第一次登录失败 重试");
                                                Thread.sleep(10000);
                                                sendLoginCommand();
                                            } else {
                                                log.error("登录失败");
                                                System.exit(0);
                                            }
                                            break;
                                        default:
                                            log.warn("未知命令 {}", s);
                                    }
                                } else {
                                    byte[] responseBytes = BytesUtil.byteBuf2bytes(msg.content());
                                    ClientTalker.receiveServerBytes(config, responseBytes, clientSessionManager, aesCipherUtil, sendCommandQueue, sessionIdCallBackMap);
                                }
                            }

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE.equals(evt)) {
                                    log.info(ctx.channel().id().asShortText() + " ws连接建立完成！");
                                    Thread.startVirtualThread(() -> {
                                        //建立ws连接后，获取时间戳
                                        sendBytes("dt".getBytes(StandardCharsets.UTF_8));
                                        //等待时间戳返回
                                        while (null == dt) {
                                            try {
                                                Thread.sleep(100);
                                            } catch (InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                        //登录
                                        sendLoginCommand();
                                    });
                                }
                                super.userEventTriggered(ctx, evt);
                            }

                            private void sendLoginCommand() {
                                aesCipherUtil = new AesCipherUtil(config.clientId, System.currentTimeMillis() + dt);
                                String loginCode = BytesUtil.bytes2base64(aesCipherUtil.encryptor.encrypt(config.clientId.getBytes(StandardCharsets.UTF_8)));
                                sendBytes(("login " + loginCode).getBytes(StandardCharsets.UTF_8));
                            }
                        });
                    }
                });

        ChannelFuture cf = boot.connect(webSocketURL.getHost(), webSocketURL.getPort()).sync();

        return cf.channel();
    }


    private ClientSessionLifecycle buildClientSessionLifecycle() {
        ClientSessionLifecycle common = new ClientSessionLifecycle() {

            @Override
            public void closed(ClientSession clientSession) {
                sendCommandQueue.add(String.valueOf(Constant.SsCommands.CloseSession) + clientSession.getSessionId());
            }
        };
        if (StringUtil.isNullOrEmpty(config.lifecycle)) {
            return common;
        } else {
            try {
                Class<? extends ClientSessionLifecycle> clazz = (Class<? extends ClientSessionLifecycle>) Class.forName(config.lifecycle);
                ClientSessionLifecycle custom = clazz.getDeclaredConstructor().newInstance();
                return new ClientSessionLifecycle() {
                    @Override
                    public void closed(ClientSession clientSession) {
                        common.closed(clientSession);
                        custom.closed(clientSession);
                    }
                };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ClientBytesSender buildClientBytesSender() {
        return new ClientBytesSender() {
            private final AtomicInteger newSessionFlagIdx = new AtomicInteger();

            @Override
            public void connected(int port, ChannelHandlerContext ctx, SessionIdCallBack cb) {
                for (ScConfig.Forward forward : config.forwards) {
                    if (forward.localPort == port) {
                        int newSessionFlag = newSessionFlagIdx.addAndGet(1);
                        String cmd = Constant.SsCommands.CreateSession + forward.remoteHost + Constant.sessionIdJoinFlag + forward.remotePort + Constant.sessionIdJoinFlag + newSessionFlag;
                        sendCommandQueue.add(cmd);
                        log.debug("connected command: {}", cmd);
                        sessionIdCallBackMap.put(newSessionFlag, cb);
                        return;
                    }
                }
                throw new RuntimeException("未知 localPort " + port);
            }

            @Override
            public void sendToTarget(ClientSession clientSession, byte[] bytes) {
                sendBytesQueue.add(new SessionBytes(clientSession.getSessionId(), bytes));
            }
        };
    }
}
