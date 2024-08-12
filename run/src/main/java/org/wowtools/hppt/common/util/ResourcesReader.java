package org.wowtools.hppt.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author liuyu
 * @date 2024/8/7
 */
public class ResourcesReader {
    /**
     * 获得项目根路径
     *
     * @param clazz 定位用的类
     * @return
     */
    public static String getRootPath(Class<?> clazz){
        File currentFile;
        try {
            currentFile = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String currentDir;
        if (currentFile.getName().endsWith("classes")) {
            //开发环境下，目录在target\classes下
            currentDir = currentFile.getPath();
        }else {
            currentDir = currentFile.getParent();
        }
        return currentDir;
    }

    /**
     * 读取类所处根目录下的文件路径加上path的文件内容为String
     *
     * @param clazz 定位用的类
     * @param path  类根路径下的相对路径
     * @return
     */
    public static String readStr(Class<?> clazz, String path) {
        try {
            String basePath = getRootPath(clazz);
            return readStr(basePath + "/" + path);
        } catch (Exception e) {
            throw new RuntimeException("读取配置文件异常", e);
        }
    }

    /**
     * 读取绝对路径下的文件的文件内容为String
     *
     * @param path 绝对路径
     * @return
     */
    public static String readStr(String path) {
        // 读取文件内容为字符串
        try {
            return Files.readString(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
