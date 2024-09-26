package org.wowtools.hppt.kafkademo;

import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端，部署在电脑B上
 *
 * @author liuyu
 * @date 2024/6/15
 */
public class ServerDemo extends ServerSessionService<KafkaCtx> {
    //TODO 传输文件等大字节数传播的情况下，需处理kafka字节顺序消费问题
    /*
     * 注：Server类的泛型CTX用以识别客户端的唯一性，所以如果需要支持多个客户端同时访问，考虑从KafkaCtx上下手改造
     * 这里简单演示单个客户端的情况
     * */
    private final KafkaCtx singleCtx = new KafkaCtx();

    public ServerDemo(SsConfig ssConfig) {
        super(ssConfig);
    }


    private KafkaUtil.BytesFunction sendToClient;
    private KafkaUtil.BytesFunction clientConsumer;

    @Override
    public void init(SsConfig ssConfig) throws Exception {
        //初始化时构造好向kafka生产和消费数据的工具
        sendToClient = KafkaUtil.buildProducer(KafkaUtil.ServerSendTopic);

        clientConsumer = (bytes) -> {
            //消费到客户端的数据，调用receiveClientBytes方法来接收
            receiveClientBytes(singleCtx, bytes);
        };
        KafkaUtil.buildConsumer("server", KafkaUtil.ClientSendTopic, clientConsumer);
    }

    @Override
    protected void sendBytesToClient(KafkaCtx kafkaCtx, byte[] bytes) {
        sendToClient.f(bytes);
    }

    @Override
    protected void closeCtx(KafkaCtx kafkaCtx) throws Exception {
        //单个客户端的话这里没什么需要做的，多个的话可能要释放KafkaCtx里的相关资源
    }

    @Override
    protected void onExit() throws Exception {
        //TODO 关闭kafka生产者和消费者
    }

    public static void main(String[] args) throws Exception{
        SsConfig cfg = new SsConfig();
        SsConfig.Client client = new SsConfig.Client();
        client.user = "user1";
        client.password = "12345";
        cfg.clients = new ArrayList<>(1);
        cfg.clients.add(client);
        ServerDemo server = new ServerDemo(cfg);
        server.init(cfg);
        server.sync();
    }
}
