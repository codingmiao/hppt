package org.wowtools.hppt.cc.service;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author liuyu
 * @date 2023/12/21
 */
@Slf4j
public class ClientSessionManager {
    public static final Map<Integer, ClientSession> clientSessionMap = new ConcurrentHashMap<>();
    private static List<Integer> closedClientSessions = new LinkedList<>();

    private static final Object closeLock = new Object();
    public static void createClientSession(String host, int port, int sessionId) {
        ClientSession clientSession = new ClientSession(host, port, sessionId);
        clientSessionMap.put(sessionId, clientSession);
    }

    public static void disposeClientSession(ClientSession clientSession, String type) {
        clientSession.close();
        log.info("ClientSession {} close,type [{}]", clientSession.getSessionId(), type);
        if (null != clientSessionMap.remove(clientSession.getSessionId())) {
            synchronized (closeLock) {
                closedClientSessions.add(clientSession.getSessionId());
            }
            ClientSessionService.awakenSendThread();
        }
    }

    public static ClientSession getClientSession(int sessionId) {
        return clientSessionMap.get(sessionId);
    }

    public static boolean notHaveClosedClientSession() {
        synchronized (closeLock) {
            return closedClientSessions.isEmpty();
        }
    }

    public static List<Integer> fetchClosedClientSessions() {
        if (closedClientSessions.isEmpty()) {
            return null;
        }
        List<Integer> res;
        synchronized (closeLock) {
            res = List.copyOf(closedClientSessions);
            closedClientSessions = new LinkedList<>();
        }
        return res;
    }
}
