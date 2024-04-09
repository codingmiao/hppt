package org.wowtools.hppt.run.transmit.transport;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.common.util.JsonConfig;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2024/4/8
 */
@Slf4j
public class PostClientTransport extends Transport {

    private long sleepTime;
    private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
    private final String sendUrl;

    private final long initSleepTime;
    private final long maxSleepTime;
    private final long addSleepTime;


    public PostClientTransport(String receiveId, String sendId, JsonConfig.MapConfig config, Cb cb) {
        super(receiveId, sendId, config, cb);
        startSendThread(cb);
        sendUrl = config.value("url") + "?c=" + config.value("clientId");//TODO clientId加密
        initSleepTime = config.value("initSleepTime", 0);
        maxSleepTime = config.value("maxSleepTime", 10000);
        addSleepTime = config.value("addSleepTime", 200);
    }

    @Override
    public void send(byte[] bytes) {
        sendQueue.add(bytes);
    }

    private void startSendThread(Cb cb) {
        Thread.startVirtualThread(() -> {
            while (null == sendQueue) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            cb.end();
            while (actived) {
                try {
                    byte[] sendBytes;
                    {
                        List<byte[]> bytesList = new LinkedList<>();
                        if (log.isDebugEnabled()) {
                            log.debug("sleep {}", sleepTime);
                        } else if (sleepTime > 5000) {
                            log.info("sleep {}", sleepTime);
                        }
                        byte[] bytes = sendQueue.poll(sleepTime, TimeUnit.MILLISECONDS);
                        if (null != bytes) {
                            bytesList.add(bytes);
                            sendQueue.drainTo(bytesList);
                            sendBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                        } else {
                            sendBytes = null;
                        }
                    }


                    byte[] responseBytes;
                    try (Response response = HttpUtil.doPost(sendUrl, sendBytes)) {
                        ResponseBody body = response.body();
                        responseBytes = null == body ? null : body.bytes();
                    }
                    if (null != responseBytes && responseBytes.length > 0) {
                        log.debug("收到服务端响应字节数 {}", responseBytes.length);
                        List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(responseBytes);
                        for (byte[] bytes : bytesList) {
                            receive(bytes);
                        }
                        sleepTime = initSleepTime;
                    } else {
                        if (null != sendBytes && sendBytes.length > 0) {
                            sleepTime = initSleepTime;
                        } else {
                            sleepTime += addSleepTime;
                            if (sleepTime > maxSleepTime || sleepTime < 0) {
                                sleepTime = maxSleepTime;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("发送线程执行异常", e);
                    exit();
                }
            }
        });
    }
}
