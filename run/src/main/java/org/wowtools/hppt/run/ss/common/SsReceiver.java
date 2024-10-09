package org.wowtools.hppt.run.ss.common;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.RoughTimeUtil;
import org.wowtools.hppt.run.sc.ClientSessionServiceBuilder;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 中继模式下，数据被发送到另一个ss
 *
 * @author liuyu
 * @date 2024/9/26
 */
@Slf4j
final class SsReceiver<CTX> implements Receiver<CTX> {

    private final class Cell implements Closeable {
        private final ClientSessionService clientSessionService;
        private volatile boolean running = true;
        private long activeTime;

        public Cell(ClientSessionService clientSessionService, CTX ctx) {
            this.clientSessionService = clientSessionService;
            org.wowtools.hppt.run.sc.common.SsReceiver ssReceiver =
                    (org.wowtools.hppt.run.sc.common.SsReceiver) clientSessionService.receiver;
            Thread.startVirtualThread(() -> {
                while (running) {
                    try {
                        byte[] bytes = ssReceiver.serverBytesQueue.poll(10, TimeUnit.SECONDS);
                        if (null == bytes) {
                            continue;
                        }
                        activeTime = RoughTimeUtil.getTimestamp();
                        serverSessionService.sendBytesToClient(ctx, bytes);
                    } catch (Exception e) {
                        exit();
                    }
                }
                log.info("cell close");
            });
        }

        @Override
        public void close() {
            running = false;
            clientSessionService.exit();
        }
    }

    private final ScConfig scConfig;
    private final Map<CTX, Cell> ctxClientSessionServiceMap = new ConcurrentHashMap<>(1);
    private final ServerSessionService<CTX> serverSessionService;
    private volatile boolean running = true;

    SsReceiver(ScConfig scConfig, ServerSessionService<CTX> serverSessionService) {
        this.scConfig = scConfig;
        scConfig.isRelay = true;
        this.serverSessionService = serverSessionService;
        Thread.startVirtualThread(() -> {
            while (running) {
                try {
                    Thread.sleep(600_000L);
                    ctxClientSessionServiceMap.forEach((ctx, cell) -> {
                        if (!cell.running) {
                            log.info("清理不在运行的cell {}", ctx);
                            ctxClientSessionServiceMap.remove(ctx);
                        } else if (RoughTimeUtil.getTimestamp() - cell.activeTime > 600_000L) {
                            //TODO 由于中继模式下不对发送报文进行解析，无法得知clientId，暂时采取定期检查活跃性的方法来移出不活跃的上下文
                            log.info("清理超时cell {}", ctx);
                            ctxClientSessionServiceMap.remove(ctx);
                            cell.close();
                        }
                    });
                    log.info("cell num {}", ctxClientSessionServiceMap.size());
                } catch (Exception e) {
                    log.warn("检查活跃cell异常", e);
                }
            }
        });
    }

    @Override
    public void receiveClientBytes(CTX ctx, byte[] bytes) throws Exception {
        Cell cell = ctxClientSessionServiceMap.get(ctx);
        if (null == cell) {
            ClientSessionService clientSessionService = ClientSessionServiceBuilder.build(scConfig);
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            ClientSessionService.Cb cb = () -> future.complete(true);
            clientSessionService.connectToServer(scConfig, cb);
            future.get();
            cell = new Cell(clientSessionService, ctx);
            ctxClientSessionServiceMap.put(ctx, cell);
            cb.end();
            log.info("new cell by ctx {}", ctx);
        }
        cell.clientSessionService.sendBytesToServer(bytes);
    }

    @Override
    public void removeCtx(CTX ctx) {
        Cell cell = ctxClientSessionServiceMap.remove(ctx);
        if (null != cell) {
            cell.close();
        }
    }

    @Override
    public void exit() {
        running = false;
        ctxClientSessionServiceMap.forEach((ctx, cell) -> cell.close());
    }
}
