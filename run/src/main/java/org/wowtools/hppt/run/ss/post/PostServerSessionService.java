package org.wowtools.hppt.run.ss.post;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.common.utils.LruCache;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.util.Map;

/**
 * @author liuyu
 * @date 2024/1/24
 */
@Slf4j
public class PostServerSessionService extends ServerSessionService<PostCtx> {

    private NettyHttpServer server;

    public PostServerSessionService(SsConfig ssConfig) throws Exception {
        super(ssConfig);
        int size = null == ssConfig.clients ? 8 : ssConfig.clients.size() * 2;
        ctxMap = LruCache.buildCache(size, size);
    }

    @Override
    public void init(SsConfig ssConfig) throws Exception {
        log.info("*********");
        server = new NettyHttpServer(ssConfig.port, this, ssConfig);
        server.start();
    }

    protected final Map<String, PostCtx> ctxMap;

    @Override
    protected void sendBytesToClient(PostCtx ctx, byte[] bytes) {
        ctx.sendQueue.add(bytes);
    }

    @Override
    protected void closeCtx(PostCtx ctx) {
        ctxMap.remove(ctx.cookie);
    }

    @Override
    public void onExit() throws Exception {
        server.stop();
    }


}
