package org.wowtools.hppt.cc.service;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.wowtools.hppt.cc.StartCc;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.HttpUtil;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author liuyu
 * @date 2023/12/21
 */
@Slf4j
public class ClientSessionService {

    private static final String talkUri = StartCc.config.serverUrl + "/talk?c=";
    private static long sleepTime = StartCc.config.initSleepTime - StartCc.config.addSleepTime;
    private static long noSleepLimitTime = 0;
    private static final AtomicBoolean sleeping = new AtomicBoolean(false);

    private static final Thread sendThread;


    static {
        sendThread = new Thread(() -> {
            while (true) {
                try {
                    /* 发请求 */
                    boolean isEmpty = sendToSs();

                    /* 睡眠发送线程策略 */
                    if (noSleepLimitTime > System.currentTimeMillis()) {
                        //线程刚刚被唤醒，不睡眠
                        sleepTime = StartCc.config.initSleepTime;
                        log.debug("线程刚刚被唤醒 {}", sleepTime);
                    } else if (isEmpty) {
                        //收发数据包都为空，逐步增加睡眠时间
                        if (sleepTime < StartCc.config.maxSleepTime) {
                            sleepTime += StartCc.config.addSleepTime;
                        }
                        log.debug("收发数据包都为空，逐步增加睡眠时间 {}", sleepTime);
                    } else {
                        sleepTime = StartCc.config.initSleepTime;
                        log.debug("正常包 {}", sleepTime);
                    }
                    if (sleepTime > 0) {
                        sleeping.set(true);
                        try {
                            log.debug("sleep {}", sleepTime);
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            log.info("发送进程被唤醒");
                        } finally {
                            sleeping.set(false);
                        }
                    }
                } catch (Exception e) {
                    log.warn("发消息到服务端发生异常", e);
                    sleeping.set(true);
                    try {
                        Thread.sleep(StartCc.config.maxSleepTime);
                    } catch (InterruptedException e1) {
                        log.info("发送进程被唤醒");
                    } finally {
                        sleeping.set(false);
                    }
                    StartCc.tryLogin();
                }
            }
        });
    }

    public static void start() {
        //启动轮询发送http请求线程
        sendThread.start();
        //起一个线程，定期检查超时session
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(StartCc.config.sessionTimeout);
                    log.debug("定期检查，当前session数 {}", ClientSessionManager.clientSessionMap.size());
                    for (Map.Entry<Integer, ClientSession> entry : ClientSessionManager.clientSessionMap.entrySet()) {
                        ClientSession session = entry.getValue();
                        if (session.isTimeOut()) {
                            ClientSessionManager.disposeClientSession(session, "超时不活跃");
                        }
                    }

                } catch (Exception e) {
                    log.warn("定期检查超时session异常", e);
                }
            }
        }).start();
    }

    /**
     * 发请求
     *
     * @return 是否为空包 即发送和接收都是空
     * @throws Exception Exception
     */
    private static boolean sendToSs() throws Exception {
        boolean isEmpty = true;
        List<ProtoMessage.BytesPb> bytePbList = new LinkedList<>();
        //发字节
        ClientSessionManager.clientSessionMap.forEach((sessionId, clientSession) -> {
            byte[] bytes = clientSession.fetchSendSessionBytes();
            if (bytes != null) {
                bytePbList.add(ProtoMessage.BytesPb.newBuilder()
                        .setSessionId(clientSession.getSessionId())
                        .setBytes(ByteString.copyFrom(bytes))
                        .build()
                );
            }
        });
        if (!bytePbList.isEmpty()) {
            isEmpty = false;
        }
        //发命令
        List<String> commands = new LinkedList<>();
        {
            StringBuilder closeSession = new StringBuilder().append(Constant.CsCommands.CloseSession);
            StringBuilder checkActiveSession = new StringBuilder().append(Constant.CsCommands.CheckSessionActive);

            ClientSessionManager.clientSessionMap.forEach((sessionId, session) -> {
                if (session.isNeedCheckActive()) {
                    checkActiveSession.append(sessionId).append(Constant.sessionIdJoinFlag);
                }
            });

            List<Integer> closedClientSessions = ClientSessionManager.fetchClosedClientSessions();
            if (null != closedClientSessions) {
                for (Integer sessionId : closedClientSessions) {
                    closeSession.append(sessionId).append(Constant.sessionIdJoinFlag);
                }
            }

            if (closeSession.length() > 1) {
                commands.add(closeSession.toString());
            }
            if (checkActiveSession.length() > 1) {
                commands.add(checkActiveSession.toString());
            }
        }
        cutSendToSs(bytePbList, commands);
        return isEmpty;
    }

    //如果请求体过大,会出现413 Request Entity Too Large,拆分一下发送
    private static boolean cutSendToSs(List<ProtoMessage.BytesPb> bytePbs, List<String> commands) throws Exception {
        List<ProtoMessage.BytesPb> bytesPbList = new LinkedList<>();
        List<String> commandList = new LinkedList<>();
        int requestBodyLength = 0;

        while (!bytePbs.isEmpty()) {
            if (bytePbs.getFirst().getBytes().size() > StartCc.config.maxSendBodySize) {
                //单个bytePb包含的字节数就超过限制了，那把bytes拆小
                ProtoMessage.BytesPb bytePb = bytePbs.removeFirst();
                byte[][] splitBytes = BytesUtil.splitBytes(bytePb.getBytes().toByteArray(), StartCc.config.maxSendBodySize);
                for (int i = splitBytes.length - 1; i >= 0; i--) {
                    bytePbs.addFirst(ProtoMessage.BytesPb.newBuilder()
                            .setBytes(ByteString.copyFrom(splitBytes[i]))
                            .setSessionId(bytePb.getSessionId())
                            .build());
                }
            } else {
                requestBodyLength += bytePbs.getFirst().getBytes().size();
                if (requestBodyLength > StartCc.config.maxSendBodySize) {
                    break;
                }
                bytesPbList.add(bytePbs.removeFirst());
            }

        }

        while (!commands.isEmpty()) {
            if (commands.getFirst().length() > StartCc.config.maxSendBodySize) {
                throw new RuntimeException("maxSendBodySize 的值过小导致无法发送命令，请调整");
            }
            requestBodyLength += commands.getFirst().length();//注意，这里限定了命令只能是ASCII字符
            if (requestBodyLength > StartCc.config.maxSendBodySize) {
                break;
            }
            commandList.add(commands.removeFirst());
        }
        log.debug("requestBodyLength {}", requestBodyLength);
        boolean isEmpty = subSendToSs(bytesPbList, commandList);
        if (bytePbs.isEmpty() && commands.isEmpty()) {
            return isEmpty;
        } else {
            return cutSendToSs(bytePbs, commands);
        }
    }


    //发送和接收
    private static boolean subSendToSs(List<ProtoMessage.BytesPb> bytesPbList, List<String> commandList) throws Exception {
        boolean isEmpty = true;

        ProtoMessage.MessagePb.Builder messagePbBuilder = ProtoMessage.MessagePb.newBuilder();
        if (!bytesPbList.isEmpty()) {
            messagePbBuilder.addAllBytesPbList(bytesPbList);
        }
        if (!commandList.isEmpty()) {
            messagePbBuilder.addAllCommandList(commandList);
        }
        byte[] requestBody = messagePbBuilder.build().toByteArray();

        //压缩、加密
        if (StartCc.config.enableCompress) {
            requestBody = BytesUtil.compress(requestBody);
        }
        if (StartCc.config.enableEncrypt) {
            requestBody = StartCc.aesCipherUtil.encryptor.encrypt(requestBody);
        }

        log.debug("发送数据 bytesPbs {} commands {} 字节数 {}", bytesPbList.size(), commandList.size(), requestBody.length);

        Response response;
        if (log.isDebugEnabled()) {
            long t = System.currentTimeMillis();
            response = HttpUtil.doPost(talkUri + StartCc.loginCode, requestBody);
            log.debug("发送http请求耗时 {}", System.currentTimeMillis() - t);
        } else {
            response = HttpUtil.doPost(talkUri + StartCc.loginCode, requestBody);
        }


        byte[] responseBody;
        try {
            if ("not_login".equals(response.headers().get("err"))) {
                //发现未登录标识，尝试重新登录
                StartCc.tryLogin();
                return true;
            }
            assert response.body() != null;
            responseBody = response.body().bytes();
        } finally {
            response.close();
        }

        ProtoMessage.MessagePb rMessagePb;
        byte[] responseBody0 = responseBody;
        try {
            //解密、解压
            if (StartCc.config.enableEncrypt) {
                responseBody = StartCc.aesCipherUtil.descriptor.decrypt(responseBody);
            }
            if (StartCc.config.enableCompress) {
                responseBody = BytesUtil.decompress(responseBody);
            }
            log.debug("收到服务端发回字节数 {}", responseBody.length);
            rMessagePb = ProtoMessage.MessagePb.parseFrom(responseBody);
        } catch (Exception e) {
            log.warn("服务端响应错误  [{}]", new String(responseBody0, StandardCharsets.UTF_8), e);
            StartCc.tryLogin();
            return true;
        }

        //收命令
        for (String command : rMessagePb.getCommandListList()) {
            log.debug("收到服务端命令 {} ", command);
            char type = command.charAt(0);
            switch (type) {
                case Constant.CcCommands.CloseSession -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        if (strSessionId.isEmpty()) {
                            continue;
                        }
                        ClientSession clientSession = ClientSessionManager.clientSessionMap.get(Integer.parseInt(strSessionId));
                        if (null != clientSession) {
                            ClientSessionManager.disposeClientSession(clientSession, "服务端发送关闭命令");
                        }
                    }
                }
                case Constant.CcCommands.ActiveSession -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        ClientSession session = ClientSessionManager.getClientSession(Integer.parseInt(strSessionId));
                        if (session != null) {
                            session.activeSession();
                        }
                    }
                }
                case Constant.CcCommands.CreateSession -> {
                    //2sessionId host port
                    String[] strs = command.substring(1).split(" ");
                    int sessionId = Integer.parseInt(strs[0]);
                    String host = strs[1];
                    int port = Integer.parseInt(strs[2]);
                    ClientSessionManager.createClientSession(host, port, sessionId);
                }
            }
        }

        //收字节
        List<ProtoMessage.BytesPb> rBytesPbListList = rMessagePb.getBytesPbListList();
        if (!rBytesPbListList.isEmpty()) {
            isEmpty = false;
            for (ProtoMessage.BytesPb bytesPb : rBytesPbListList) {
                ClientSession clientSession = ClientSessionManager.clientSessionMap.get(bytesPb.getSessionId());
                if (clientSession != null) {
                    clientSession.putBytes(bytesPb.getBytes().toByteArray());
                }
            }
            noSleepLimitTime = System.currentTimeMillis() + StartCc.config.awakenTime;
        }

        return isEmpty;
    }

    /**
     * 唤醒发送线程
     */
    public static void awakenSendThread() {
        if (sleeping.get()) {
            try {
                sleepTime = StartCc.config.initSleepTime;
                sendThread.interrupt();
                log.info("唤醒发送线程");
            } catch (Exception e) {
                log.warn("唤醒线程异常", e);
            }
        }
    }

}
