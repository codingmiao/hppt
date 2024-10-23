package org.wowtools.hppt.common.pojo;

/**
 * 可发送的SessionBytes，被发送后会触发回调函数并告知是否成功
 * @author liuyu
 * @date 2024/10/20
 */
public class SendAbleSessionBytes {
    public interface CallBack{
        void cb(boolean success);
    }

    public final SessionBytes sessionBytes;

    public final CallBack callBack;

    public SendAbleSessionBytes(SessionBytes sessionBytes, CallBack callBack) {
        this.sessionBytes = sessionBytes;
        this.callBack = callBack;
    }
}
