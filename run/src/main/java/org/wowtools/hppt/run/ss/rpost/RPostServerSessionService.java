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
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2024/4/16
 */
@Slf4j
public class RPostServerSessionService extends ServerSessionService<RPostCtx> {

    private final BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
    private final String sendUrl;
    private final String receiveUrl;


    private volatile boolean actived = true;


    public RPostServerSessionService(SsConfig ssConfig) {
        super(ssConfig);
        sendUrl = ssConfig.rpost.serverUrl + "/s";
        receiveUrl = ssConfig.rpost.serverUrl + "/r";
    }

    @Override
    public void init(SsConfig ssConfig) throws Exception {
        startSendThread();
        startReceiveThread();
    }

    private void startSendThread() {
        Thread.startVirtualThread(() -> {
            while (actived) {
                try {
                    byte[] sendBytes = sendQueue.poll(10000, TimeUnit.MINUTES);
                    if (null == sendBytes || !actived) {
                        continue;
                    }
                    List<byte[]> bytesList = new LinkedList<>();
                    bytesList.add(sendBytes);
                    sendBytes = BytesUtil.bytesCollection2PbBytes(bytesList);
                    try (Response ignored = HttpUtil.doPost(receiveUrl, sendBytes)) {
                    }
                } catch (Exception e) {
                    log.warn("发送线程执行异常,10秒后重启", e);
                    try {
                        Thread.sleep(10000);
                    } catch (Exception ex) {
                    }
                    exit();
                }
            }
        });
    }

    private void startReceiveThread() {
        Thread.startVirtualThread(() -> {
            RPostCtx rPostCtx = new RPostCtx();
            while (actived) {
                try {
                    byte[] responseBytes;
                    try (Response response = HttpUtil.doPost(sendUrl, null)) {
                        ResponseBody body = response.body();
                        responseBytes = null == body ? null : body.bytes();
                    }
                    if (null != responseBytes && responseBytes.length > 0) {
                        log.debug("收到服务端响应字节数 {}", responseBytes.length);
                        List<byte[]> bytesList = BytesUtil.pbBytes2BytesList(responseBytes);
                        for (byte[] bytes : bytesList) {
                            receiveClientBytes(rPostCtx, bytes);
                        }
                    }
                } catch (Exception e) {
                    log.warn("接收线程执行异常,10秒后重启", e);
                    try {
                        Thread.sleep(10000);
                    } catch (Exception ex) {
                    }
                    exit();
                }
            }
        });
    }

    @Override
    protected void sendBytesToClient(RPostCtx rPostCtx, byte[] bytes) {
        sendQueue.add(bytes);
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
