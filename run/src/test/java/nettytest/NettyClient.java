package nettytest;

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
import org.wowtools.hppt.common.util.NettyChannelTypeChecker;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
public class NettyClient {

    private static final class Rsp{
        CompletableFuture<String> future = new CompletableFuture<>();

    }
    private static final Map<Integer, Rsp> callRequestMap = new ConcurrentHashMap<>();
    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup workerGroup = NettyChannelTypeChecker.buildVirtualThreadEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            int len = 4;
                            int maxFrameLength = (int) (Math.pow(256, len) - 1);
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, len, 0, len));
                            pipeline.addLast(new LengthFieldPrepender(len));
                            pipeline.addLast(new MessageHandler());
                        }
                    });

            bootstrap.connect("localhost", 20871).sync().channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    public static ByteBuf bytes2byteBuf(ChannelHandlerContext ctx, byte[] bytes) {
        ByteBuf byteBuf = ctx.alloc().buffer(bytes.length, bytes.length);
        byteBuf.writeBytes(bytes);
        return byteBuf;
    }

    private static class MessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            // 处理接收到的消息
            byte[] content = BytesUtil.byteBuf2bytes(msg);
            Thread.startVirtualThread(()->{
                int id = Integer.parseInt(new String(content, StandardCharsets.UTF_8).split(" ")[0]);
                log.info("rsp {}",id);
                Rsp rsp = callRequestMap.remove(id);
                if (null != rsp) {
                    rsp.future.complete(String.valueOf(id));
                    synchronized (rsp) {
                        rsp.notify();
                        log.info("rsp notify {}",id);
                    }

                }
            });
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // 发送消息到服务器
            Thread.startVirtualThread(()->{
                for (int i = 0; i < 1000000; i++) {
                    String msg = i+" "+ UUID.randomUUID()+UUID.randomUUID()+UUID.randomUUID()+UUID.randomUUID();
                    int fi = i;

                    log.info("write {}",fi);
                    Rsp rsp = new Rsp();
                    callRequestMap.put(fi, rsp);

                    BytesUtil.writeToChannelHandlerContext(ctx, msg.getBytes(StandardCharsets.UTF_8));

                    try {
                        String rr = rsp.future.get(10000, TimeUnit.MILLISECONDS);
                        System.out.println(rr);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    } catch (TimeoutException e) {
                        System.out.println("等待超時");
                    }

                }
            });

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}

