package org.wowtools.hppt.common.util;

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
public class BufferPool<T> {
    private final LinkedBlockingQueue<T> queue = new LinkedBlockingQueue<>();

    private final String name;

    /**
     *
     * @param name 缓冲池名字，为便于排查，请保证业务功能上的唯一
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
        queue.add(t);
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
}
