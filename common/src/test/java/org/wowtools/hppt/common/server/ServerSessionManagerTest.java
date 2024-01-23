package org.wowtools.hppt.common.server;


import java.nio.charset.StandardCharsets;

public class ServerSessionManagerTest {

    public static void main(String[] args) throws Exception {
        ServerSessionManager serverSessionManager = new ServerSessionManagerBuilder().setLifecycle(new ServerSessionLifecycle() {
            @Override
            public void created(ServerSession serverSession) {
                serverSession.sendToTarget("123".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void sendToUser(ServerSession serverSession, byte[] bytes) {
                System.out.println("sendToUser " + new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void closed(ServerSession serverSession) {
                System.out.println("closed");
            }
        }).build();

        new Thread(() -> {
            ServerSession session = serverSessionManager.createServerSession("c1", "localhost", 10001);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            session.sendToTarget("close".getBytes(StandardCharsets.UTF_8));
        }).start();

        new Thread(() -> {
            ServerSession session = serverSessionManager.createServerSession("c1", "localhost", 10001);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            session.sendToTarget("close".getBytes(StandardCharsets.UTF_8));
        }).start();

        new Thread(() -> {
            ServerSession session = serverSessionManager.createServerSession("c1", "localhost", 10002);
        }).start();
    }
}
