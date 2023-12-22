package org.wowtools.hppt.cs.pojo;

import java.util.ArrayList;

/**
 * @author liuyu
 * @date 2023/12/20
 */
public class CsConfig {

    public static final class Client {
        public String clientId;

        public ArrayList<Forward> forwards;
    }

    public static final class Forward {
        /**
         * 本机代理端口
         */
        public int localPort;
        /**
         * 远程ip或域名
         */
        public String remoteHost;
        /**
         * 远程端口
         */
        public int remotePort;

    }

    /**
     * http服务端口
     */
    public int port = 30871;

    /**
     * 是否启用压缩，默认启用 需和服务端保持一致
     */
    public boolean enableCompress = true;

    /**
     * 是否启用内容加密，默认启用 需和服务端保持一致
     */
    public boolean enableEncrypt = true;

    public ArrayList<Client> clients;

}
