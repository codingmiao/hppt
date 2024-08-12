package org.wowtools.hppt.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 监听某个文件夹中的文件被修改
 *
 * @author liuyu
 * @date 2024/6/17
 */
@Slf4j
public class DirChangeWatcher implements AutoCloseable {

    @FunctionalInterface
    public interface Cb {
        void cb(Path file);
    }

    private volatile boolean running = true;
    private final WatchService watchService;

    public void close() throws Exception {
        running = false;
        watchService.close();
    }

    /**
     * 监听某个文件夹中的文件被修改
     *
     * @param dir 文件夹
     * @param cb  文件被修改时，触发cb(file)
     */
    public DirChangeWatcher(Path dir, Cb cb) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        boolean success;
        try {
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            success = true;
        } catch (Exception e) {
            log.warn("系统或文件夹不支持文件监听，降级为每10ms轮询一次文件的方式", e);
            success = false;
        }
        if (success) {
            Thread.startVirtualThread(() -> {
                while (running) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        continue;
                    }
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path changed = (Path) event.context();
                            log.debug("file change: {}", changed);
                            cb.cb(changed);
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        log.debug("WatchKey has been unregistered: {}", dir);
                        break;
                    }
                }
            });
        } else {
            Thread.startVirtualThread(() -> {
                //TODO 性能起见，这里只会监听当前已存在的文件，后面新建的文件得不到监听，当前场景下可以满足，后面有需要的话需要调整动态加入到files中
                ArrayList<File> list = new ArrayList<>();
                for (File file : dir.toFile().listFiles()) {
                    if (file.isFile()) {
                        list.add(file);
                    }
                }
                Path[] files = new Path[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    files[i] = list.get(i).toPath();
                }
                while (running) {
                    try {
                        Thread.sleep(10);
                        for (Path file : files) {
                            cb.cb(file);
                        }
                    } catch (Exception e) {
                        log.warn("监听线程异常", e);
                    }
                }
            });
        }


    }
}
