package org.wowtools.hppt.common.client;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.AesCipherUtil;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.CommonConfig;
import org.wowtools.hppt.common.util.Constant;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2024/2/1
 */
@Slf4j
public class ClientTalker {

    /**
     * 将缓冲区的数据转为满足向服务端发送的字节
     *
     * @param config           config
     * @param maxSendBodySize  最多发送多少字节
     * @param sendCommandQueue 命令队列
     * @param sendBytesQueue   字节队列
     * @param aesCipherUtil    加密工具
     * @param wait             若两个队列为空且wait为true，则取数据时会阻塞等待3秒
     * @return
     * @throws Exception
     */
    public static byte[] buildSendToServerBytes(CommonConfig config, long maxSendBodySize,
                                                BlockingQueue<String> sendCommandQueue, BlockingQueue<SessionBytes> sendBytesQueue,
                                                AesCipherUtil aesCipherUtil, boolean wait) throws Exception {
        long sendBodySize = 0;//大致预估发送体积
        //命令
        LinkedList<String> commands = new LinkedList<>();
        do {
            String cmd = sendCommandQueue.poll();
            if (null == cmd) {
                break;
            }
            sendBodySize += cmd.length() + 1;
            commands.add(cmd);
            if (sendBodySize >= maxSendBodySize) {
                break;
            }
        } while (true);
        if (sendBodySize > 0) {
            wait = false;
        }
        //bytes
        List<ProtoMessage.BytesPb> bytesPbList = new LinkedList<>();
        do {
            if (sendBodySize >= maxSendBodySize) {
                break;
            }
            SessionBytes bytes;
            if (wait) {
                bytes = sendBytesQueue.poll(3, TimeUnit.SECONDS);
                wait = false;
            } else {
                bytes = sendBytesQueue.poll();
            }
            if (null == bytes) {
                break;
            }
            sendBodySize += bytes.getBytes().length;
            bytesPbList.add(ProtoMessage.BytesPb.newBuilder()
                    .setBytes(ByteString.copyFrom(bytes.getBytes()))
                    .setSessionId(bytes.getSessionId())
                    .build());
        } while (true);

        if (sendBodySize == 0) {
            return null;
        }
        ProtoMessage.MessagePb.Builder rBuilder = ProtoMessage.MessagePb.newBuilder();
        if (!commands.isEmpty()) {
            rBuilder.addAllCommandList(commands);
        }
        if (!bytesPbList.isEmpty()) {
            rBuilder.addAllBytesPbList(bytesPbList);
        }

        byte[] bytes = rBuilder.build().toByteArray();
        //加密
        if (config.enableEncrypt) {
            bytes = aesCipherUtil.encryptor.encrypt(bytes);
        }

        return bytes;

    }

    //接收服务端发来的字节并做相应处理
    public static boolean receiveServerBytes(CommonConfig config, byte[] responseBody,
                                             ClientSessionManager clientSessionManager, AesCipherUtil aesCipherUtil, BlockingQueue<String> sendCommandQueue,
                                             Map<Integer, ClientBytesSender.SessionIdCallBack> sessionIdCallBackMap) throws Exception {
        if (null == responseBody) {
            return true;
        }
        ProtoMessage.MessagePb rMessagePb;
        try {
            //解密
            if (config.enableEncrypt) {
                responseBody = aesCipherUtil.descriptor.decrypt(responseBody);
            }
            log.debug("收到服务端发回字节数 {}", responseBody.length);
            rMessagePb = ProtoMessage.MessagePb.parseFrom(responseBody);
        } catch (Exception e) {
            log.warn("服务端响应错误  {}", new String(responseBody, StandardCharsets.UTF_8), e);
            Thread.sleep(10000);
            return true;
        }

        boolean isEmpty = true;
        //收命令
        for (String command : rMessagePb.getCommandListList()) {
            log.debug("收到服务端命令 {} ", command);
            char type = command.charAt(0);
            switch (type) {
                case Constant.ScCommands.InitSession -> {
                    //sessionId,initFlag
                    String[] params = command.substring(1).split(Constant.sessionIdJoinFlag);
                    int sessionId = Integer.parseInt(params[0]);
                    int initFlag = Integer.parseInt(params[1]);
                    ClientBytesSender.SessionIdCallBack sessionIdCallBack = sessionIdCallBackMap.remove(initFlag);
                    if (null != sessionIdCallBack) {
                        sessionIdCallBack.cb(sessionId);
                    } else {
                        log.warn("没有对应的SessionIdCallBack {}", sessionIdCallBack);
                    }
                }
                case Constant.ScCommands.CloseSession -> {
                    int sessionId = Integer.parseInt(command.substring(1));
                    ClientSession session = clientSessionManager.getClientSessionBySessionId(sessionId);
                    if (null != session) {
                        clientSessionManager.disposeClientSession(session, "服务端发送关闭命令");
                    }
                }
                case Constant.ScCommands.CheckSessionActive -> {
                    int sessionId = Integer.parseInt(command.substring(1));
                    ClientSession session = clientSessionManager.getClientSessionBySessionId(sessionId);
                    if (null != session) {
                        //session存在，则发送存活消息
                        sendCommandQueue.add(String.valueOf(Constant.SsCommands.ActiveSession) + sessionId);
                    } else {
                        //否则发送关闭消息
                        sendCommandQueue.add(String.valueOf(Constant.SsCommands.CloseSession) + sessionId);
                    }
                }
            }
        }

        //收字节
        List<ProtoMessage.BytesPb> rBytesPbListList = rMessagePb.getBytesPbListList();
        if (!rBytesPbListList.isEmpty()) {
            isEmpty = false;
            for (ProtoMessage.BytesPb bytesPb : rBytesPbListList) {
                ClientSession clientSession = clientSessionManager.getClientSessionBySessionId(bytesPb.getSessionId());
                if (clientSession != null) {
                    clientSession.sendToUser(bytesPb.getBytes().toByteArray());
                } else {
                    //客户端没有这个session，异步等待一下看是否是未初始化完成
                    Thread.startVirtualThread(() -> {
                        ClientSession clientSession1;
                        //每50ms检测一次，30秒后超时
                        for (int i = 0; i < 600; i++) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                continue;
                            }
                            clientSession1 = clientSessionManager.getClientSessionBySessionId(bytesPb.getSessionId());
                            if (null != clientSession1) {
                                clientSession1.sendToUser(bytesPb.getBytes().toByteArray());
                                return;
                            }
                        }
//                        //客户端没有这个session 通知服务端关闭
//                        log.info("sessionId {} 不存在，关闭session", bytesPb.getSessionId());
//                        sendCommandQueue.add(String.valueOf(Constant.SsCommands.CloseSession) + bytesPb.getSessionId());
                    });

                }
            }
        }

        return isEmpty;

    }


}
