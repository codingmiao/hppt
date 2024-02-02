package org.wowtools.hppt.common.server;

import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.util.AesCipherUtil;
import org.wowtools.hppt.common.util.BytesUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author liuyu
 * @date 2023/12/18
 */
public class LoginClientService {


    public static final class Client {
        public final String clientId;
        public final AesCipherUtil aesCipherUtil;

        private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();

        private final BlockingQueue<SessionBytes> sessionBytesQueue = new LinkedBlockingQueue<>();

        public Client(String clientId, AesCipherUtil aesCipherUtil) {
            this.clientId = clientId;
            this.aesCipherUtil = aesCipherUtil;
        }

        //添加一条向客户端发送的命令
        public void addCommand(String cmd) {
            commandQueue.add(cmd);
        }

        //取出所有需要向客户端发送的命令 无命令则返回null
        public List<String> fetchCommands() {
            if (commandQueue.isEmpty()) {
                return null;
            }
            List<String> res = new LinkedList<>();
            commandQueue.drainTo(res);
            return res;
        }

        //取出所有需要向客户端发送的命令 无命令则阻塞
        public List<String> fetchCommandsBlocked() {
            List<String> res = new LinkedList<>();
            res.add(takeCommand());
            if (commandQueue.isEmpty()) {
                return res;
            }
            commandQueue.drainTo(res);
            return res;
        }

        //取出一条命令 无命令则阻塞
        public String takeCommand() {
            try {
                return commandQueue.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //添加一条向客户端发送的bytes
        public void addBytes(int sessionId, byte[] bytes) {
            sessionBytesQueue.add(new SessionBytes(sessionId, bytes));
        }

        //取出所有需要向客户端发送的bytes 取出的bytes会按相同sessionId进行整合 无bytes则返回null
        public List<SessionBytes> fetchBytes() {
            if (sessionBytesQueue.isEmpty()) {
                return null;
            }
            List<SessionBytes> bytesList = new LinkedList<>();
            sessionBytesQueue.drainTo(bytesList);
            return merge(bytesList);
        }

        //取出所有需要向客户端发送的bytes 取出的bytes会按相同sessionId进行整合 无bytes则阻塞
        public List<SessionBytes> fetchBytesBlocked() {
            List<SessionBytes> bytesList = new LinkedList<>();
            bytesList.add(takeBytes());
            if (sessionBytesQueue.isEmpty()) {
                return bytesList;
            }
            sessionBytesQueue.drainTo(bytesList);
            return merge(bytesList);
        }

        private static List<SessionBytes> merge(List<SessionBytes> bytesList) {
            Map<Integer, List<byte[]>> bytesMap = new HashMap<>();
            for (SessionBytes bytes : bytesList) {
                bytesMap.computeIfAbsent(bytes.getSessionId(), (r) -> new LinkedList<>())
                        .add(bytes.getBytes());
            }
            List<SessionBytes> res = new ArrayList<>(bytesMap.size());
            bytesMap.forEach((sessionId, bytes) -> {
                res.add(new SessionBytes(sessionId, BytesUtil.merge(bytes)));
            });
            return res;
        }

        //取出一条bytes 无bytes则阻塞
        public SessionBytes takeBytes() {
            try {
                return sessionBytesQueue.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private final Map<String, Client> loginClients = new HashMap<>();

    private final String[] allowClientIds;

    public LoginClientService(String[] allowClientIds) {
        this.allowClientIds = allowClientIds;
    }

    /**
     * @param allowClientIds 允许的clientId
     */
    public LoginClientService(Collection<String> allowClientIds) {
        this.allowClientIds = new String[allowClientIds.size()];
        allowClientIds.toArray(this.allowClientIds);
    }

    /**
     * 传入的code能解密出客户端id，则登录成功
     *
     * @param code loginCode
     * @return 是否登录成功
     */
    public boolean login(String code) {
        byte[] bytesCode = BytesUtil.base642bytes(code);
        for (String clientId : allowClientIds) {
            AesCipherUtil aesCipherUtil = new AesCipherUtil(clientId, System.currentTimeMillis());
            try {
                if (new String(aesCipherUtil.descriptor.decrypt(bytesCode), StandardCharsets.UTF_8).equals(clientId)) {
                    synchronized (loginClients) {
                        //把已登录的同id客户端踢掉
                        LinkedList<String> r = new LinkedList<>();
                        loginClients.forEach((c, client) -> {
                            if (client.clientId.equals(clientId)) {
                                r.add(c);
                            }
                        });
                        for (String s : r) {
                            loginClients.remove(s);
                        }
                        //放入
                        loginClients.put(code, new Client(clientId, aesCipherUtil));
                    }
                    return true;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    public Client getClient(String code) {
        return loginClients.get(code);
    }
}
