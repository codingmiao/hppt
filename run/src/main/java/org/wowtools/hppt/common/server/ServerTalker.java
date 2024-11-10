package org.wowtools.hppt.common.server;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.SendAbleSessionBytes;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.pojo.TalkMessage;
import org.wowtools.hppt.common.util.BufferPool;
import org.wowtools.hppt.common.util.CommonConfig;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.DebugConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author liuyu
 * @date 2024/1/25
 */
@Slf4j
public class ServerTalker {


    //接收客户端发来的字节并做相应处理
    public static void receiveClientBytes(CommonConfig config, ServerSessionManager serverSessionManager,
                                          LoginClientService.Client client, byte[] bytes, long timeoutMillis) throws Exception {
        if (null == bytes || bytes.length == 0) {
            return;
        }
        //解密
        if (config.enableEncrypt) {
            bytes = client.aesCipherUtil.descriptor.decrypt(bytes);
        }
        TalkMessage talkMessage = new TalkMessage(bytes);
        if (DebugConfig.OpenSerialNumber) {
            log.debug("客户端发来的字节解析 >talkMessage-SerialNumber {}", talkMessage.getSerialNumber());
        }
        Map<Integer, ServerSession> serverSessionMap = serverSessionManager.getServerSessionMapByClientId(client.clientId);

        /* 发消息 */
        //发命令
        if (null != talkMessage.getCommands()) {
            for (String command : talkMessage.getCommands()) {
                receiveClientCommand(command, serverSessionManager, serverSessionMap, client, timeoutMillis);
            }
        }

        //发bytes
        if (null != talkMessage.getSessionBytes()) {
            for (SessionBytes sessionByte : talkMessage.getSessionBytes()) {
                if (DebugConfig.OpenSerialNumber) {
                    log.debug("客户端发来字节 >sessionBytes-SerialNumber {}", sessionByte.getSerialNumber());
                }
                ServerSession severSession = serverSessionMap.get(sessionByte.getSessionId());
                if (null == severSession) {
                    //服务端已经没有这个session了，给客户端发关闭命令
                    client.addCommand(String.valueOf(Constant.ScCommands.CloseSession) + sessionByte.getSessionId());
                } else {
                    severSession.sendToTarget(sessionByte);
                }
            }
        }

    }

    private static void receiveClientCommand(String command,
                                             ServerSessionManager serverSessionManager, Map<Integer, ServerSession> serverSessionMap, LoginClientService.Client client, long timeoutMillis) {
        log.debug("收到客户端命令 {} ", command);
        char type = command.charAt(0);
        switch (type) {
            case Constant.SsCommands.CreateSession -> {
                String[] params = command.substring(1).split(Constant.sessionIdJoinFlag);
                int sessionId = serverSessionManager.createServerSession(client, params[0], Integer.parseInt(params[1]), timeoutMillis);
                client.addCommand(String.valueOf(Constant.ScCommands.InitSession) + sessionId + Constant.sessionIdJoinFlag + params[2]);
                if (null == serverSessionManager.getServerSessionBySessionId(sessionId)) {
                    //获取sessionId为空，说明刚才serverSessionManager.createServerSession失败了，所以接着发一条关闭命令给客户端
                    client.addCommand(String.valueOf(Constant.ScCommands.CloseSession) + sessionId);
                }
            }
            case Constant.SsCommands.CloseSession -> {
                if (null == serverSessionMap) {
                    log.info("CloseSession, serverSessionMap尚未建立，忽略命令 {}", command);
                    return;
                }
                int sessionId = Integer.parseInt(command.substring(1));
                ServerSession serverSession = serverSessionMap.get(sessionId);
                if (null != serverSession) {
                    serverSessionManager.disposeServerSession(serverSession, "客户端发送关闭命令");
                }
            }
            case Constant.SsCommands.ActiveSession -> {
                if (null == serverSessionMap) {
                    log.info("ActiveSession, serverSessionMap尚未建立，忽略命令 {}", command);
                    return;
                }
                int sessionId = Integer.parseInt(command.substring(1));
                ServerSession serverSession = serverSessionMap.get(sessionId);
                if (null != serverSession) {
                    serverSession.activeSession();
                }
            }
        }
    }

    /**
     * 回复客户端消息的回复器
     */
    @FunctionalInterface
    public interface Replier {
        //回复消息并返回是否回复成功
        boolean reply(byte[] bytes);
    }

    //生成向客户端回复的消息
    public static void replyToClient(CommonConfig config, ServerSessionManager serverSessionManager,
                                     LoginClientService.Client client, long maxReturnBodySize, boolean blocked, Replier replier) throws Exception {
        boolean empty = true;
        /* 取消息 */

        //取命令
        List<String> fetchCommands = client.fetchCommands();
        if (null != fetchCommands && !fetchCommands.isEmpty()) {
            empty = false;
            blocked = false;
        }

        //取bytes
        List<SendAbleSessionBytes> fetchBytes = blocked ? client.fetchBytesBlocked(maxReturnBodySize) : client.fetchBytes(maxReturnBodySize);
        List<SessionBytes> sessionBytes = null;
        if (null != fetchBytes && !fetchBytes.isEmpty()) {
            if (DebugConfig.OpenSerialNumber) {
                for (SendAbleSessionBytes ssb : fetchBytes) {
                    log.debug("生成向客户端回复的消息 <sessionBytes-SerialNumber {}", ssb.sessionBytes.getSerialNumber());
                }
            }

            sessionBytes = new ArrayList<>(fetchBytes.size());
            for (SendAbleSessionBytes ssb : fetchBytes) {
                SessionBytes fetchByte = ssb.sessionBytes;
                sessionBytes.add(fetchByte);
            }
            empty = false;
        }

        if (empty) {
            return;
        }

        TalkMessage talkMessage = new TalkMessage(sessionBytes, fetchCommands);
        if (DebugConfig.OpenSerialNumber) {
            log.debug("ServerTalker组装 <talkMessage-SerialNumber {}", talkMessage.getSerialNumber());
        }
        byte[] bytes = talkMessage.toProto().build().toByteArray();
        //加密
        if (config.enableEncrypt) {
            bytes = client.aesCipherUtil.encryptor.encrypt(bytes);
        }

        boolean success;
        Exception exception = null;
        try {
            success = replier.reply(bytes);
        } catch (Exception e) {
            exception = e;
            success = false;
        }

        if (null != fetchBytes) {
            for (SendAbleSessionBytes fetchByte : fetchBytes) {
                sendAbleSessionBytesResultQueue.add(new SendAbleSessionBytesResult(success, fetchByte.callBack));
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    //处理SendAbleSessionBytes的回调
    private static final class SendAbleSessionBytesResult {
        private final boolean success;
        private final SendAbleSessionBytes.CallBack callBack;

        public SendAbleSessionBytesResult(boolean success, SendAbleSessionBytes.CallBack callBack) {
            this.success = success;
            this.callBack = callBack;
        }
    }

    @Slf4j
    private static final class CbRunnable implements Runnable {
        private final SendAbleSessionBytesResult sendAbleSessionBytesResult;

        public CbRunnable(SendAbleSessionBytesResult sendAbleSessionBytesResult) {
            this.sendAbleSessionBytesResult = sendAbleSessionBytesResult;
        }

        @Override
        public void run() {
            try {
                sendAbleSessionBytesResult.callBack.cb(sendAbleSessionBytesResult.success);
            } catch (Exception e) {
                log.warn("CbRunnable err", e);
            }
        }
    }

    private static final BufferPool<SendAbleSessionBytesResult> sendAbleSessionBytesResultQueue
            = new BufferPool<>("ServerTalker.sendAbleSessionBytesResultQueue");

    static {
        Thread.startVirtualThread(() -> {
            while (true) {
                try {
                    SendAbleSessionBytesResult sendAbleSessionBytesResult = sendAbleSessionBytesResultQueue.take();
                    CbRunnable cbRunnable = new CbRunnable(sendAbleSessionBytesResult);
                    Thread.startVirtualThread(cbRunnable);
                } catch (Exception e) {
                    log.warn("sendAbleSessionBytesResultQueue.take err", e);
                }
            }
        });
    }
}
