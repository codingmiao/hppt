package org.wowtools.hppt.ss.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.wowtools.hppt.ss.StartSs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * ssh转http转文件效率很低，用这个servlet上传
 * curl -X POST -H "Content-Type: application/octet-stream" --data-binary "@/mnt/e/t.py" "http://localhost:20871/up?name=t.py"
 * @author liuyu
 * @date 2023/11/29
 */
@MultipartConfig
public class UpFileServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleFileUpload(req);
        resp.getWriter().write("File uploaded successfully!");
        resp.setHeader("Server","hppt");
    }

    private void handleFileUpload(HttpServletRequest request) throws IOException, ServletException {
        String uploadDir = StartSs.config.fileDir;

        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }

        String fileName = request.getParameter("name");
        File file = new File(uploadDir, fileName);

        try {
            Files.copy(request.getInputStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw e;
        }
    }
}
