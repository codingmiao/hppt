package org.wowtools.hppt.cc.pojo;

/**
 * @author liuyu
 * @date 2023/12/21
 */
public class CcConfig {
    /**
     * 客户端id，每个cc.jar用一个，不要重复
     */
    public String clientId;

    /**
     * 服务端http地址，可以填nginx转发过的地址
     */
    public String serverUrl;

    /**
     * 开始时闲置几毫秒发一次http请求，越短延迟越低但越耗性能
     */
    public long initSleepTime = 1_000;

    /**
     * 当收到空消息时，闲置毫秒数增加多少毫秒
     */
    public long addSleepTime = 1_000;

    /**
     * 当用户端输入字节时，唤醒发送线程，此后多少毫秒不睡眠
     */
    public long awakenTime = 10_000;

    /**
     * 闲置毫秒数最大到多少毫秒
     */
    public long maxSleepTime = 60_000;

    /**
     * 兜底策略，会话超过多少毫秒未确认后自行关闭
     */
    public long sessionTimeout = 60_000;

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
     * 缓冲区最大允许缓冲多少条消息
     */
    public int messageQueueSize = 10240;
}
