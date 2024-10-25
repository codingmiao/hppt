package org.wowtools.hppt.common.pojo;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.Getter;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.DebugConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务端和客户端交互的消息，包含交互的SessionBytes和命令
 *
 * @author liuyu
 * @date 2024/10/23
 */
@Getter
public class TalkMessage {
    private static final AtomicInteger serialNumberBuilder;

    static {
        if (DebugConfig.OpenSerialNumber) {
            serialNumberBuilder = new AtomicInteger();
        } else {
            serialNumberBuilder = null;
        }
    }

    private final List<SessionBytes> sessionBytes;
    private final List<String> commands;
    private final int serialNumber;

    public TalkMessage(List<SessionBytes> sessionBytes, List<String> commands) {
        this.sessionBytes = sessionBytes;
        this.commands = commands;
        if (!DebugConfig.OpenSerialNumber) {
            serialNumber = 0;
        } else {
            serialNumber = serialNumberBuilder.incrementAndGet();
        }
    }

    public TalkMessage(byte[] pbBytes) {
        ProtoMessage.MessagePb pb;
        try {
            pb = ProtoMessage.MessagePb.parseFrom(pbBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        commands = pb.getCommandListList();

        List<ProtoMessage.BytesPb> bytesPbs = pb.getBytesPbListList();
        sessionBytes = new ArrayList<>(bytesPbs.size());
        for (ProtoMessage.BytesPb bytesPb : bytesPbs) {
            sessionBytes.add(new SessionBytes(bytesPb));
        }


        if (!DebugConfig.OpenSerialNumber) {
            serialNumber = 0;
        } else {
            serialNumber = pb.getSerialNumber();
        }
    }

    public int getSerialNumber() {
        return serialNumber;
    }

    public ProtoMessage.MessagePb.Builder toProto() {
        ProtoMessage.MessagePb.Builder builder = ProtoMessage.MessagePb.newBuilder();
        if (null != commands && !commands.isEmpty()) {
            builder.addAllCommandList(commands);
        }
        if (null != sessionBytes && !sessionBytes.isEmpty()) {
            List<ProtoMessage.BytesPb> pbs = new ArrayList<>(sessionBytes.size());
            for (SessionBytes sessionByte : sessionBytes) {
                pbs.add(sessionByte.toProto().build());
            }
            builder.addAllBytesPbList(pbs);
        }
        if (DebugConfig.OpenSerialNumber) {
            builder.setSerialNumber(serialNumber);
        }
        return builder;
    }
}
