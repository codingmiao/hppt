package org.wowtools.hppt.ss.service;

import lombok.extern.slf4j.Slf4j;

/**
 * @author liuyu
 * @date 2023/11/17
 */
@Slf4j
public class ServerPort {
    private final String host;
    private final int port;

    public ServerPort(String host, int port) {
        this.host = host;
        this.port = port;
    }




    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
