package org.wowtools.hppt.addons.kafka;

import java.util.Map;

/**
 * @author liuyu
 * @date 2025/7/7
 */
public class Config {
    //客户端发数据的topic
    public String clientSendTopic = "client-send-01";
    //服务端发数据的topic
    public String serverSendTopic = "server-send-01";

    public String tag;

    public Map<String,Object> properties;
}
