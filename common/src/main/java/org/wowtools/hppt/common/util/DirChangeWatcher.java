package org.wowtools.hppt.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;

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
        Thread.startVirtualThread(() -> {
            while (running) {
                log.debug("Waiting for file change: {}", dir);
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

    }
}
