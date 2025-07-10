package org.wowtools.hppt.common.client;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.pojo.TalkMessage;
import org.wowtools.hppt.common.util.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2024/2/1
 */
@Slf4j
public class ClientTalker {

    public interface CommandCallBack {
        void cb(char type, String param) throws Exception;
    }

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
                                                BufferPool<String> sendCommandQueue, BufferPool<SessionBytes> sendBytesQueue,
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
        List<SessionBytes> bytesPbList = new LinkedList<>();
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
            bytesPbList.add(bytes);
        } while (true);

        if (sendBodySize == 0) {
            return null;
        }
        if (DebugConfig.OpenSerialNumber) {
            for (SessionBytes sessionBytes : bytesPbList) {
                log.debug("ClientTalker收集 >sessionBytes-SerialNumber {}", sessionBytes.getSerialNumber());
            }
        }

        TalkMessage talkMessage = new TalkMessage(bytesPbList, commands);
        if (DebugConfig.OpenSerialNumber) {
            log.debug("ClientTalker组装 >talkMessage-SerialNumber {}", talkMessage.getSerialNumber());
        }
        byte[] bytes = talkMessage.toProto().build().toByteArray();
        //加密
        if (config.enableEncrypt) {
            bytes = aesCipherUtil.encryptor.encrypt(bytes);
        }

        return bytes;

    }

    //接收服务端发来的字节并做相应处理
    public static boolean receiveServerBytes(CommonConfig config, byte[] responseBody,
                                             ClientSessionManager clientSessionManager, AesCipherUtil aesCipherUtil, BufferPool<String> sendCommandQueue,
                                             Map<Integer, ClientBytesSender.SessionIdCallBack> sessionIdCallBackMap, CommandCallBack commandCallBack) throws Exception {
        if (null == responseBody) {
            return true;
        }
        TalkMessage talkMessage;
        try {
            //解密
            if (config.enableEncrypt) {
                responseBody = aesCipherUtil.descriptor.decrypt(responseBody);
            }
            log.debug("收到服务端发回字节数 {}", responseBody.length);
            talkMessage = new TalkMessage(responseBody);
            if (DebugConfig.OpenSerialNumber) {
                log.debug("ClientTalker收到服务端发回 <talkMessage-SerialNumber {}", talkMessage.getSerialNumber());
            }
        } catch (Exception e) {
            log.warn("服务端响应错误  {}", new String(responseBody, StandardCharsets.UTF_8), e);
            Thread.sleep(10000);
            return true;
        }

        boolean isEmpty = true;
        //收命令
        if (null != talkMessage.getCommands() && !talkMessage.getCommands().isEmpty()) {
            for (String command : talkMessage.getCommands()) {
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
                            log.warn("没有对应的SessionIdCallBack {}", sessionId);
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
                    default -> {
                        if (null != commandCallBack) {
                            try {
                                commandCallBack.cb(type, command.substring(1));
                            } catch (Exception e) {
                                log.warn("服务端命令处理错误 {}", command, e);
                            }
                        }
                    }
                }
            }
        }


        //收字节
        List<SessionBytes> sessionBytes = talkMessage.getSessionBytes();
        if (null != sessionBytes && !sessionBytes.isEmpty()) {
            isEmpty = false;
            for (SessionBytes sessionByte : sessionBytes) {
                ClientSession clientSession = clientSessionManager.getClientSessionBySessionId(sessionByte.getSessionId());
                if (clientSession != null) {
                    clientSession.sendToUser(sessionByte.getBytes());
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
                            clientSession1 = clientSessionManager.getClientSessionBySessionId(sessionByte.getSessionId());
                            if (null != clientSession1) {
                                clientSession1.sendToUser(sessionByte.getBytes());
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
