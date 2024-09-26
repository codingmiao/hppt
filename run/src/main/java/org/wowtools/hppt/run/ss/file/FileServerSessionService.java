package org.wowtools.hppt.run.ss.file;

import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.util.DirChangeWatcher;
import org.wowtools.hppt.common.util.FileConsumerBuffer;
import org.wowtools.hppt.common.util.FileProducerBuffer;
import org.wowtools.hppt.run.ss.common.ServerSessionService;
import org.wowtools.hppt.run.ss.pojo.SsConfig;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * @author liuyu
 * @date 2024/7/5
 */
@Slf4j
public class FileServerSessionService extends ServerSessionService<FileCtx> {

    private final DirChangeWatcher dirChangeWatcher;
    private final FileProducerBuffer fileProducerBuffer;

    public FileServerSessionService(SsConfig ssConfig) {
        super(ssConfig);
        FileCtx fileCtx = new FileCtx();//TODO 暂时只支持一个客户端连接过来
        File clientSendFile = new File(ssConfig.file.fileDir + "/c.bin");
        clientSendFile.delete();
        try {
            clientSendFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File serverSendFile = new File(ssConfig.file.fileDir + "/s.bin");
        serverSendFile.delete();
        try {
            serverSendFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FileConsumerBuffer fileConsumerBuffer = new FileConsumerBuffer(clientSendFile);
        fileProducerBuffer = new FileProducerBuffer(serverSendFile);

        Path path = clientSendFile.toPath().getFileName();
        dirChangeWatcher = new DirChangeWatcher(new File(ssConfig.file.fileDir).toPath(), (f) -> {
            if (path.equals(f.getFileName())) {
                synchronized (path) {
                    byte[] bytes;
                    while (true) {
                        bytes = fileConsumerBuffer.poll();
                        if (null == bytes) {
                            break;
                        }
                        try {
                            receiveClientBytes(fileCtx,bytes);
                        } catch (Exception e) {
                            log.info("receiveClientBytes err {}", bytes, e);
                        }
                    }
                }

            }
        });
    }

    @Override
    public void init(SsConfig ssConfig) throws Exception {

    }

    @Override
    protected void sendBytesToClient(FileCtx fileCtx, byte[] bytes) {
        log.debug("sendBytesToClient start {}",bytes.length);
        fileProducerBuffer.write(bytes);
        log.debug("sendBytesToClient end {}",bytes.length);
    }

    @Override
    protected void closeCtx(FileCtx fileCtx) throws Exception {

    }

    @Override
    protected void onExit() throws Exception {
        dirChangeWatcher.close();
    }
}
