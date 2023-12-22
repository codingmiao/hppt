package org.wowtools.hppt.sc.pojo;

import java.util.ArrayList;

/**
 * @author liuyu
 * @date 2023/11/5
 */
public class ScConfig {
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
     * 客户端id，每个sc.jar用一个，不要重复
     */
    public String clientId;

    /**
     * 服务端http地址，可以填nginx转发过的地址
     */
    public String serverUrl;

    /**
     * 开始时闲置几毫秒发一次http请求，越短延迟越低但越耗性能
     */
    public long initSleepTime = 1000;

    /**
     * 当收到空消息时，闲置毫秒数增加多少毫秒
     */
    public long addSleepTime = 1000;

    /**
     * 闲置毫秒数最大到多少毫秒
     */
    public long maxSleepTime = 60000;

    /**
     * 向服务端发数据请求体的字节数最大值
     * 有时会出现413 Request Entity Too Large问题，没办法改nginx的话就用这个值限制
     */
    public int maxSendBodySize = Integer.MAX_VALUE;

    /**
     * 是否启用压缩，默认启用 需和服务端保持一致
     */
    public boolean enableCompress = true;

    /**
     * 是否启用内容加密，默认启用 需和服务端保持一致
     */
    public boolean enableEncrypt = true;

    /**
     * 端口转发
     */
    public ArrayList<Forward> forwards;


}
