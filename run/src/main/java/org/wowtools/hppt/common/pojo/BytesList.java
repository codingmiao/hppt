package org.wowtools.hppt.common.pojo;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.Getter;
import org.wowtools.hppt.common.protobuf.ProtoMessage;
import org.wowtools.hppt.common.util.DebugConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author liuyu
 * @date 2024/10/23
 */
public class BytesList {
    private static final AtomicInteger serialNumberBuilder;

    static {
        if (DebugConfig.OpenSerialNumber) {
            serialNumberBuilder = new AtomicInteger();
        } else {
            serialNumberBuilder = null;
        }
    }

    private final Collection<byte[]> bytesCollection;
    private final int serialNumber;

    public BytesList(Collection<byte[]> bytesCollection) {
        this.bytesCollection = bytesCollection;
        if (!DebugConfig.OpenSerialNumber) {
            serialNumber = 0;
        } else {
            serialNumber = serialNumberBuilder.incrementAndGet();
        }
    }

    public BytesList(byte[] pbBytes) {
        ProtoMessage.BytesListPb pb;
        try {
            pb = ProtoMessage.BytesListPb.parseFrom(pbBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
        List<ByteString> byteStringList = pb.getBytesListList();
        ArrayList<byte[]> res = new ArrayList<>(byteStringList.size());
        for (ByteString s : byteStringList) {
            res.add(s.toByteArray());
        }
        this.bytesCollection = res;

        if (!DebugConfig.OpenSerialNumber) {
            serialNumber = 0;
        } else {
            serialNumber = pb.getSerialNumber();
        }
    }

    public Collection<byte[]> getBytes(){
        return bytesCollection;
    }

    public int getSerialNumber() {
        return serialNumber;
    }

    public ProtoMessage.BytesListPb.Builder toProto() {
        List<ByteString> byteStringList = new ArrayList<>(bytesCollection.size());
        for (byte[] bytes : bytesCollection) {
            byteStringList.add(ByteString.copyFrom(bytes));
        }
        ProtoMessage.BytesListPb.Builder builder = ProtoMessage.BytesListPb.newBuilder();
        builder.addAllBytesList(byteStringList);
        return builder;
    }
}
