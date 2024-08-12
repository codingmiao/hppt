package t1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ClientSession {
    private final int sessionId;
    private final ChannelHandlerContext channelHandlerContext;

    public ClientSession(int sessionId, ChannelHandlerContext channelHandlerContext) {
        this.sessionId = sessionId;
        this.channelHandlerContext = channelHandlerContext;
    }

    public void putBytes(byte[] bytes) {
        log.debug("ClientSession {} 收到服务端发来的字节数 {}", sessionId, bytes.length);
        ByteBuf msg = Unpooled.copiedBuffer(bytes);
        channelHandlerContext.writeAndFlush(msg);
    }

    public int getSessionId() {
        return sessionId;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    public void close() {
        channelHandlerContext.close();
    }
}
