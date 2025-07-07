package org.wowtools.hppt.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

@Slf4j
public class AddonsLoader {

    private URLClassLoader urlClassLoader;

    public AddonsLoader(String jarsDirPath) throws IOException {
        File dir = new File(jarsDirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn(("插件目录路径不存在或不是文件夹，跳过: " + jarsDirPath));
            return;
        }

        File[] jarFiles = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            log.warn(("插件目录下没有找到jar文件，跳过: " + jarsDirPath));
            return;
        }

        URL[] urls = new URL[jarFiles.length];
        for (int i = 0; i < jarFiles.length; i++) {
            urls[i] = jarFiles[i].toURI().toURL();
        }

        // 创建类加载器
        this.urlClassLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());

        // 设置为当前线程类加载器
        Thread.currentThread().setContextClassLoader(this.urlClassLoader);
    }

    /**
     * 加载某个类
     */
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return urlClassLoader.loadClass(className);
    }

    public URLClassLoader getClassLoader() {
        return urlClassLoader;
    }
}

