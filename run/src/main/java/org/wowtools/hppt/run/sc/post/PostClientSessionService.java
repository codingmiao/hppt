package org.wowtools.hppt.run.sc.post;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
        new Thread(() -> {
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
                            log.info("sleep {}", sleepTime);
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
                            receiveServerBytes(bytes);
                        }
                        sleepTime = config.post.initSleepTime;
                    } else {
                        if (null != sendBytes && sendBytes.length > 0) {
                            sleepTime = config.post.initSleepTime;
                        } else if (clientSessionManager.getSessionNum() == 0
                                && sleepTime >= config.post.initSleepTime + 3 * config.post.addSleepTime) {
                            log.info("无用户连接，睡眠发送线程");
                            sleepTime = Long.MAX_VALUE;
                        } else {
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
        }).start();
    }

    @Override
    protected void sendBytesToServer(byte[] bytes) {
        sendQueue.add(bytes);
    }


}
