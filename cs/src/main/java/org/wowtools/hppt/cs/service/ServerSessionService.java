package org.wowtools.hppt.cs.service;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.Constant;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author liuyu
 * @date 2023/12/20
 */
@Slf4j
public class ServerSessionService {

    public static ProtoMessage.MessagePb talk(String clientId, ProtoMessage.MessagePb inputMessage) {
        Map<Integer, ServerSession> sessionMap = ServerSessionManager.getServerSessionByClientId(clientId);
        List<String> commands = new LinkedList<>();
        /* 收消息,发给对应sever session */
        //bytes
        for (ProtoMessage.BytesPb bytesPb : inputMessage.getBytesPbListList()) {
            ServerSession session = sessionMap.get(bytesPb.getSessionId());
            if (session != null) {
                session.putBytes(bytesPb.getBytes().toByteArray());
            }
        }
        //命令
        for (String command : inputMessage.getCommandListList()) {
            log.debug("收到客户端命令 {} ", command);
            char type = command.charAt(0);
            switch (type) {
                case Constant.CsCommands.CloseSession -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        ServerSession serverSession = sessionMap.get(Integer.parseInt(strSessionId));
                        if (null != serverSession) {
                            ServerSessionManager.disposeServerSession(serverSession, "客户端发送关闭命令");
                        }
                    }
                }
                case Constant.CsCommands.CheckSessionActive -> {
                    String[] strSessionIds = command.substring(1).split(Constant.sessionIdJoinFlag);
                    for (String strSessionId : strSessionIds) {
                        ServerSession serverSession = sessionMap.get(Integer.parseInt(strSessionId));
                        if (null != serverSession) {
                            commands.add(Constant.CcCommands.ActiveSession + strSessionId);
                        } else {
                            commands.add(Constant.CcCommands.CloseSession + strSessionId);
                        }
                    }
                }
            }
        }

        /* 发消息，取各个sever session中的消息，返回给客户端 */
        List<ProtoMessage.BytesPb> bytesPbList = new LinkedList<>();
        sessionMap.forEach((sessionId, serverSession) -> {
            byte[] bytes = serverSession.fetchSendSessionBytes();
            if (null != bytes) {
                ProtoMessage.BytesPb bytesPb = ProtoMessage.BytesPb.newBuilder()
                        .setBytes(ByteString.copyFrom(bytes))
                        .setSessionId(serverSession.getSessionId())
                        .build();
                bytesPbList.add(bytesPb);
            }
        });
        //命令
        //关闭会话命令
        List<Integer> closedSessions = ServerSessionManager.fetchClosedServerSessions(clientId);
        if (closedSessions != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(Constant.CcCommands.CloseSession);
            for (Integer id : closedSessions) {
                sb.append(id).append(Constant.sessionIdJoinFlag);
            }
            commands.add(sb.toString());
        }
        //新建会话命令
        List<String> needCreateSessionCommandList = ServerSessionManager.fetchCreateCommands(clientId);
        if (null != needCreateSessionCommandList) {
            commands.addAll(needCreateSessionCommandList);
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
}
