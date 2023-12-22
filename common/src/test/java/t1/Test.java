package t1;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class Test {


    public static void main(String[] args) throws Exception {
        log.info("-----------------------start");
        ServerPort serverPort = new ServerPort("mycentos7", 22);
        ClientPort clientPort = new ClientPort(11001, (_clientPort, channelHandlerContext) -> {
            /* 1、用户发起连接，ClientPort收到连接信息后向ServerPort发起连接请求，ServerPort返回一个唯一的sessionId; */
            int sessionId = createSessionId();
            log.info("新会话建立 {}", sessionId);
            /* 2、ClientPort根据sessionId新建一个ClientSession，ServerPort根据sessionId新建一个ServerSession；*/
            ClientSession clientSession = ClientSessionManager.createClientSession(sessionId, _clientPort, channelHandlerContext);
            ServerSession serverSession = ServerSessionManager.createServerSession(sessionId, serverPort);
            /* 3、用户->ClientSession->HTTP服务 通信->ServerSession->真实端口的通道建立完成。*/
        });

        new Thread(() -> {
            //读取clientSendQueue的值分发给serverSession 真实环境改为http
            while (true) {
                try {
                    List<SessionBytes> sessionBytesList = new LinkedList<>();
                    ClientSessionManager.clientSessionSendQueue.drainTo(sessionBytesList);
                    if (sessionBytesList.isEmpty()) {
                        Thread.sleep(10);
                    } else {
                        for (SessionBytes sessionBytes : sessionBytesList) {
                            ServerSession session = ServerSessionManager.serverSessionMap.get(sessionBytes.getSessionId());
                            session.putBytes(sessionBytes.getBytes());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }).start();
        new Thread(() -> {
            //读取serverSendQueue的值分发给clientSession 真实环境改为http
            while (true) {
                try {
                    List<SessionBytes> sessionBytesList = new LinkedList<>();
                    ServerSessionManager.serverSessionSendQueue.drainTo(sessionBytesList);
                    if (sessionBytesList.isEmpty()) {
                        Thread.sleep(10);
                    } else {
                        for (SessionBytes sessionBytes : sessionBytesList) {
                            ClientSession session = ClientSessionManager.clientSessionMap.get(sessionBytes.getSessionId());
                            session.putBytes(sessionBytes.getBytes());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(() -> {
            //定期查看客户端关闭的session，并通知给服务端 真实环境改为http
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                List<Integer> closedIds = ClientSessionManager.fetchClosedClientSessions();
                if (null == closedIds) {
                    continue;
                }
                for (Integer closedId : closedIds) {
                    ServerSession serverSession = ServerSessionManager.serverSessionMap.get(closedId);
                    if (null != serverSession) {
                        ServerSessionManager.disposeServerSession(serverSession, "同步客户端关闭");
                    }
                }
            }
        }).start();

        new Thread(() -> {
            //定期查看服务端关闭的session，并通知给客户端 真实环境改为http
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                List<Integer> closedIds = ServerSessionManager.fetchClosedServerSessions();
                if (null == closedIds) {
                    continue;
                }
                for (Integer closedId : closedIds) {
                    ClientSession clientSession = ClientSessionManager.clientSessionMap.get(closedId);
                    if (null != clientSession) {
                        ClientSessionManager.disposeClientSession(clientSession, "同步服务端关闭");
                    }
                }
            }
        }).start();

    }


    private static final AtomicInteger sessionIdx = new AtomicInteger();

    private static int createSessionId() {
        return sessionIdx.addAndGet(1);
    }


}
