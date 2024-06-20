package org.wowtools.hppt.run.sc.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.wowtools.hppt.common.util.CommonConfig;

import java.util.ArrayList;

/**
 * @author liuyu
 * @date 2023/11/5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScConfig extends CommonConfig {

    /**
     * 客户端netty workerGroup 线程数，默认12
     */
    public int workerGroupNum = 12;

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

    public static final class PostConfig {
        /**
         * 服务端http地址，可以填nginx转发过的地址
         */
        public String serverUrl;

        /**
         * 人为添加一个发送等待时间（毫秒），若网络质量不佳或发送请求过于频繁，可设置一个大于0的值来等待若干毫秒后一起发送
         */
        public long sendSleepTime = 0;

    }

    public static final class WebSocketConfig {
        /**
         * 服务端http地址，可以填nginx转发过的地址，但注意加允许ws转发的配置
         */
        public String serverUrl;

        /**
         * 发送websocket ping的周期(毫秒)，定期发送一个ping心跳信号以防止ws闲置断开，小于等于0则不执行心跳ping，默认3000
         */
        public long pingInterval = 30000;

        /**
         * netty workerGroupNum 默认2
         */
        public int workerGroupNum = 2;
    }

    public static final class HpptConfig {
        /**
         * 服务端host
         */
        public String host;
        /**
         * 服务端端口
         */
        public int port;

        /**
         * 用几个字节来作为长度位，对应最多可发送Max(256^lengthFieldLength-1,2^31-1)长度的字节，只支持1、2、3、4，服务端与客户端必须一致，默认3
         */
        public int lengthFieldLength = 3;

        /**
         * netty workerGroupNum 默认2
         */
        public int workerGroupNum = 2;
    }

    public static final class RHpptConfig {
        /**
         * 启动服务端口
         */
        public int port;

        /**
         * 用几个字节来作为长度位，对应最多可发送Max(256^lengthFieldLength-1,2^31-1)长度的字节，只支持1、2、3、4，服务端与客户端必须一致，默认3
         */
        public int lengthFieldLength = 3;

    }

    public static final class RPostConfig {
        /**
         * 启动服务端口
         */
        public int port;

        /**
         * 等待用户输入字节多少毫秒
         */
        public long waitBytesTime = 10000;
    }

    /**
     * 运行类型 支持 websocket(以websocket协议传输数据)、post(以http post协议传输数据)、hppt(以hppt自定义的协议传输数据)
     */
    public String type;

    /**
     * 客户端id，每个sc.jar用一个，不要重复
     */
    public String clientId;


    /**
     * 向服务端发数据请求体的字节数最大值
     * 有时会出现413 Request Entity Too Large问题，没办法改nginx的话就用这个值限制，可能会有少量超出
     */
    public int maxSendBodySize = Integer.MAX_VALUE;

    /**
     * 端口转发
     */
    public ArrayList<Forward> forwards;

    /**
     * 自定义生命周期实现类
     */
    public String lifecycle;

    public PostConfig post = new PostConfig();

    public WebSocketConfig websocket = new WebSocketConfig();

    public HpptConfig hppt = new HpptConfig();

    public RHpptConfig rhppt = new RHpptConfig();

    public RPostConfig rpost = new RPostConfig();
}
