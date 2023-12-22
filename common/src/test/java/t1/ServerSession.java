package t1;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ServerSession {
    private final SimpleClientHandler simpleClientHandler;
    private final int sessionId;

    private volatile boolean running = true;

    private final ServerSession serverSession = this;

    public ServerSession(String host, int port, int sessionId) {
        this.sessionId = sessionId;
        simpleClientHandler = new SimpleClientHandler(sessionId);
        new Thread(() -> {
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(simpleClientHandler);
                            }
                        });

                ChannelFuture future = bootstrap.connect(host, port).sync();
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                group.shutdownGracefully();
                log.debug("ServerSession group.shutdownGracefully();");
            }
        }).start();


    }

    private class SimpleClientHandler extends ChannelInboundHandlerAdapter {
        private final int sessionId;

        private final BlockingQueue<byte[]> sendQueue = new ArrayBlockingQueue<>(100000);

        private Thread sendThread;
        private ChannelHandlerContext ctx;

        public SimpleClientHandler(int sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            //连接建立 从队列中取数据发送到目标端口
            if (null != sendThread) {
                throw new RuntimeException("逻辑错误 sendThread 重复构建");
            }
            sendThread = new Thread(() -> {
                while (running) {
                    byte[] bytes;
                    try {
                        bytes = sendQueue.poll(1, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        log.debug("sendThread {} Interrupted", sessionId);
                        break;
                    }
                    if (bytes == null) {
                        continue;
                    }
                    ByteBuf message = Unpooled.copiedBuffer(bytes);
                    ctx.writeAndFlush(message);
                    log.debug("serverSession {} 向目标端口发送字节 {}", sessionId, bytes.length);
                }
                log.debug("sendThread {} end", sessionId);
            });
            sendThread.start();
            this.ctx = ctx;
            log.info("serverSession channelActive {}", sessionId);

        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("serverSession channelInactive {}", sessionId);
            super.channelInactive(ctx);
            ServerSessionManager.disposeServerSession(serverSession,"channelInactive");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf byteBuf = (ByteBuf) msg;
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            log.debug("serverSession {} 收到目标端口字节 {}", sessionId, bytes.length);
            try {
                ServerSessionManager.serverSessionSendQueue.add(new SessionBytes(sessionId, bytes));
            } catch (Exception e) {
                log.warn("serverSessionSendQueue.add异常，清空", e);
                ServerSessionManager.serverSessionSendQueue.clear();
            }

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("{} exceptionCaught", sessionId, cause);
            ServerSessionManager.disposeServerSession(serverSession,"exceptionCaught");
        }

        public void close() {
            sendThread.interrupt();
            ctx.close();
        }
    }

    public void putBytes(byte[] bytes) {
        try {
            simpleClientHandler.sendQueue.add(bytes);
            log.debug("ServerSession {} 收到 Client发来的字节 {}", sessionId, bytes.length);
        } catch (Exception e) {
            log.warn("ServerSession {} simpleClientHandler.sendQueue.add(bytes) 异常，清空队列", sessionId, e);
        }
    }


    public int getSessionId() {
        return sessionId;
    }

    public void close() {
        running = false;
        simpleClientHandler.close();
    }


//    public static void main(String[] args) throws Exception {
//        ServerSession session = new ServerSession("localhost", 7779, 1, (bytes) -> {
//            System.out.println(new String(bytes));
//        });
//        session.putBytes(send.getBytes(StandardCharsets.UTF_8));
//        Thread.sleep(10000000);
//    }
//
//    private static final String send = "GET /sc.json HTTP/1.1\n" +
//            "Host: localhost:11001\n" +
//            "User-Agent: curl/7.50.3\n" +
//            "Accept: */*\n" +
//            "\n";
}
