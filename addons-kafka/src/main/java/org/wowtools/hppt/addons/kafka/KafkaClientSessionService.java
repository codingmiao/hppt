package org.wowtools.hppt.addons.kafka;

import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.util.ArrayList;

/**
 * 客户端，部署在电脑A上
 *
 * @author liuyu
 * @date 2024/6/15
 */
public class KafkaClientSessionService extends ClientSessionService {
    public KafkaClientSessionService(ScConfig config) throws Exception {
        super(config);
    }

    private KafkaUtil.BytesFunction sendToServer;
    private KafkaUtil.BytesFunction clientConsumer;

    @Override
    public void connectToServer(ScConfig config, Cb cb) throws Exception {
        //初始化时构造好向kafka生产和消费数据的工具
        sendToServer = KafkaUtil.buildProducer(KafkaUtil.config.clientSendTopic, false);

        clientConsumer = (bytes) -> {
            //消费到客户端的数据，调用receiveServerBytes方法来接收
            try {
                receiveServerBytes(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        KafkaUtil.buildConsumer("client", KafkaUtil.config.serverSendTopic, clientConsumer, false);
        cb.end(null);//调用end方法，通知框架连接完成
    }

    @Override
    public void sendBytesToServer(byte[] bytes) {
        sendToServer.f(bytes);
    }

    public static void main(String[] args) throws Exception {
        ScConfig cfg = new ScConfig();
        cfg.clientUser = "user1";
        cfg.clientPassword = "12345";
        ScConfig.Forward forward = new ScConfig.Forward();
        forward.localPort = 22022;
        forward.remoteHost = "127.0.0.1";
        forward.remotePort = 22;
        cfg.forwards = new ArrayList<>();
        cfg.forwards.add(forward);
        new KafkaClientSessionService(cfg).sync();
    }

}
