package org.wowtools.hppt.ss.service;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.Constant;
import org.wowtools.hppt.ss.StartSs;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.wowtools.hppt.ss.service.ServerSessionManager.serverSessionMap;

/**
 * @author liuyu
 * @date 2023/11/25
 */
@Slf4j
public class ServerSessionService {
    private static final AtomicInteger sessionIdBuilder = new AtomicInteger();


    //需要发到客户端检查活跃性的sessionId <clientId,List<sessionId>>
    private static final Map<String, List<Integer>> needCheckActiveServerSessionMap = new ConcurrentHashMap<>();


    /**
     * 新建会话
     *
     * @param clientId   客户端id
     * @param remoteHost 真实端口的host
     * @param remotePort 真实端口
     * @return 会话id
     */
    public static int initSession(String clientId, String remoteHost, int remotePort) {
        int sessionId = sessionIdBuilder.addAndGet(1);
        ServerPort serverPort = new ServerPort(remoteHost, remotePort);
        ServerSession serverSession = ServerSessionManager.createServerSession(clientId, sessionId, serverPort);
        serverSessionMap.put(sessionId, serverSession);
        return sessionId;
    }

    /**
     * 接收客户端传来的消息，并将服务端积攒的bytes和命令整理成消息返回去
     *
     * @param clientId     客户端id
     * @param inputMessage 客户端发来的消息
     * @return 回发消息
     */
    public static ProtoMessage.MessagePb talk(String clientId, ProtoMessage.MessagePb inputMessage) {
        List<Integer> needCheckActiveServerSessionIds = new LinkedList<>();
        List<Integer> needCloseClientSessionIds = new LinkedList<>();
        List<ServerSession> serverSessions = new LinkedList<>();
        serverSessionMap.forEach((sessionId, serverSession) -> {
            if (serverSession.getClientId().equals(clientId)) {
                serverSessions.add(serverSession);
            }
        });
        /* 发消息 */
        //发bytes
        for (ProtoMessage.BytesPb bytesPb : inputMessage.getBytesPbListList()) {
            ServerSession severSession = serverSessionMap.get(bytesPb.getSessionId());
            if (null == severSession) {
                //服务端已经没有这个session了，给客户端发关闭命令
                needCloseClientSessionIds.add(bytesPb.getSessionId());
            } else {
                severSession.putBytes(bytesPb.getBytes().toByteArray());
            }
        }

        //发命令
        for (String command : inputMessage.getCommandListList()) {
            log.debug("收到客户端命令 {} ", command);
            char type = command.charAt(0);
            switch (type) {
                case Constant.SsCommands.CloseSession -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        ServerSession serverSession = serverSessionMap.get(Integer.parseInt(strSessionId));
                        if (null != serverSession) {
                            ServerSessionManager.disposeServerSession(serverSession, "客户端发送关闭命令");
                        }
                    }
                }
                case Constant.SsCommands.ActiveSession -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        ServerSession serverSession = serverSessionMap.get(Integer.parseInt(strSessionId));
                        if (null != serverSession) {
                            serverSession.activeSession();
                        }
                    }
                }
            }
        }

        /* 取消息 */
        //取bytes
        List<ProtoMessage.BytesPb> bytesPbList = new LinkedList<>();
        for (ServerSession serverSession : serverSessions) {
            byte[] bytes = serverSession.fetchSendSessionBytes();
            if (null != bytes) {
                ProtoMessage.BytesPb bytesPb = ProtoMessage.BytesPb.newBuilder()
                        .setBytes(ByteString.copyFrom(bytes))
                        .setSessionId(serverSession.getSessionId())
                        .build();
                bytesPbList.add(bytesPb);
            }
        }
        //取命令
        {
            List<Integer> nList = needCheckActiveServerSessionMap.get(clientId);
            if (null != nList && !nList.isEmpty()) {
                synchronized (nList) {
                    needCheckActiveServerSessionIds.addAll(nList);
                    nList.clear();
                }
            }
            nList = ServerSessionManager.fetchClosedServerSessions(clientId);
            if (null != nList) {
                needCloseClientSessionIds.addAll(nList);
            }
        }
        List<String> commands = new LinkedList<>();
        if (!needCheckActiveServerSessionIds.isEmpty()) {
            StringBuilder sb = new StringBuilder().append(Constant.ScCommands.CheckSessionActive);
            for (Integer id : needCheckActiveServerSessionIds) {
                sb.append(id).append(Constant.sessionIdJoinFlag);
            }
            commands.add(sb.toString());
        }
        if (!needCloseClientSessionIds.isEmpty()) {
            StringBuilder sb = new StringBuilder().append(Constant.ScCommands.CloseSession);
            for (Integer id : needCloseClientSessionIds) {
                sb.append(id).append(Constant.sessionIdJoinFlag);
            }
            commands.add(sb.toString());
        }

        ProtoMessage.MessagePb.Builder rBuilder = ProtoMessage.MessagePb.newBuilder();
        if (!bytesPbList.isEmpty()) {
            rBuilder.addAllBytesPbList(bytesPbList);
        }
        if (!commands.isEmpty()) {
            rBuilder.addAllCommandList(commands);
        }
        return rBuilder.build();
    }


    static {
        //起一个线程，定期检查超时session
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(StartSs.config.sessionTimeout);
                    log.debug("定期检查，当前session数 {}", serverSessionMap.size());
                    for (Map.Entry<Integer, ServerSession> entry : serverSessionMap.entrySet()) {
                        int sessionId = entry.getKey();
                        ServerSession session = entry.getValue();
                        if (session.isTimeOut()) {
                            ServerSessionManager.disposeServerSession(session, "超时不活跃");
                        } else if (session.isNeedCheckActive()) {
                            log.debug("校验session是否活跃 {}", sessionId);
                            List<Integer> list = needCheckActiveServerSessionMap.computeIfAbsent(session.getClientId(), (id) -> new LinkedList<>());
                            synchronized (list) {
                                list.add(sessionId);
                            }
                        }
                    }

                } catch (Exception e) {
                    log.warn("定期检查超时session异常", e);
                }
            }
        }).start();
    }
}
