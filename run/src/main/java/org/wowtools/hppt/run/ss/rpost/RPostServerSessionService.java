package org.wowtools.hppt.run.ss.rpost;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author liuyu
 * @date 2024/4/16
 */
@Slf4j
public class RPostServerSessionService extends ServerSessionService<RPostCtx> {

    private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
    private final String sendUrl;

    private final String interruptUrl;

    private volatile boolean actived = true;

    private volatile boolean sending = false;

    public RPostServerSessionService(SsConfig ssConfig) {
        super(ssConfig);
        sendUrl = ssConfig.rpost.serverUrl + "/t";
        interruptUrl = ssConfig.rpost.serverUrl + "/i";
    }

    @Override
    public void init(SsConfig ssConfig) throws Exception {
        startSendThread();
    }

    private void startSendThread() {
        Thread.startVirtualThread(() -> {
            RPostCtx rPostCtx = new RPostCtx();
            while (actived) {
                try {
                    byte[] sendBytes;
                    {
                        List<byte[]> bytesList = new LinkedList<>();
                        sendQueue.drainTo(bytesList);
                        if (!bytesList.isEmpty()) {
                            sendBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                        } else {
                            sendBytes = null;
                        }
                    }
                    byte[] responseBytes;
                    sending = true;
                    try (Response response = HttpUtil.doPost(sendUrl, sendBytes)) {
                        ResponseBody body = response.body();
                        responseBytes = null == body ? null : body.bytes();
                    }
                    sending = false;
                    if (null != responseBytes && responseBytes.length > 0) {
                        log.debug("收到服务端响应字节数 {}", responseBytes.length);
                        List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(responseBytes);
                        for (byte[] bytes : bytesList) {
                            receiveClientBytes(rPostCtx, bytes);
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
    protected void sendBytesToClient(RPostCtx rPostCtx, byte[] bytes) {
        sendQueue.add(bytes);
        if (sending) {
            //通知服务端打断等待
            synchronized (interruptUrl) {
                if (sending) {
                    HttpUtil.doPost(interruptUrl, null).close();
                    sending = false;
                    log.debug("通知服务端打断等待");
                }
            }

        }
    }

    @Override
    protected void closeCtx(RPostCtx rPostCtx) throws Exception {
        actived = false;
    }

    @Override
    protected void doClose() throws Exception {
        actived = false;
    }
}
