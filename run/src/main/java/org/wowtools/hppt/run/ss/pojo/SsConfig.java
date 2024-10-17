package org.wowtools.hppt.run.ss.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.wowtools.hppt.common.util.CommonConfig;
import org.wowtools.hppt.run.sc.pojo.ScConfig;

import java.util.ArrayList;

/**
 * @author liuyu
 * @date 2023/11/25
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SsConfig extends CommonConfig {

    /**
     * 运行类型 支持 websocket(以websocket协议传输数据)、post(以http post协议传输数据)、hppt(以hppt自定义的协议传输数据)
     */
    public String type;

    /**
     * 中继模式下，配置一个ScConfig，即构造一个sc转发给下一个ss
     */
    public ScConfig relayScConfig;

    /**
     * 服务端口
     */
    public int port;

    /**
     * 超过sessionTimeout，给客户端发送存活确认命令，若下一个sessionTimeout内未收到确认，则强制关闭服务
     */
    public long sessionTimeout = 120000;

    /**
     * 接收到客户端/真实端口的数据时，数据被暂存在一个队列里，队列满后强制关闭会话
     */
    public int messageQueueSize = 2048;

    /**
     * 生命周期实现类path，为空则使用默认
     */
    public String lifecycle;

    /**
     * 允许的客户端
     */
    public ArrayList<Client> clients;

    /**
     * 密码最大尝试次数，超过次数没输入对会锁定账号直至重启
     */
    public int passwordRetryNum = 5;

    public static final class Client {
        /**
         * 用户名
         */
        public String user;

        /**
         * 密码
         */
        public String password;
    }

    public static final class PostConfig {
        /**
         * 等待真实端口返回数据的毫秒数，一般设一个略小于http服务超时时间的值
         */
        public long waitResponseTime = 30000;

        /**
         * 回复的servlet人为设置的延迟，避免客户端过于频繁的发请求
         */
        public long replyDelayTime = 0;

        /**
         * 服务端netty bossGroupNum
         */
        public int bossGroupNum = 1;

        /**
         * 服务端netty workerGroupNum
         */
        public int workerGroupNum = 0;
    }

    public PostConfig post = new PostConfig();


    public static final class WebSocketConfig {
        /**
         * 服务端netty bossGroupNum
         */
        public int bossGroupNum = 1;

        /**
         * 服务端netty workerGroupNum
         */
        public int workerGroupNum = 0;
    }

    public WebSocketConfig websocket = new WebSocketConfig();

    public static final class HpptConfig {
        /**
         * 用几个字节来作为长度位，对应最多可发送Max(256^lengthFieldLength-1,2^31-1)长度的字节，只支持1、2、3、4，服务端与客户端必须一致，默认3
         */
        public int lengthFieldLength = 3;

        /**
         * 服务端netty bossGroupNum
         */
        public int bossGroupNum = 1;

        /**
         * 服务端netty workerGroupNum 默认按CPU数动态计算
         */
        public int workerGroupNum = 0;
    }

    public HpptConfig hppt = new HpptConfig();

    public static final class RHpptConfig {
        /**
         * 客户端host
         */
        public String host;
        /**
         * 客户端端口
         */
        public int port;
        /**
         * 用几个字节来作为长度位，对应最多可发送Max(256^lengthFieldLength-1,2^31-1)长度的字节，只支持1、2、3、4，服务端与客户端必须一致，默认3
         */
        public int lengthFieldLength = 3;
    }

    public RHpptConfig rhppt = new RHpptConfig();

    public static final class RPostConfig {
        /**
         * 服务端http地址，可以填nginx转发过的地址
         */
        public String serverUrl;
    }

    public RPostConfig rpost = new RPostConfig();

    public static final class FileConfig {
        /**
         * 共享文件夹路径
         */
        public String fileDir;

    }

    public FileConfig file = new FileConfig();
}
