package org.wowtools.hppt.kafkademo;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * kafka工具类
 *
 * @author liuyu
 * @date 2024/6/15
 */
public class KafkaUtil {

    //客户端发数据的topic
    public static final String ClientSendTopic = "client-send";
    //服务端发数据的topic
    public static final String ServerSendTopic = "server-send";


    //基本的kafka连接配置
    private static Properties buildProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "wsl:9092"); // 部署在电脑C上的Kafka服务器地址
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return props;
    }

    @FunctionalInterface
    public interface BytesFunction {
        void f(byte[] bytes);
    }

    /**
     * 构造一个向指定topic发送bytes数据的工具
     *
     * @param topic 主题
     * @return BytesFunction 调用其f(byte[] bytes)方法发送数据
     */
    public static BytesFunction buildProducer(String topic) {
        Producer<String, byte[]> producer = new KafkaProducer<>(buildProperties());
        return (bytes -> {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, bytes);
            producer.send(record);
        });
    }

    /**
     * 消费kafka数据
     *
     * @param groupId 消费者组
     * @param topic   主题
     * @param cb      消费到字节时回调
     */
    public static void buildConsumer(String groupId, String topic, BytesFunction cb) {
        Properties props = buildProperties();
        props.put("group.id", groupId+System.currentTimeMillis());
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        // 订阅主题
        consumer.subscribe(Collections.singletonList(topic));
        // 消费消息
        Thread.startVirtualThread(()->{
            while (true) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(10));
                for (ConsumerRecord<String, byte[]> record : records) {
                    byte[] value = record.value();
                    cb.f(value);
                }
            }
        });
    }
}
