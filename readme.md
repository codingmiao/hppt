![hppt](_doc/img/hppt.png)

hppt，一款可通过任意协议转发tcp端口的工具。

[github](https://github.com/codingmiao/hppt)

[gitee](https://gitee.com/wowtools/hppt)


# 简介
日常工作中，我们常常因为无法访问某些远程端口而带来很多麻烦，例如下面的场景，服务器的防火墙只留了80/443端口用以web访问，
如果你希望访问到服务器上的数据库、SSH等端口，可以借助本工具把需要的端口映射出来：

![hppt](_doc/img/1.jpg)

# 快速开始
在[releases](https://github.com/codingmiao/hppt/releases)
页面下载最新版本编译好的hppt，或自行下载源码编译。
```shell
#jar
mvn clean package -DskipTests

#可选操作 打native包
su graalvm
cd run
mvn org.graalvm.buildtools:native-maven-plugin:build
```

本项目编译成了可执行文件及jar包。

可执行文件无环境依赖、内存占用，小单因为没有jit支持，性能略逊于jar包执行；

jar包执行性能更好，但多消耗一些内存，如需jar包执行，请先前往[jdk官网](https://jdk.java.net/archive/)下载对应你操作系统版本的jdk21。

## 示例1 通过http端口，反向代理访问内部服务器SSH端口

假设你有一个服务器集群，仅有一个nginx提供了80/443端口对外访问(111.222.33.44:80)，你想要访问集群中的应用服务器(192.168.0.2)的22端口，则可以按如下结构部署

![示例1](_doc/img/3.jpg)

1、在集群中任一服务器上新建一个hppt目录，并上传hppt.jar（也可用可执行文件 hppt.ext 或 hppt）、ss.yml、log4j2.xml文件到此目录下:

```
hppt
    - hppt.jar
    - ss.yml
    - log4j2.xml
```

并调整ss.yml的配置信息:

```yaml
#使用http post协议传输数据，此协议最为简单，但性能略差，如有需要请查看websocket协议或hppt协议或自定义协议
type: post
#服务http端口
port: 20871
# 允许的客户端账号和密码
clients:
  - user: user1
    password: 12345
  - user: user2
    password: 112233

```
（注1：作为快速演示，这里的type选择了最简单的post类型，此场景下最佳性能的协议为websocket，或是有独立端口的话可以配置hppt协议，ws、hppt版的说明奋力码字中。。）

（注2：实际应用中，为了确保安全，建议把clientId设置得更复杂一些）

执行如下命令运行服务端的hppt（3选1）

jar包运行
```shell
cd hppt
<jdk21_path>/bin/java -jar hppt.jar ss ss.yml
```

windows下可执行文件运行
```shell
cd hppt
chcp 65001
title "hppt"
hppt.exe ss ss.yml
pause
```

linux下可执行文件运行
```shell
cd hppt
./hppt ss ss.yml
#后台运行用命令  nohup ./hppt ss ss.yml >/dev/null &
```


在nginx上增加一段配置指向hppt

```
server {
    # 用https也ok的，对应修改nginx https配置即可
    listen       80;
    ...
    location /xxx/ {
        proxy_pass http://localhost:20871/;
    }
    ...
```

随后，访问`http://111.222.33.44:80/xxx/` 能看到“error 404”字样即证明服务端部署成功。

2、自己笔记本上，新建一个hppt目录，拷贝hppt.jar、sc.yml、log4j2.xml文件到此目录下:

```
hppt
    - hppt.jar
    - sc.yml
    - log4j2.xml
```

并调整sc.yml的配置信息:

```yaml
# 和服务端的type保持一致
type: post
# 客户端用户名，每个sc进程用一个，不要重复
clientUser: user1
# 客户端密码
clientPassword: 12345

post:
  # 服务端http地址，如无法直连，用nginx代理几次填nginx的地址也ok
  serverUrl: "http://111.222.33.44:80/xxx"
  # 人为设置的延迟（毫秒），一般填0即可，如果传文件等数据量大、延迟要求低的场景，可以设一个几百毫秒的延迟来降低post请求发送频率
  sendSleepTime: 0
forwards:
    # 把192.168.0.2的22端口代理到本机的10022端口
  - localPort: 10022
    remoteHost: "192.168.0.2"
    remotePort: 22
    # 同理也可以代理数据库等任意TCP端口，只要服务端的hppt所在服务器能访问到的端口都行
  - localPort: 10023
    remoteHost: "192.168.0.3"
    remotePort: 3306


```

执行如下命令启动客户端的hppt（3选1）

jar包运行
```shell
cd hppt
<jdk21_path>/bin/java -jar hppt.jar sc sc.yml
```

windows下可执行文件运行
```shell
cd hppt
chcp 65001
title "hppt"
hppt.exe sc sc.yml
pause
```

linux下可执行文件运行
```shell
cd hppt
./hppt sc sc.yml
#后台运行用命令  nohup ./hppt ss ss.yml >/dev/null &
```

随后，你就可以在公司用linux连接工具访问localhost的10022端口，来登录应用服务器了


## 示例2 内网穿透，通过公网转发，访问无公网IP的服务器

假设你家里有一台台式机(ssh端口为22)，并且有一台公网VPS服务器(ip 111.222.33.44)，你想在公司用笔记本登录家里的台式机，可按如下结构部署：

![示例2](_doc/img/4.jpg)

1、公网服务器上，新建一个hppt目录，拷贝hppt.jar、sc.yml、log4j2.xml文件到此目录下:

```
hppt
    - hppt.jar
    - sc.yml
    - log4j2.xml
```

并调整sc.yml的配置信息:

```yaml
# 通讯协议 本示例使用了性能最好的hppt协议，加r前缀表示客户端和服务端角色互换。这里也可以配http post或websocket
type: rhppt
# 客户端用户名，每个sc进程用一个，不要重复
clientUser: user1
# 客户端密码
clientPassword: 12345

# 服务端口
rhppt:
  port: 20871

forwards:
    # 把192.168.0.2的22端口代理到本机的10022端口
  - localPort: 10022
    remoteHost: "192.168.0.2"
    remotePort: 22
    # 同理也可以代理数据库等任意TCP端口，只要服务端的hppt所在服务器能访问到的端口都行
  - localPort: 10023
    remoteHost: "192.168.0.3"
    remotePort: 3306

```

执行如下命令启动公网服务器上的hppt

```shell
<jdk21_path>/bin/java -jar hppt.jar sc sc.yml
```

2、家里的台式机上，新建一个hppt目录，拷贝hppt.jar、ss.yml、log4j2.xml文件到此目录下：

```
hppt
    - hppt.jar
    - ss.yml
    - log4j2.xml
```

修改ss.yml

```yaml
# 通讯协议 客户端与服务端保持一致
type: rhppt
#服务http端口
port: 20871

# 指向上一步启动的服务的ip和端口
rhppt:
  host: "111.222.33.44"
  port: 20871

# 允许的客户端账号和密码
clients:
  - user: user1
    password: 12345
  - user: user2
    password: 112233

```


执行如下命令启动家里台式机上的hppt

```shell
cd hppt
<jdk21_path>/bin/java -jar hppt.jar ss ss.yml
```

随后，你就可以在公司用linux连接工具访问111.222.33.44的10022端口，来登录家里的台式机了


## 示例3 编写自定义协议
如下图所示，A、B两台机器间无法进行通信，但他们都可以访问到机器C上的kafka，本示例演示如何通过编写自定义协议，使得A能够以C上的kafka作为桥梁访问到B上的SSH端口：

![kafkademo](_doc/img/kafkademo.jpg)

首先clone本项目到本地，然后`mvn clean install`把本项目安装到maven。

然后新建一个java工程，引入hppt-run以及kafka等maven依赖
```xml
        <dependency>
            <groupId>org.wowtools.hppt</groupId>
            <artifactId>run</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
        <!--继续添加其他依赖-->
```
然后就可以编写代码了：

编写一个服务端实现并在机器B上运行，你需要实现如下方法:
```java
public class ServerDemo extends ServerSessionService<T> {

    public ServerDemo(SsConfig ssConfig) {
        super(ssConfig);
    }

    //初始化时需要做什么
    public void init(SsConfig ssConfig) throws Exception {
    }

    //怎样发送字节到客户端
    protected void sendBytesToClient(T ctx, byte[] bytes) {
    }

    //收到客户端的字节时，主动去调用receiveClientBytes(CTX ctx, byte[] bytes)

    //当客户端断开时需要做什么
    protected void closeCtx(T ctx) throws Exception {
    }

    //当本服务端关闭后，在此释放掉连接池等资源
    protected void doClose() throws Exception {
    }
}
```
完整的示例实现请参考[这里](kafkademo/src/main/java/org/wowtools/hppt/kafkademo/ServerDemo.java)


编写一个客户端实现并在机器B上运行，你需要实现如下方法:
```java
public class ClientDemo extends ClientSessionService {
    public ClientDemo(ScConfig config) throws Exception {
        super(config);
    }

    //怎样连接到服务端
    protected void connectToServer(ScConfig config, Cb cb) throws Exception {
    }

    //怎样发送字节到服务端
    protected void sendBytesToServer(byte[] bytes) {
    }

    //收到服务端的字节时，主动去调用receiveServerBytes(byte[] bytes)
}
```
完整的示例实现请参考[这里](kafkademo/src/main/java/org/wowtools/hppt/kafkademo/ClientDemo.java)

随后，你就可以通过访问A的10022端口，来连接B上的SSH 22端口了。

# Q&A

## 性能如何？

使用hppt或websocket等长连接协议的话，本项目只是做了个转发和加解密等操作，性能损耗在5%以内，以下是示例2中scp命令拷贝一个186m的文件，连接原始端口和代理端口的耗时对比：

```shell
# 直连
scp -P 22 jdk-21_linux-aarch64_bin.tar.gz   root@xxx:/xxx                                                                                                                          
100%  186MB   7.5MB/s   00:25
# 代理
scp -P 10022 jdk-21_linux-aarch64_bin.tar.gz   root@xxx:/xxx                                                                                                                          
100%  186MB   7.2MB/s   00:26

```

但如果是用http post作为传输协议的话，由于http本身短连接、带了很多请求头等无用信息之类的原因，损耗就比较大了，笔者在应用环境中测试甚至会达到30%左右的损耗。
所以在性能敏感的场景，建议使用长连接协议，短连接协议仅在不关注性能或是环境不允许的情况下再使用。

## 安全性如何？

必须使用指定的clientId才能连接，数据传输过程中对字节进行了加密，如果你还需要更多的个性化验证，比如用户登录，可以发邮件到[liuyu@wowtools.org](liuyu@wowtools.org)
进行定制化开发。

# 后续计划

完成rwebsocket协议，优化rpost协议的性能开销

中继模式开发

完善文档和demo

...(还有什么好玩的想法给我提issue或者发邮件哈)
