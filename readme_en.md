![hppt](_doc/img/hppt.png)

**hppt** — A tool that enables TCP port forwarding over *any* communication protocol.

— As long as two machines have *any* communication channel (such as HTTP short connection, WebSocket, TCP, or even message queues like Kafka), they can establish mutual port access!

[中文](./readme.md)    [English](./readme_en.md)

[github](https://github.com/codingmiao/hppt)    [gitee](https://gitee.com/wowtools/hppt)

# Overview

In daily work, we often encounter difficulties accessing certain remote ports. For example, some servers allow access only through ports 80/443 due to firewall settings.
If you want to access the server's database, SSH, or other internal services, you can use **hppt** to map the desired ports:

![hppt](_doc/img/1.jpg)

# Quick Start

This project requires JDK 21. If not installed, please download it from the [official JDK site](https://jdk.java.net/archive/).

Download the latest pre-built release of hppt from the [releases](https://github.com/codingmiao/hppt/releases) page,
or build from source:

```shell
mvn clean package -DskipTests
```

## Example 1: Reverse Proxy to Internal SSH Port via HTTP

Suppose you have a server cluster, with only one server (nginx) exposing ports 80/443 to the public (e.g., 111.222.33.44:80). You want to access an internal application server (192.168.0.2) via SSH (port 22). You can deploy hppt as shown:

![Example 1](_doc/img/3.jpg)

1. On any machine in the internal cluster, create a directory named `hppt` and place `hppt.jar`, `ss.yml`, and `logback.xml` inside:

```
hppt
 ├─ hppt.jar
 ├─ ss.yml
 └─ logback.xml
```

Update `ss.yml` as follows:

```yaml
type: post  # Use HTTP POST protocol (simple but less performant). For better performance, consider using WebSocket or hppt protocols.
port: 20871
clients:
  - user: user1
    password: 12345
  - user: user2
    password: 112233
```

> *Note 1*: This uses the simplest `post` protocol. For higher performance, refer to the [WebSocket guide](_doc/demo/websocket.md) or [hppt protocol guide](_doc/demo/hppt.md).
> *Note 2*: In real applications, please use stronger passwords for security.

Run the server:

```shell
cd hppt
<jdk21_path>/bin/java -jar hppt.jar ss ss.yml
```

Update your nginx configuration to forward `/xxx/` to hppt:

```
server {
    listen 80;
    ...
    location /xxx/ {
        proxy_pass http://localhost:20871/;
    }
    ...
}
```

Now, visiting `http://111.222.33.44:80/xxx/` and seeing "error 404" indicates the server is up.

2. On your laptop, create a similar `hppt` directory with `hppt.jar`, `sc.yml`, and `logback.xml`:

```
hppt
 ├─ hppt.jar
 ├─ sc.yml
 └─ logback.xml
```

Update `sc.yml`:

```yaml
type: post
clientUser: user1
clientPassword: 12345

post:
  serverUrl: "http://111.222.33.44:80/xxx"
  sendSleepTime: 0
forwards:
  - localPort: 10022
    remoteHost: "192.168.0.2"
    remotePort: 22
  - localPort: 10023
    remoteHost: "192.168.0.3"
    remotePort: 3306
```

Run the client:

```shell
cd hppt
<jdk21_path>/bin/java -jar hppt.jar sc sc.yml
```

You can now SSH into the internal server from your laptop using `localhost:10022`.

## Example 2: NAT Traversal via Public Relay Server

Suppose you have a desktop at home (SSH port 22) and a public VPS (IP: 111.222.33.44). You want to connect from your laptop at work to your desktop at home. Set up as follows:

![Example 2](_doc/img/4.jpg)

1. On the public VPS, create an `hppt` directory with `hppt.jar`, `sc.yml`, and `logback.xml`:

```
hppt
 ├─ hppt.jar
 ├─ sc.yml
 └─ logback.xml
```

Configure `sc.yml`:

```yaml
type: rhppt  # 'r' prefix means client/server roles are reversed
clientUser: user1
clientPassword: 12345

rhppt:
  port: 20871

heartbeatPeriod: 30000

forwards:
  - localPort: 10022
    remoteHost: "192.168.0.2"
    remotePort: 22
  - localPort: 10023
    remoteHost: "192.168.0.3"
    remotePort: 3306
```

Run on the VPS:

```shell
cd hppt
<jdk21_path>/bin/java -jar hppt.jar sc sc.yml
```

2. On your home desktop, create an `hppt` directory with `hppt.jar`, `ss.yml`, and `logback.xml`:

```
hppt
 ├─ hppt.jar
 ├─ ss.yml
 └─ logback.xml
```

Configure `ss.yml`:

```yaml
type: rhppt
port: 20871

rhppt:
  host: "111.222.33.44"
  port: 20871

heartbeatTimeout: 3600000

clients:
  - user: user1
    password: 12345
  - user: user2
    password: 112233
```

Run on your desktop:

```shell
cd hppt
<jdk21_path>/bin/java -jar hppt.jar ss ss.yml
```

You can now SSH into your home desktop from work via `111.222.33.44:10022`.


## more config
[more config see this doc](_doc/config.md)

## Example 3: Custom Protocol Implementation (e.g., Kafka)

If A and B cannot communicate directly but both can access a Kafka server on machine C, you can bridge them via Kafka:

![kafkademo](_doc/img/kafkademo.jpg)

### Embedded in Java

Clone the project and install locally:

```shell
mvn clean install
```

Create a new Java project and add the dependencies:

```xml
<dependency>
    <groupId>org.wowtools.hppt</groupId>
    <artifactId>run</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<!-- Add Kafka and other dependencies -->
```

#### Server Side

Implement `ServerSessionService`:

```java
public class ServerDemo extends ServerSessionService<T> {
    public ServerDemo(SsConfig ssConfig) { super(ssConfig); }
    public void init(SsConfig ssConfig) throws Exception {}
    protected void sendBytesToClient(T ctx, byte[] bytes) {}
    protected void closeCtx(T ctx) throws Exception {}
    protected void doClose() throws Exception {}
}
```

See [ServerDemo.java](kafkademo/src/main/java/org/wowtools/hppt/kafkademo/ServerDemo.java) for a complete example.

#### Client Side

Implement `ClientSessionService`:

```java
public class ClientDemo extends ClientSessionService {
    public ClientDemo(ScConfig config) throws Exception { super(config); }
    protected void connectToServer(ScConfig config, Cb cb) throws Exception {}
    protected void sendBytesToServer(byte[] bytes) {}
}
```

See [ClientDemo.java](kafkademo/src/main/java/org/wowtools/hppt/kafkademo/ClientDemo.java) for details.

Now, you can access B’s SSH port from A via port 10022.

### Plugin-Based Implementation

You can also implement **hppt** as a **plugin**. Refer to [this module](addons-kafka) for an example.

After developing your plugin, place the files in the following structure:

```
hppt
 ├─ addons
 │   ├─ addons.jar
 │   └─ config files
 ├─ hppt.jar
 ├─ ss.yml or sc.yml
 └─ logback.xml
```

Then, in the configuration file, set the `type` field to the plugin class name.

Example `ss.yml`:

```yaml
# Communication protocol — must match between client and server
type: 'org.wowtools.hppt.addons.kafka.KafkaServerSessionService'

# Allowed client credentials
clients:
  - user: user1
    password: 12345
  - user: user2
    password: 112233
```

Example `sc.yml`:

```yaml
type: 'org.wowtools.hppt.addons.kafka.KafkaClientSessionService'

localHost: 127.0.0.1
# Client username — each sc process should use a unique user
clientUser: user1
# Client password
clientPassword: 12345

# Whether to enable content encryption (default is true). Must match the server setting.
enableEncrypt: true

forwards:
  - localPort: 10022
    remoteHost: "wsl"
    remotePort: 22
```


# Q\&A

## How’s the performance?

When using persistent connections like `hppt` or `WebSocket`, hppt incurs less than 5% overhead. Below is a file transfer comparison (186MB via `scp`):

```shell
# Direct
scp -P 22 jdk-21_linux-aarch64_bin.tar.gz root@xxx:/xxx
100%  186MB   7.5MB/s   00:25

# Via hppt
scp -P 10022 jdk-21_linux-aarch64_bin.tar.gz root@xxx:/xxx
100%  186MB   7.2MB/s   00:26
```

If using HTTP POST (short connection), performance may degrade significantly (up to 30% in some cases).
Use persistent protocols in performance-sensitive scenarios.

## How about security?

Only specified users can connect. Data is encrypted in transit. For advanced custom authentication (e.g., login), contact [liuyu@wowtools.org](mailto:liuyu@wowtools.org).

## What does "hppt" mean?

`hppt` is a reverse spelling of `http`. Initially, the project aimed to reverse HTTP communication to enable TCP connections between machines.
It now supports arbitrary protocols — hence the name sticks to reflect its origin. o(*￣︶￣*)o

> ...Have cool ideas? Feel free to open an issue or email me!
