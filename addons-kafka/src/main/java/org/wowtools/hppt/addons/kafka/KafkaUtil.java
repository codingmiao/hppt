package org.wowtools.hppt.addons.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.wowtools.hppt.common.util.ResourcesReader;

import java.time.Duration;
import java.util.*;

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
     * @param topic    主题
     * @param recreate 为true则重建一个 1个分区、1个副本的topic
     * @return BytesFunction 调用其f(byte[] bytes)方法发送数据
     */
    public static BytesFunction buildProducer(String topic, boolean recreate) {
        Properties c = buildProperties();
        if (recreate) {
            recreateTopic(c, topic);
        }
        Producer<String, byte[]> producer = new KafkaProducer<>(c);
        return (bytes -> {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, 0, "x", bytes);
            producer.send(record);
        });
    }

    private static void recreateTopic(Properties c, String topic) {
        try (AdminClient adminClient = AdminClient.create(c)) {
            try {
                // 获取当前所有 topic
                Set<String> topicNames = adminClient.listTopics().names().get();
                // 如果 topic 存在，则删除
                if (topicNames.contains(topic)) {
                    log.info("Topic exists. Deleting: " + topic);
                    DeleteTopicsResult deleteTopicsResult = adminClient.deleteTopics(Collections.singletonList(topic));
                    deleteTopicsResult.all().get();
                    // 等待 topic 删除彻底（视情况可调整）
                    Thread.sleep(3000);
                }
                // 创建新的 topic
                NewTopic newTopic = new NewTopic(topic, 1, (short) 1); // 1个分区，1个副本
                CreateTopicsResult createTopicsResult = adminClient.createTopics(Collections.singletonList(newTopic));
                createTopicsResult.all().get();
                log.info("Topic created successfully: " + topic);
            } catch (Exception e) {
                log.warn("删除topic失败，尝试清除数据 {}", topic, e);
                // 获取 topic 的 partition 列表
                DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(List.of(topic));
                List<TopicPartition> partitions = describeTopicsResult.values().get(topic)
                        .get()
                        .partitions()
                        .stream()
                        .map(p -> new TopicPartition(topic, p.partition()))
                        .toList();

                // 获取每个 partition 的最新 offset
                Map<TopicPartition, OffsetSpec> offsetSpecs = new HashMap<>();
                for (TopicPartition tp : partitions) {
                    offsetSpecs.put(tp, OffsetSpec.latest());
                }
                Map<TopicPartition, ListOffsetsResultInfo> offsetResults =
                        adminClient.listOffsets(offsetSpecs).all().get();

                // 构造 RecordsToDelete 请求
                Map<TopicPartition, RecordsToDelete> recordsToDelete = new HashMap<>();
                for (TopicPartition tp : partitions) {
                    long offset = offsetResults.get(tp).offset();
                    recordsToDelete.put(tp, RecordsToDelete.beforeOffset(offset));
                }

                // 执行 deleteRecords
                DeleteRecordsResult deleteRecordsResult = adminClient.deleteRecords(recordsToDelete);
                // 遍历所有分区的结果
                for (Map.Entry<TopicPartition, KafkaFuture<DeletedRecords>> entry : deleteRecordsResult.lowWatermarks().entrySet()) {
                    TopicPartition tp = entry.getKey();
                    DeletedRecords deleted = entry.getValue().get(); // get() 是 KafkaFuture.get()
                    log.info("Partition {} deleted up to offset {}", tp, deleted.lowWatermark());
                }
            }
        } catch (Exception e) {
            log.warn("清理topic失败 {}", topic, e);
        }
    }

    /**
     * 消费kafka数据
     *
     * @param groupId  消费者组
     * @param topic    主题
     * @param recreate 为true则重建一个 1个分区、1个副本的topic
     * @param cb       消费到字节时回调
     */
    public static void buildConsumer(String groupId, String topic, BytesFunction cb, boolean recreate) {
        Properties props = buildProperties();
        if (recreate) {
            recreateTopic(props, topic);
        }
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
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
