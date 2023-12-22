package org.wowtools.hppt.ss.service;

import org.wowtools.hppt.common.util.AesCipherUtil;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.ss.StartSs;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author liuyu
 * @date 2023/12/18
 */
public class ClientService {

    public static final class Client {
        public final String clientId;
        public final AesCipherUtil aesCipherUtil;

        public Client(String clientId, AesCipherUtil aesCipherUtil) {
            this.clientId = clientId;
            this.aesCipherUtil = aesCipherUtil;
        }
    }

    private static final Map<String, Client> loginClients = new HashMap<>();

    /**
     * 传入的code能解密出客户端id，则登录成功
     *
     * @param code code
     * @return 是否登录成功
     */
    public static boolean login(String code) {
        byte[] bytesCode = BytesUtil.base642bytes(code);
        for (String clientId : StartSs.config.clientIds) {
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

    public static Client getClient(String code) {
        return loginClients.get(code);
    }
}
