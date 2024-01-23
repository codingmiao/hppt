package org.wowtools.hppt.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * @author liuyu
 * @date 2023/11/5
 */
public class Constant {
    public static final ObjectMapper jsonObjectMapper = new ObjectMapper();

    public static final ObjectMapper ymlMapper = new ObjectMapper(new YAMLFactory());

    public static final String sessionIdJoinFlag = ",";

    public static final String commandParamJoinFlag = "\n";

    //ss端执行的命令代码
    public static final class SsCommands {
        //关闭Session 0逗号连接需要的SessionId
        public static final char CloseSession = '0';

        //保持Session活跃 1逗号连接需要的SessionId
        public static final char ActiveSession = '1';
    }

    //Sc端执行的命令代码
    public static final class ScCommands {
        //检查客户端的Session是否还活跃 0逗号连接需要的SessionId
        public static final char CheckSessionActive = '0';

        //关闭客户端连接 1逗号连接需要的SessionId
        public static final char CloseSession = '1';
    }

    //Cs端执行的命令代码
    public static final class CsCommands {
        //检查客户端的Session是否还活跃 0逗号连接需要的SessionId
        public static final char CheckSessionActive = '0';

        //关闭客户端连接 1逗号连接需要的SessionId
        public static final char CloseSession = '1';
    }

    //cc端执行的命令代码
    public static final class CcCommands {
        //关闭Session 0逗号连接需要的SessionId
        public static final char CloseSession = '0';

        //保持Session活跃 1逗号连接需要的SessionId
        public static final char ActiveSession = '1';

        //新建会话 2sessionId host port
        public static final char CreateSession = '2';
    }

}
