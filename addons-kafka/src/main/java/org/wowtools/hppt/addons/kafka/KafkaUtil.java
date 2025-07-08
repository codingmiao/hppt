package org.wowtools.hppt.addons.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.wowtools.hppt.common.util.ResourcesReader;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * kafka工具类
 *
 * @author liuyu
 * @date 2024/6/15
 */
@Slf4j
public class KafkaUtil {
    public static final Config config;

    static {
        try {
            config = new ObjectMapper(new YAMLFactory())
                    .readValue(ResourcesReader.readStr(Config.class, "config-kafka.yml"), Config.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    //基本的kafka连接配置
    private static Properties buildProperties() {
        Properties props = new Properties();
        props.putAll(config.properties);
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
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic,0,"x", bytes);
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
        props.put("group.id", groupId + "-" + config.tag);
        props.put("auto.offset.reset", "latest");
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        // 订阅主题
        TopicPartition partition = new TopicPartition(topic, 0);
        try {
            consumer.seekToEnd(List.of(partition));
        } catch (java.lang.IllegalStateException e) {
            consumer.assign(List.of(partition));
        }
        {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, byte[]> record : records) {
                log.info("测试消费 {}", record.toString());
                byte[] value = record.value();
                cb.f(value);
            }
        }
        // 消费消息
        Thread.startVirtualThread(() -> {
            while (true) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, byte[]> record : records) {
                    byte[] value = record.value();
                    cb.f(value);
                }
            }
        });
    }
}
