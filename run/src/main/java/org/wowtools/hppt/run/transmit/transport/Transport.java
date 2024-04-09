package org.wowtools.hppt.run.transmit.transport;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.JsonConfig;
import org.wowtools.hppt.run.transmit.receiver.Receiver;

import java.util.Map;

/**
 * @author liuyu
 * @date 2024/4/7
 */
@Slf4j
public abstract class Transport implements Receiver {


    /**
     * 当一个事件结束时发起的回调
     */
    @FunctionalInterface
    protected interface Cb {
        void end();
    }

    private Receiver receiver;

    protected final JsonConfig.MapConfig config;

    protected volatile boolean actived = true;

    private final String receiveId;
    private final String sendId;

    /**
     * @param config 配置文件
     * @param cb     当初始化完成时，调用cb.end() 通知外部
     */
    public Transport(String receiveId, String sendId, JsonConfig.MapConfig config, Cb cb) {
        this.receiveId = receiveId;
        this.sendId = sendId;
        this.config = config;
    }


    /**
     * 把字节交给receiver去处理
     *
     * @param bytes bytes
     */
    public void receive(byte[] bytes) {
        if (null != receiver) {
            receiver.send(bytes);
        }
    }

    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    /**
     * 当发生难以修复的异常等情况时，主动调用此方法结束当前服务，以便后续自动重启等操作
     */
    protected void exit() {
        actived = false;
        synchronized (this) {
            this.notify();
        }
    }

    /**
     * 阻塞直到exit方法被调用
     */
    public void sync() {
        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("sync interrupted");
        }
    }

    public String getReceiveId() {
        return receiveId;
    }

    public String getSendId() {
        return sendId;
    }
}
