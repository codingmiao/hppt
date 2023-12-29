package org.wowtools.hppt.ss.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.wowtools.hppt.ss.StartSs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * ssh转http转文件效率很低，用这个servlet下载
 * wget "http://localhost:20871/down?name=t.py"
 * @author liuyu
 * @date 2023/11/29
 */
@MultipartConfig
public class DownFileServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws IOException {
        // 设置响应类型和字符编码
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding("UTF-8");

        // 获取要下载的文件路径，这里假设文件存储在 web 应用的根目录下的 files 文件夹中
        String filePath = StartSs.config.fileDir + "/" + req.getParameter("name");
        File file = new File(filePath);

        // 设置响应头，告诉浏览器以附件形式下载文件
        response.setHeader("Content-Disposition", "attachment; filename=" + file.getName());
        response.setHeader("Content-Length", String.valueOf(file.length()));
        response.setHeader("Server","hppt");
        // 读取文件内容并写入响应输出流
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doPost(req, resp);
    }

}
