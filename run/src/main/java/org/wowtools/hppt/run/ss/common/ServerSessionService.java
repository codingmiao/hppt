package org.wowtools.hppt.run.ss.common;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

/**
 * ServerSessionService抽象类
 * 注意，编写实现类时，不要在构造方法里做会阻塞的事情(比如起一个端口)，丢到init方法里做
 *
 * @param <CTX> 实际和客户端连接的上下文，如ChannelHandlerContext等
 */
@Slf4j
public abstract class ServerSessionService<CTX> {

    protected final SsConfig ssConfig;

    private final Receiver<CTX> receiver;


    protected boolean running = false;

    /**
     * @param ssConfig 配置信息
     */
    public ServerSessionService(SsConfig ssConfig) {
        this.ssConfig = ssConfig;
        if (null == ssConfig.relayScConfig) {
            receiver = new PortReceiver<>(ssConfig, this);
            log.info("--- 普通模式");
        } else {
            receiver = new SsReceiver<>(ssConfig.relayScConfig, this);
            log.info("--- 中继模式");
        }

        //起一个线程，定期检查服务心跳
        if (ssConfig.heartbeatTimeout > 0) {
            Thread.startVirtualThread(() -> {
                while (running) {
                    long lt = receiver.getLastHeartbeatTime();
                    if (lt < 0 || System.currentTimeMillis() - lt < ssConfig.heartbeatTimeout) {
                        try {
                            Thread.sleep(ssConfig.heartbeatTimeout);
                        } catch (InterruptedException ignored) {
                        }
                    } else {
                        break;
                    }

                }
                log.warn("服务端心跳监测超时，执行重启");
                exit("心跳监测失败");
            });
        }

    }

    /**
     * 启动服务 同步阻塞直到发生异常退出
     *
     * @param ssConfig 配置信息
     */
    public void syncStart(SsConfig ssConfig) {
        log.info("syncStart {}", this);
        running = true;
        try {
            init(ssConfig);
        } catch (Exception e) {
            log.warn("初始化失败 {}", this, e);
            exit("init err");
        }
        log.info("-------syncStart end {}", this);
        while (running) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        exit("running stop");
    }

    /**
     * 初始化操作，允许阻塞/挂起方法
     *
     * @param ssConfig 配置信息
     */
    protected abstract void init(SsConfig ssConfig) throws Exception;

    /**
     * 发送字节到客户端的具体方法
     *
     * @param ctx   实际和客户端连接的上下文
     * @param bytes bytes
     */
    protected abstract void sendBytesToClient(CTX ctx, byte[] bytes);

    /**
     * 收到客户端传过来的字节时，主动调用此方法进行接收操作。
     * 此方法内部只是一个快速放入缓冲池的过程，所以速度很快，外部建议无需另起多线程来调此方法，不然外部的先后顺序不好控制
     *
     * @param ctx   实际和客户端连接的上下文
     * @param bytes bytes
     */
    public void receiveClientBytes(CTX ctx, byte[] bytes) {
        if (null == bytes || bytes.length == 0) {
            return;
        }
        log.debug("收到客户端字节数 {} , {}", bytes.length, ctx);
        try {
            receiver.receiveClientBytes(ctx, bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 关闭上下文
     *
     * @param ctx ctx
     */
    protected abstract void closeCtx(CTX ctx) throws Exception;

    /**
     * 退出当前服务时需要做的事情
     */
    protected abstract void onExit() throws Exception;

    /**
     * 当发生难以修复的异常等情况时，主动调用此方法结束当前服务，以便后续自动重启等操作
     */
    public void exit(String type) {
        log.warn("ServerSessionService exit,type [{}] service {}",type, this);
        receiver.exit();
        try {
            onExit();
        } catch (Exception e) {
            log.warn("doClose error ", e);
        }
        running = false;
    }


    /**
     * 移除无用的上下文，在有异常、上下文关闭等情况下主动调用
     *
     * @param ctx
     */
    protected void removeCtx(CTX ctx) {
        receiver.removeCtx(ctx);
        try {
            closeCtx(ctx);
        } catch (Exception e) {
            log.warn("closeCtx error", e);
        }
    }

    public Receiver<CTX> getReceiver() {
        return receiver;
    }
}
