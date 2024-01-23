package org.wowtools.hppt.run.ss.pojo;

import java.util.ArrayList;

/**
 * @author liuyu
 * @date 2023/11/25
 */
public class SsConfig {
    public int port;

    /**
     * 超过sessionTimeout，给客户端发送存活确认命令，若下一个sessionTimeout内未收到确认，则强制关闭服务
     */
    public long sessionTimeout = 120000;

    /**
     * 接收到客户端/真实端口的数据时，数据被暂存在一个队列里，队列满后强制关闭会话
     */
    public int messageQueueSize = 2048;

    /**
     * 是否启用压缩，默认启用
     */
    public boolean enableCompress = true;

    /**
     * 是否启用内容加密，默认启用
     */
    public boolean enableEncrypt = true;

    /**
     * 允许的客户端
     */
    public ArrayList<String> clientIds;
}
