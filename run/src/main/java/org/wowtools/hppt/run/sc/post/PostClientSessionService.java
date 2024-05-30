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

/**
 * @author liuyu
 * @date 2024/1/31
 */
@Slf4j
public class PostClientSessionService extends ClientSessionService {

    private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
    private final String sendUrl;
    private final String replyUrl;


    public PostClientSessionService(ScConfig config) throws Exception {
        super(config);
        String cookie = UUID.randomUUID().toString().replace("-", "");
        sendUrl = config.post.serverUrl + "/s?c=" + cookie;
        replyUrl = config.post.serverUrl + "/r?c=" + cookie;
    }


    @Override
    protected void connectToServer(ScConfig config, Cb cb) {
        startSendThread(() -> {
            startReplyThread();
            cb.end();
        });
    }

    private void startSendThread(Cb cb) {
        Thread.startVirtualThread(() -> {
            //等待初始化完成
            while (null == sendQueue) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            cb.end();
            final long sendSleepTime = config.post.sendSleepTime;
            while (actived) {
                if (sendSleepTime > 0) {
                    try {
                        Thread.sleep(sendSleepTime);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                try {
                    byte[] sendBytes;
                    List<byte[]> bytesList = new LinkedList<>();
                    sendBytes = sendQueue.take();
                    bytesList.add(sendBytes);
                    sendQueue.drainTo(bytesList);
                    sendBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                    try (Response ignored = HttpUtil.doPost(sendUrl, sendBytes)) {
                        log.debug("startSendThread 发送完成");
                    }
                } catch (Exception e) {
                    log.warn("SendThread异常", e);
                    exit();
                }
            }
        });
    }

    private final Object replyThreadEmptyLock = new Object();

    private void startReplyThread() {
        Thread.startVirtualThread(() -> {
            boolean empty = false;
            final long sendSleepTime = config.post.sendSleepTime;
            while (actived) {
                if (sendSleepTime > 0) {
                    try {
                        Thread.sleep(sendSleepTime);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (empty && !isNoLogin() && clientSessionManager.getSessionNum() == 0) {
                    synchronized (replyThreadEmptyLock) {
                        log.info("无客户端,睡眠接收线程");
                        try {
                            replyThreadEmptyLock.wait();
                        } catch (InterruptedException e) {
                            log.info("唤醒接收线程");
                        }
                    }
                }
                try {
                    byte[] responseBytes;
                    try (Response response = HttpUtil.doPost(replyUrl, null)) {
                        ResponseBody body = response.body();
                        responseBytes = null == body ? null : body.bytes();
                    }
                    if (null != responseBytes && responseBytes.length > 0) {
                        log.debug("收到服务端响应字节数 {}", responseBytes.length);
                        List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(responseBytes);
                        for (byte[] bytes : bytesList) {
                            receiveServerBytes(bytes);
                        }
                        empty = bytesList.isEmpty();
                    }else {
                        empty = true;
                    }
                } catch (Exception e) {
                    log.warn("ReplyThread异常", e);
                    exit();
                }
            }
        });
    }


    @Override
    protected void sendBytesToServer(byte[] bytes) {
        sendQueue.add(bytes);
    }

    @Override
    protected void newConnected() {
        synchronized (replyThreadEmptyLock) {
            replyThreadEmptyLock.notify();
        }
    }
}
