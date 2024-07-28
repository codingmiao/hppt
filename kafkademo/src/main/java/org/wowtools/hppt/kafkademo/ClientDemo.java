package org.wowtools.hppt.kafkademo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.util.ArrayList;
import java.util.Properties;

/**
 * 客户端，部署在电脑A上
 * @author liuyu
 * @date 2024/6/15
 */
public class ClientDemo extends ClientSessionService {
    //TODO 传输文件等大字节数传播的情况下，需处理kafka字节顺序消费问题
    public ClientDemo(ScConfig config) throws Exception {
        super(config);
    }

    private KafkaUtil.BytesFunction sendToServer;
    private KafkaUtil.BytesFunction clientConsumer;
    @Override
    protected void connectToServer(ScConfig config, Cb cb) throws Exception {
        //初始化时构造好向kafka生产和消费数据的工具
        sendToServer = KafkaUtil.buildProducer(KafkaUtil.ClientSendTopic);

        clientConsumer = (bytes) -> {
            //消费到客户端的数据，调用receiveServerBytes方法来接收
            try {
                receiveServerBytes(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        KafkaUtil.buildConsumer("client", KafkaUtil.ServerSendTopic, clientConsumer);
        cb.end();//调用end方法，通知框架连接完成
    }

    @Override
    protected void sendBytesToServer(byte[] bytes) {
        sendToServer.f(bytes);
    }

    public static void main(String[] args) throws Exception{
        ScConfig cfg = new ScConfig();
        cfg.clientUser = "user1";
        cfg.clientPassword = "12345";
        ScConfig.Forward forward = new ScConfig.Forward();
        forward.localPort = 10022;
        forward.remoteHost = "wsl";
        forward.remotePort = 22;
        cfg.forwards = new ArrayList<>();
        cfg.forwards.add(forward);
        new ClientDemo(cfg).sync();
    }

}
