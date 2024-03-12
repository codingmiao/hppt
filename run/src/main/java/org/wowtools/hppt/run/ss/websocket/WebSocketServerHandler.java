package org.wowtools.hppt.run.ss.websocket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.server.LoginClientService;
import org.wowtools.hppt.common.server.ServerSessionManager;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.run.ss.pojo.SsConfig;
import org.wowtools.hppt.run.ss.util.SsChannelTalker;

/**
 * @author liuyu
 * @date 2024/2/7
 */
@Slf4j
@ChannelHandler.Sharable
class WebSocketServerHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    private final SsChannelTalker talker;

    public WebSocketServerHandler(SsConfig ssConfig, ServerSessionManager serverSessionManager, LoginClientService loginClientService) {
        talker = new SsChannelTalker(ssConfig, serverSessionManager, loginClientService){
            @Override
            protected Object buildWriteAndFlushObj(ChannelHandlerContext ctx, byte[] bytes) {
                return new BinaryWebSocketFrame(BytesUtil.bytes2byteBuf(ctx, bytes));
            }
        };
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        talker.removeClient(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        talker.removeClient(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
        // 获取字节数据
        byte[] bytes = new byte[msg.content().readableBytes()];
        msg.content().readBytes(bytes);
        talker.doChannelRead0(ctx, bytes);
    }

}
