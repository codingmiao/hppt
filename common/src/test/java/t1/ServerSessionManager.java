package t1;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author liuyu
 * @date 2023/11/18
 */
@Slf4j
public class ServerSessionManager {

    public static final Map<Integer, ServerSession> serverSessionMap = new ConcurrentHashMap<>();
    public static final BlockingQueue<SessionBytes> serverSessionSendQueue = new ArrayBlockingQueue<>(100000);
    private static List<Integer> closedServerSessions = new LinkedList<>();
    private static final Object closeLock = new Object();

    public static ServerSession createServerSession(int sessionId, ServerPort serverPort) {
        log.info("new ServerSession {}:{} {}", serverPort.getHost(), serverPort.getPort(), sessionId);
        ServerSession serverSession = new ServerSession(serverPort.getHost(), serverPort.getPort(), sessionId);
        serverSessionMap.put(sessionId, serverSession);
        return serverSession;
    }

    public static void disposeServerSession(ServerSession serverSession, String type) {
        serverSession.close();
        log.info("serverSession {} close,type [{}]", serverSession.getSessionId(), type);
        serverSessionMap.remove(serverSession.getSessionId());
        synchronized (closeLock) {
            closedServerSessions.add(serverSession.getSessionId());
        }
    }

    public static List<Integer> fetchClosedServerSessions() {
        if (closedServerSessions.isEmpty()) {
            return null;
        }
        List<Integer> res;
        synchronized (closeLock) {
            res = List.copyOf(closedServerSessions);
            closedServerSessions = new LinkedList<>();
        }
        return res;
    }
}
