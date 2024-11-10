package org.wowtools.hppt.run.sc.post;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
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
    public void connectToServer(ScConfig config, Cb cb) {
        startSendThread((e) -> {
            startReplyThread();
            cb.end(e);
        });
    }

    private void startSendThread(Cb cb) {
        Thread.startVirtualThread(() -> {
            //等待初始化完成
            cb.end(null);
            //起一个while循环不断发送数据
            final long sendSleepTime = config.post.sendSleepTime;
            while (running) {
                try {
                    byte[] sendBytes;
                    List<byte[]> bytesList = new LinkedList<>();
                    sendBytes = sendQueue.take();
                    bytesList.add(sendBytes);
                    if (sendSleepTime > 0) {
                        try {
                            Thread.sleep(sendSleepTime);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    sendQueue.drainTo(bytesList);
                    sendBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                    if (log.isDebugEnabled()) {
                        long t = System.currentTimeMillis();
                        try (Response r = HttpUtil.doPost(sendUrl, sendBytes)) {
                            assert r.body() != null;
                            byte[] rBytes = r.body().bytes();
                            if (rBytes.length == 0) {
                                log.debug("SendThread 发送完成,cost {}", System.currentTimeMillis() - t);
                            } else {
                                throw new RuntimeException("异常的响应值" + new String(rBytes, StandardCharsets.UTF_8));
                            }
                        }
                    } else {
                        try (Response r = HttpUtil.doPost(sendUrl, sendBytes)) {
                            assert r.body() != null;
                            byte[] rBytes = r.body().bytes();
                            if (rBytes.length == 0) {
                                log.debug("SendThread 发送完成");
                            } else {
                                throw new RuntimeException("异常的响应值" + new String(rBytes, StandardCharsets.UTF_8));
                            }
                        }
                    }

                } catch (Exception e) {
                    log.warn("SendThread异常", e);
                    exit();
                }
            }
        });
    }

    private final Object replyThreadEmptyLock = new Object();
    private volatile boolean replyThreadEmptySleep = false;

    private void startReplyThread() {
        Thread.startVirtualThread(() -> {
            while (null == sendUrl) {
                log.info("wait url init");
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
            boolean empty = false;
            final long sendSleepTime = config.post.sendSleepTime;
            while (running) {
                //检测是否需要挂起接收线程
                if (empty && notUsed()) {
                    log.info("无客户端,挂起接收线程");
                    do {
                        synchronized (replyThreadEmptyLock) {
                            try {
                                replyThreadEmptyLock.wait(10_000);
                            } catch (Exception e) {
                            }
                        }
                    } while (notUsed() && replyThreadEmptySleep && running);
                    log.info("唤醒接收线程");
                    if (!running) {
                        log.info("退出已停止ReplyThread");
                        return;
                    }
                }
                //发一个接收请求接数据
                try {
                    byte[] responseBytes;
                    if (log.isDebugEnabled()) {
                        log.debug("ReplyThread 发起请求");
                        long t = System.currentTimeMillis();
                        try (Response response = HttpUtil.doPost(replyUrl, null)) {
                            ResponseBody body = response.body();
                            responseBytes = null == body ? null : body.bytes();
                        }finally {
                            log.debug("ReplyThread 请求完成,cost {}", System.currentTimeMillis() - t);
                        }
                    }else {
                        try (Response response = HttpUtil.doPost(replyUrl, null)) {
                            ResponseBody body = response.body();
                            responseBytes = null == body ? null : body.bytes();
                        }
                    }
                    if (null != responseBytes && responseBytes.length > 0) {
                        log.debug("收到服务端响应字节数 {}", responseBytes.length);
                        Collection<byte[]> bytesList;
                        try {
                            bytesList = BytesUtil.pbBytes2BytesList(responseBytes).getBytes();
                        } catch (Exception e) {
                            log.warn("解析字节异常 {}", new String(responseBytes, StandardCharsets.UTF_8));
                            throw e;
                        }
                        for (byte[] bytes : bytesList) {
                            receiveServerBytes(bytes);
                        }
                        empty = bytesList.isEmpty();
                    } else {
                        empty = true;
                    }
                } catch (Exception e) {
                    log.warn("ReplyThread异常", e);
                    exit();
                }
                //按需做等待
                if (sendSleepTime > 0) {
                    try {
                        Thread.sleep(sendSleepTime);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }


    @Override
    public void sendBytesToServer(byte[] bytes) {
        sendQueue.add(bytes);
    }

    @Override
    protected void newConnected() {
        synchronized (replyThreadEmptyLock) {
            replyThreadEmptyLock.notify();
        }
    }

    @Override
    public void exit() {
        super.exit();
        Thread.startVirtualThread(() -> {
            synchronized (replyThreadEmptyLock) {
                replyThreadEmptyLock.notify();
            }
        });
    }
}
