package org.wowtools.hppt.ss.service;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author liuyu
 * @date 2023/11/18
 */
@Slf4j
public class ServerSessionManager {

    public static final Map<Integer, ServerSession> serverSessionMap = new ConcurrentHashMap<>();

    //需要发给客户端让客户端关闭会话的sessionId <clientId,List<sessionId>>
    private static final Map<String, List<Integer>> needClosedSessionMap = new HashMap<>();

    public static ServerSession createServerSession(String clientId, int sessionId, ServerPort serverPort) {
        log.info("new ServerSession {} {}:{} from {}", sessionId, serverPort.getHost(), serverPort.getPort(), clientId);
        ServerSession serverSession = new ServerSession(serverPort.getHost(), serverPort.getPort(), clientId, sessionId);
        serverSessionMap.put(sessionId, serverSession);
        return serverSession;
    }

    public static void disposeServerSession(ServerSession serverSession, String type) {
        try {
            serverSession.close();
        } catch (Exception e) {
            log.warn("close session error", e);
        }
        log.info("serverSession {} close,type [{}]", serverSession.getSessionId(), type);
        if (null != serverSessionMap.remove(serverSession.getSessionId())) {
            List<Integer> needClosedSessionList;
            synchronized (needClosedSessionMap) {
                needClosedSessionList = needClosedSessionMap.computeIfAbsent(serverSession.getClientId(), (id) -> new LinkedList<>());
            }
            synchronized (needClosedSessionList) {
                needClosedSessionList.add(serverSession.getSessionId());
            }
        }

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
}
