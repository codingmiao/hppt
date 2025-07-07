package org.wowtools.hppt.common.client;


import java.nio.charset.StandardCharsets;

public class ClientSessionManagerTest {
    private static ClientSessionManager clientSessionManager;

    public static void main(String[] args) {
        clientSessionManager = new ClientSessionManagerBuilder().setLifecycle(new ClientSessionLifecycle() {

            @Override
            public void created(ClientSession clientSession) {
                clientSession.sendToUser("hello".getBytes(StandardCharsets.UTF_8));
                System.out.println("created");
            }


            @Override
            public void closed(ClientSession clientSession) {
                System.out.println("closed");
            }
        }).build();
        clientSessionManager.bindPort(null,10001);
        clientSessionManager.bindPort(null,10002);
    }
}
