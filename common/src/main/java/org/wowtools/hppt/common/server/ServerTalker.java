package org.wowtools.hppt.common.server;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.wowtools.hppt.common.pojo.SessionBytes;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.BytesUtil;
import org.wowtools.hppt.common.util.CommonConfig;
import org.wowtools.hppt.common.util.Constant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author liuyu
 * @date 2024/1/25
 */
@Slf4j
public class ServerTalker {

    //接收客户端发来的字节并做相应处理
    public static void receiveClientBytes(CommonConfig config, ServerSessionManager serverSessionManager,
                                          LoginClientService.Client client, byte[] bytes) throws Exception {
        if (null == bytes || bytes.length == 0) {
            return;
        }
        //解密 解压
        if (config.enableEncrypt) {
            bytes = client.aesCipherUtil.descriptor.decrypt(bytes);
        }
        if (config.enableCompress) {
            bytes = BytesUtil.decompress(bytes);
        }

        ProtoMessage.MessagePb inputMessage = ProtoMessage.MessagePb.parseFrom(bytes);
        Map<Integer, ServerSession> serverSessionMap = serverSessionManager.getServerSessionMapByClientId(client.clientId);

        /* 发消息 */
        //发命令
        for (String command : inputMessage.getCommandListList()) {
            receiveClientCommand(command, serverSessionManager, serverSessionMap, client);
        }
        //发bytes
        for (ProtoMessage.BytesPb bytesPb : inputMessage.getBytesPbListList()) {
            ServerSession severSession = serverSessionMap.get(bytesPb.getSessionId());
            if (null == severSession) {
                //服务端已经没有这个session了，给客户端发关闭命令
                client.addCommand(String.valueOf(Constant.ScCommands.CloseSession) + bytesPb.getSessionId());
            } else {
                //TODO sendToTarget会阻塞，考虑severSession中加一个缓冲池接收，减少不同severSession互相等待
                severSession.sendToTarget(bytesPb.getBytes().toByteArray());
            }
        }
    }

    private static void receiveClientCommand(String command,
                                             ServerSessionManager serverSessionManager, Map<Integer, ServerSession> serverSessionMap, LoginClientService.Client client) {
        log.debug("收到客户端命令 {} ", command);
        char type = command.charAt(0);
        switch (type) {
            case Constant.SsCommands.CreateSession -> {
                String[] params = command.substring(1).split(Constant.sessionIdJoinFlag);
                ServerSession session = serverSessionManager.createServerSession(client, params[0], Integer.parseInt(params[1]));
                client.addCommand(String.valueOf(Constant.ScCommands.InitSession) + session.getSessionId() + Constant.sessionIdJoinFlag + params[2]);
            }
            case Constant.SsCommands.CloseSession -> {
                int sessionId = Integer.parseInt(command.substring(1));
                ServerSession serverSession = serverSessionMap.get(sessionId);
                if (null != serverSession) {
                    serverSessionManager.disposeServerSession(serverSession, "客户端发送关闭命令");
                }
            }
            case Constant.SsCommands.ActiveSession -> {
                int sessionId = Integer.parseInt(command.substring(1));
                ServerSession serverSession = serverSessionMap.get(sessionId);
                if (null != serverSession) {
                    serverSession.activeSession();
                }
            }
        }
    }

    //生成向客户端回复的消息
    public static byte[] replyToClient(CommonConfig config, ServerSessionManager serverSessionManager,
                                       LoginClientService.Client client, long maxReturnBodySize, boolean blocked) throws Exception {
        ProtoMessage.MessagePb.Builder rBuilder = ProtoMessage.MessagePb.newBuilder();
        boolean empty = true;
        /* 取消息 */

        //取命令
        List<String> fetchCommands = client.fetchCommands();
        if (null != fetchCommands && !fetchCommands.isEmpty()) {
            rBuilder.addAllCommandList(fetchCommands);
            empty = false;
            blocked = false;
        }

        //取bytes
        List<SessionBytes> fetchBytes = blocked ? client.fetchBytesBlocked(maxReturnBodySize) : client.fetchBytes(maxReturnBodySize);
        if (null != fetchBytes && !fetchBytes.isEmpty()) {
            List<ProtoMessage.BytesPb> bytesPbList = new ArrayList<>(fetchBytes.size());
            for (SessionBytes fetchByte : fetchBytes) {
                bytesPbList.add(ProtoMessage.BytesPb.newBuilder()
                        .setSessionId(fetchByte.getSessionId())
                        .setBytes(ByteString.copyFrom(fetchByte.getBytes()))
                        .build());
            }
            rBuilder.addAllBytesPbList(bytesPbList);
            empty = false;
        }

        if (empty) {
            return null;
        }


        byte[] bytes = rBuilder.build().toByteArray();
        //压缩 加密
        if (config.enableCompress) {
            bytes = BytesUtil.compress(bytes);
        }
        if (config.enableEncrypt) {
            bytes = client.aesCipherUtil.encryptor.encrypt(bytes);
        }

        return bytes;
    }
}
