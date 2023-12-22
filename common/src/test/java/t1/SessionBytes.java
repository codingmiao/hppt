package t1;

/**
 * @author liuyu
 * @date 2023/11/17
 */
public class SessionBytes {
    private final int sessionId;
    private final byte[] bytes;

    public int getSessionId() {
        return sessionId;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public SessionBytes(int sessionId, byte[] bytes) {
        this.sessionId = sessionId;
        this.bytes = bytes;
    }
}
