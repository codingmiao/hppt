package t1;

import io.netty.channel.ChannelHandlerContext;
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
public class ClientSessionManager {
    public static final Map<Integer, ClientSession> clientSessionMap = new ConcurrentHashMap<>();
    public static final BlockingQueue<SessionBytes> clientSessionSendQueue = new ArrayBlockingQueue<>(100000);

    private static List<Integer> closedClientSessions = new LinkedList<>();

    private static final Object closeLock = new Object();

    public static ClientSession createClientSession(int sessionId, ClientPort clientPort, ChannelHandlerContext channelHandlerContext) {
        ClientSession clientSession = clientPort.createClientSession(sessionId, channelHandlerContext);
        clientSessionMap.put(sessionId, clientSession);
        return clientSession;
    }

    public static void disposeClientSession(ClientSession clientSession, String type) {
        clientSession.close();
        log.info("ClientSession {} close,type [{}]", clientSession.getSessionId(), type);
        clientSessionMap.remove(clientSession.getSessionId());
        synchronized (closeLock) {
            closedClientSessions.add(clientSession.getSessionId());
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
