package org.wowtools.hppt.run.sc.post;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author liuyu
 * @date 2024/1/31
 */
@Slf4j
public class PostClientSessionService extends ClientSessionService {

    private long sleepTime;
    private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
    private final String sendUrl;

    public PostClientSessionService(ScConfig config) throws Exception {
        super(config);
        sendUrl = config.post.serverUrl + "/talk?c=" + UUID.randomUUID().toString().replace("-", "");
    }


    @Override
    protected void connectToServer(ScConfig config, Cb cb) {
        startSendThread(cb);
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
                    byte[] sendBytes = sendQueue.poll(sleepTime, TimeUnit.SECONDS);

                    byte[] responseBytes;
                    try (Response response = HttpUtil.doPost(sendUrl, sendBytes)) {
                        ResponseBody body = response.body();
                        responseBytes = null == body ? null : body.bytes();
                    }
                    if (null != responseBytes && responseBytes.length > 0) {
                        receiveServerBytes(responseBytes);
                        sleepTime = config.post.initSleepTime;
                    } else {
                        if (clientSessionManager.getSessionNum()==0) {
                            log.info("无用户连接，睡眠发送线程");
                            sleepTime = Long.MAX_VALUE;
                            //TODO 合并发送和接收的bytes
                        }else {
                            sleepTime += config.post.addSleepTime;
                            if (sleepTime > config.post.maxSleepTime || sleepTime < 0) {
                                sleepTime = config.post.maxSleepTime;
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

    @Override
    protected void sendBytesToServer(byte[] bytes) {
        sendQueue.add(bytes);
    }


}
