package org.wowtools.hppt.common.client;


import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSessionManagerTest {
    private static ClientSessionManager clientSessionManager;

    public static void main(String[] args) {
        clientSessionManager = new ClientSessionManagerBuilder().setLifecycle(new ClientSessionLifecycle() {
            private AtomicInteger idx = new AtomicInteger();

            @Override
            public int connected(int port, ChannelHandlerContext ctx) {
                System.out.println(port + " connected");
                return idx.addAndGet(1);
            }

            @Override
            public void created(ClientSession clientSession) {
                clientSession.sendToUser("hello".getBytes(StandardCharsets.UTF_8));
                System.out.println("created");
            }

            @Override
            public void sendToTarget(ClientSession clientSession, byte[] bytes) {
                String s = new String(bytes, StandardCharsets.UTF_8);
                System.out.println(s);
                if ("close".equals(s.trim())) {
                    clientSessionManager.disposeClientSession(clientSession, "用户主动关闭");
                }
            }

            @Override
            public void closed(ClientSession clientSession) {
                System.out.println("closed");
            }
        }).build();
        clientSessionManager.bindPort(10001);
        clientSessionManager.bindPort(10002);
    }
}
