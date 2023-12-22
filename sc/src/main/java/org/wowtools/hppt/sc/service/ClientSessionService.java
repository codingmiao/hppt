package org.wowtools.hppt.sc.service;


import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.common.util.HttpUtil;
import org.wowtools.hppt.sc.StartSc;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author liuyu
 * @date 2023/11/25
 */
@Slf4j
public class ClientSessionService {

    private static final String initUri = StartSc.config.serverUrl + "/init";
    private static final String talkUri = StartSc.config.serverUrl + "/talk?c=" + StartSc.loginCode;
    private static long sleepTime = StartSc.config.initSleepTime - StartSc.config.addSleepTime;
    private static long noSleepLimitTime = 0;
    private static final AtomicBoolean sleeping = new AtomicBoolean(false);

    private static final Thread sendThread;

    //服务端发来的查询客户端sessionId是否还存在的id队列
    private static final BlockingQueue<Integer> serverQuerySessionIdQueue = new ArrayBlockingQueue<>(10000);


    /**
     * 拉起一个服务端会话，返回服务端会话id
     *
     * @param remoteHost remoteHost
     * @param remotePort remotePort
     * @return 服务端会话id
     */
    public static int initServerSession(String remoteHost, int remotePort) {
        String body = StartSc.loginCode + ":" + remoteHost + ":" + remotePort;
        String res;
        try (Response response = HttpUtil.doPost(initUri, body.getBytes(StandardCharsets.UTF_8))){
            res = response.body().string();
        } catch (Exception e) {
            throw new RuntimeException("获取sessionId异常", e);
        }
        return Integer.parseInt(res);
    }

    static {
        //读取clientSendQueue的值，发送http请求分发到ss端
        sendThread = new Thread(() -> {
            while (true) {
                try {
                    /* 发请求 */
                    boolean isEmpty = sendToSs();

                    /* 睡眠发送线程策略 */
                    if (ClientSessionManager.clientSessionMap.isEmpty()
                            && serverQuerySessionIdQueue.isEmpty()
                            && ClientSessionManager.notHaveClosedClientSession()
                    ) {
                        //无客户端，长睡直到被唤醒
                        log.info("无客户端连接，且无命令要发送，睡眠发送线程");
                        sleepTime = Long.MAX_VALUE;

                    } else if (noSleepLimitTime > System.currentTimeMillis()) {
                        //线程刚刚被唤醒，不睡眠
                        sleepTime = StartSc.config.initSleepTime;
                        log.debug("线程刚刚被唤醒 {}", sleepTime);
                    } else if (isEmpty) {
                        //收发数据包都为空，逐步增加睡眠时间
                        if (sleepTime < StartSc.config.maxSleepTime) {
                            sleepTime += StartSc.config.addSleepTime;
                        }
                        log.debug("收发数据包都为空，逐步增加睡眠时间 {}", sleepTime);
                    } else {
                        sleepTime = StartSc.config.initSleepTime;
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
                        Thread.sleep(StartSc.config.maxSleepTime);
                    } catch (InterruptedException e1) {
                        log.info("发送进程被唤醒");
                    } finally {
                        sleeping.set(false);
                    }
                }
            }

        });
        sendThread.start();
    }


    /**
     * 唤醒发送线程
     */
    public static void awakenSendThread() {
        if (sleeping.get()) {
            try {
                sleepTime = StartSc.config.initSleepTime;
                noSleepLimitTime = System.currentTimeMillis() + StartSc.config.addSleepTime;
                sendThread.interrupt();
                log.info("唤醒发送线程");
            } catch (Exception e) {
                log.warn("唤醒线程异常", e);
            }
        }
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
        ClientSessionManager.clientSessionMap.forEach((clientId, clientSession) -> {
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
            StringBuilder closeSession = new StringBuilder().append(Constant.SsCommands.CloseSession);
            StringBuilder activeSession = new StringBuilder().append(Constant.SsCommands.ActiveSession);

            List<Integer> sessionIds = new LinkedList<>();
            serverQuerySessionIdQueue.drainTo(sessionIds);
            for (Integer sessionId : sessionIds) {
                if (ClientSessionManager.clientSessionMap.containsKey(sessionId)) {
                    activeSession.append(sessionId).append(Constant.sessionIdJoinFlag);
                } else {
                    closeSession.append(sessionId).append(Constant.sessionIdJoinFlag);
                }
            }

            List<Integer> closedClientSessions = ClientSessionManager.fetchClosedClientSessions();
            if (null != closedClientSessions) {
                for (Integer sessionId : closedClientSessions) {
                    closeSession.append(sessionId).append(Constant.sessionIdJoinFlag);
                }
            }

            if (closeSession.length() > 1) {
                commands.add(closeSession.toString());
            }
            if (activeSession.length() > 1) {
                commands.add(activeSession.toString());
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
            if (bytePbs.getFirst().getBytes().size() > StartSc.config.maxSendBodySize) {
                //单个bytePb包含的字节数就超过限制了，那把bytes拆小
                ProtoMessage.BytesPb bytePb = bytePbs.removeFirst();
                byte[][] splitBytes = BytesUtil.splitBytes(bytePb.getBytes().toByteArray(), StartSc.config.maxSendBodySize);
                for (int i = splitBytes.length - 1; i >= 0; i--) {
                    bytePbs.addFirst(ProtoMessage.BytesPb.newBuilder()
                            .setBytes(ByteString.copyFrom(splitBytes[i]))
                            .setSessionId(bytePb.getSessionId())
                            .build());
                }
            } else {
                requestBodyLength += bytePbs.getFirst().getBytes().size();
                if (requestBodyLength > StartSc.config.maxSendBodySize) {
                    break;
                }
                bytesPbList.add(bytePbs.removeFirst());
            }

        }

        while (!commands.isEmpty()) {
            if (commands.getFirst().length() > StartSc.config.maxSendBodySize) {
                throw new RuntimeException("maxSendBodySize 的值过小导致无法发送命令，请调整");
            }
            requestBodyLength += commands.getFirst().length();//注意，这里限定了命令只能是ASCII字符
            if (requestBodyLength > StartSc.config.maxSendBodySize) {
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
        if (StartSc.config.enableCompress) {
            requestBody = BytesUtil.compress(requestBody);
        }
        if (StartSc.config.enableEncrypt) {
            requestBody = StartSc.aesCipherUtil.encryptor.encrypt(requestBody);
        }

        log.debug("发送数据 bytesPbs {} commands {} 字节数 {}", bytesPbList.size(), commandList.size(), requestBody.length);
        Response response;

        if (log.isDebugEnabled()) {
            long t = System.currentTimeMillis();
            response = HttpUtil.doPost(talkUri, requestBody);
            log.debug("发送http请求耗时 {}", System.currentTimeMillis() - t);
        } else {
            response = HttpUtil.doPost(talkUri, requestBody);
        }

        //响应结果转protobuf再从pbf中取出来加入对应的队列中
        assert response.body() != null;
        byte[] responseBody = response.body().bytes();
        response.close();

        ProtoMessage.MessagePb rMessagePb;
        try {
            //解密、解压
            if (StartSc.config.enableEncrypt) {
                responseBody = StartSc.aesCipherUtil.descriptor.decrypt(responseBody);
            }
            if (StartSc.config.enableCompress) {
                responseBody = BytesUtil.decompress(responseBody);
            }
            log.debug("收到服务端发回字节数 {}", responseBody.length);
            rMessagePb = ProtoMessage.MessagePb.parseFrom(responseBody);
        } catch (Exception e) {
            log.warn("服务端响应错误  {}", new String(responseBody, StandardCharsets.UTF_8), e);
            Thread.sleep(10000);
            return isEmpty;
        }

        //收字节
        List<ProtoMessage.BytesPb> rBytesPbListList = rMessagePb.getBytesPbListList();
        if (!rBytesPbListList.isEmpty()) {
            isEmpty = false;
            for (ProtoMessage.BytesPb bytesPb : rBytesPbListList) {
                ClientSession clientSession = ClientSessionManager.clientSessionMap.get(bytesPb.getSessionId());
                if (clientSession != null) {
                    clientSession.putBytes(bytesPb.getBytes().toByteArray());
                } else {
                    //客户端没有这个session 通知服务端关闭
                    serverQuerySessionIdQueue.put(bytesPb.getSessionId());
                }
            }
        }

        //收命令
        for (String command : rMessagePb.getCommandListList()) {
            log.debug("收到服务端命令 {} ", command);
            char type = command.charAt(0);
            switch (type) {
                case Constant.ScCommands.CloseSession -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        ClientSession clientSession = ClientSessionManager.clientSessionMap.get(Integer.parseInt(strSessionId));
                        if (null != clientSession) {
                            ClientSessionManager.disposeClientSession(clientSession, "服务端发送关闭命令");
                        }
                    }
                }
                case Constant.ScCommands.CheckSessionActive -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        serverQuerySessionIdQueue.put(Integer.parseInt(strSessionId));
                    }
                }
            }
        }

        return isEmpty;
    }


}
