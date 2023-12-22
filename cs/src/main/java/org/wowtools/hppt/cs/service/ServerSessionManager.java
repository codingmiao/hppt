package org.wowtools.hppt.cs.service;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.Constant;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyu
 * @date 2023/12/20
 */
@Slf4j
public class ServerSessionManager {
    private static final AtomicInteger sessionIdIndex = new AtomicInteger();

    private static final Map<ChannelHandlerContext, ServerSession> serviceSessionMap = new ConcurrentHashMap<>();

    private static final Map<String, List<Integer>> needClosedSessionMap = new HashMap<>();
    private static final Map<String, List<String>> needCreateSessionCommandMap = new HashMap<>();

    public static void createServerSession(ChannelHandlerContext ctx, String clientId, String remoteHost, int remotePort) {
        int sessionId = sessionIdIndex.addAndGet(1);
        ServerSession serverSession = new ServerSession(sessionId, clientId, remoteHost, remotePort, ctx);
        serviceSessionMap.put(ctx, serverSession);

        List<String> needCreateSessionCommandList;
        synchronized (needCreateSessionCommandMap) {
            needCreateSessionCommandList = needCreateSessionCommandMap.computeIfAbsent(serverSession.getClientId(), (id) -> new LinkedList<>());
        }
        //2sessionId host port

        String createCommand = new StringBuilder().append(Constant.CcCommands.CreateSession)
                .append(sessionId).append(" ").append(remoteHost).append(" ").append(remotePort).toString();
        synchronized (needCreateSessionCommandList) {
            needCreateSessionCommandList.add(createCommand);
        }
        log.info("create session {} {}:{}", sessionId, remoteHost, remotePort);
    }

    public static ServerSession getServerSession(ChannelHandlerContext ctx) {
        return serviceSessionMap.get(ctx);
    }

    public static void disposeServerSession(ServerSession serverSession, String type) {
        try {
            serverSession.close();
        } catch (Exception e) {
            log.warn("close session error", e);
        }
        log.info("serverSession {} close,type [{}]", serverSession.getSessionId(), type);
        if (null != serviceSessionMap.remove(serverSession.getChannelHandlerContext())) {
            List<Integer> needClosedSessionList;
            synchronized (needClosedSessionMap) {
                needClosedSessionList = needClosedSessionMap.computeIfAbsent(serverSession.getClientId(), (id) -> new LinkedList<>());
            }
            synchronized (needClosedSessionList) {
                needClosedSessionList.add(serverSession.getSessionId());
            }
        }
    }

    public static Map<Integer, ServerSession> getServerSessionByClientId(String clientId) {
        Map<Integer, ServerSession> res = new HashMap<>();
        serviceSessionMap.forEach((k, session) -> {
            if (clientId.equals(session.getClientId())) {
                res.put(session.getSessionId(), session);
            }
        });
        return res;
    }

    public static List<Integer> fetchClosedServerSessions(String clientId) {
        List<Integer> needClosedSessionList;
        synchronized (needClosedSessionMap) {
            needClosedSessionList = needClosedSessionMap.get(clientId);
        }
        if (needClosedSessionList == null) {
            return null;
        }
        List<Integer> res;
        synchronized (needClosedSessionList) {
            res = List.copyOf(needClosedSessionList);
            needClosedSessionList.clear();
        }
        return res;
    }

    public static List<String> fetchCreateCommands(String clientId) {
        List<String> needCreateSessionCommandList;
        synchronized (needCreateSessionCommandMap) {
            needCreateSessionCommandList = needCreateSessionCommandMap.get(clientId);
        }
        if (needCreateSessionCommandList == null) {
            return null;
        }
        List<String> res;
        synchronized (needCreateSessionCommandList) {
            if (needCreateSessionCommandList.isEmpty()) {
                return null;
            }
            res = List.copyOf(needCreateSessionCommandList);
            needCreateSessionCommandList.clear();
        }
        return res;
    }
}
