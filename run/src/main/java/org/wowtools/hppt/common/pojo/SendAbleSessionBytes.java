package org.wowtools.hppt.common.pojo;

/**
 * 可发送的SessionBytes，被发送后会触发回调函数并告知是否成功
 *
 * @author liuyu
 * @date 2024/10/20
 */
public record SendAbleSessionBytes(SessionBytes sessionBytes,
                                   org.wowtools.hppt.common.pojo.SendAbleSessionBytes.CallBack callBack) {
    public interface CallBack {
        void cb(boolean success);
    }

}
