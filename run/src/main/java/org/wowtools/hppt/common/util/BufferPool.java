package org.wowtools.hppt.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 缓冲池，内置一个LinkedBlockingQueue,用以解耦生产者和消费者、缓冲数据并做监控
 *
 * @author liuyu
 * @date 2024/10/27
 */
@Slf4j
public class BufferPool<T> {
    private final LinkedBlockingQueue<T> queue = new LinkedBlockingQueue<>();

    private final String name;

    /**
     * @param name 缓冲池名字，为便于排查，请保证名称在业务层面的准确清晰
     */
    public BufferPool(String name) {
        this.name = name;
    }

    /**
     * 添加
     *
     * @param t t
     */
    public void add(T t) {
        if (!DebugConfig.OpenBufferPoolDetector) {
            queue.add(t);
        } else {
            int n = queue.size();
            queue.add(t);
            int n1 = queue.size();
            if (n < DebugConfig.BufferPoolWaterline && n1 >= DebugConfig.BufferPoolWaterline) {
                log.debug("{} 缓冲池高水位线: {} -> {}", name, n, n1);
            }
        }

    }

    /**
     * 获取,队列为空则一直阻塞等待
     *
     * @return t
     */
    public T take() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取,队列为空则返回null
     *
     * @return t or null
     */
    public T poll() {
        return queue.poll();
    }

    /**
     * 获取,队列为空则阻塞等待一段时间,超时则返回null
     *
     * @param timeout timeout
     * @param unit    TimeUnit
     * @return t or null
     */
    public T poll(long timeout, TimeUnit unit) {
        T t;
        try {
            t = queue.poll(timeout, unit);
        } catch (InterruptedException e) {
            return null;
        }
        return t;
    }

    /**
     * 获取队列中当前可用的所有元素,队列为空则阻塞等待，所以list至少会有一个元素
     *
     * @return list
     */
    public List<T> takeAndDrainToList() {
        List<T> list = new LinkedList<>();
        T t0 = take();
        list.add(t0);
        queue.drainTo(list);
        return list;
    }

    /**
     * 获取队列中当前可用的所有元素，队列为空则返回null
     *
     * @return list
     */
    public List<T> drainToList() {
        if (queue.isEmpty()) {
            return null;
        }
        List<T> list = new LinkedList<>();
        queue.drainTo(list);
        return list;
    }

    /**
     * 获取队列中当前可用的所有元素添加到list中
     *
     * @param list list
     */
    public void drainToList(List<T> list) {
        queue.drainTo(list);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
