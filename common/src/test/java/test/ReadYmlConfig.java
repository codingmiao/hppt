package test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.wowtools.common.utils.ResourcesReader;

/**
 * @author liuyu
 * @date 2023/12/18
 */
public class ReadYmlConfig {

    public static final class SsConfig {
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
         * 上传/下载文件用的目录
         */
        public String fileDir;
    }
    public static void main(String[] args) throws Exception {
        String s = ResourcesReader.readStr(ReadYmlConfig.class,"ss.yml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SsConfig obj = mapper.readValue(s, SsConfig.class);
        System.out.println(obj);
    }
}
