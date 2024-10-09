package org.wowtools.hppt.run.sc.file;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.DirChangeWatcher;
import org.wowtools.hppt.common.util.FileConsumerBuffer;
import org.wowtools.hppt.common.util.FileProducerBuffer;
import org.wowtools.hppt.run.sc.common.ClientSessionService;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.io.File;
import java.nio.file.Path;

/**
 * 基于文件的客户端
 *
 * @author liuyu
 * @date 2024/7/5
 */
@Slf4j
public class FileClientSessionService extends ClientSessionService {

    private final DirChangeWatcher dirChangeWatcher;
    private final FileProducerBuffer fileProducerBuffer;


    public FileClientSessionService(ScConfig config) throws Exception {
        super(config);
        File clientSendFile = new File(config.file.fileDir + "/c.bin");
        File serverSendFile = new File(config.file.fileDir + "/s.bin");
        FileConsumerBuffer fileConsumerBuffer = new FileConsumerBuffer(serverSendFile);
        fileProducerBuffer = new FileProducerBuffer(clientSendFile);

        Path path = serverSendFile.toPath().getFileName();
        dirChangeWatcher = new DirChangeWatcher(new File(config.file.fileDir).toPath(), (f) -> {
            if (path.equals(f.getFileName())) {
                synchronized (path) {
                    byte[] bytes;
                    while (true) {
                        bytes = fileConsumerBuffer.poll();
                        if (null == bytes) {
                            break;
                        }
                        try {
                            receiveServerBytes(bytes);
                        } catch (Exception e) {
                            log.info("receiveServerBytes err {}", bytes, e);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void connectToServer(ScConfig config, Cb cb) throws Exception {
        cb.end();
    }

    @Override
    public void sendBytesToServer(byte[] bytes) {
        while (null == fileProducerBuffer) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        log.debug("sendBytesToServer start {}",bytes.length);
        fileProducerBuffer.write(bytes);
        log.debug("sendBytesToServer end {}",bytes.length);
    }

    @Override
    protected void doClose() throws Exception {
        dirChangeWatcher.close();
    }
}
