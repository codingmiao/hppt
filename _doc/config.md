## ✅ 服务端配置：ss.yml

### 基础配置项

| 配置项                  | 类型           | 默认值        | 是否必填 | 说明                                                                           |
| -------------------- | ------------ | ---------- | ---- | ---------------------------------------------------------------------------- |
| `type`               | String       | 无          | ✅    | 服务运行协议类型，支持：`post`、`websocket`、`hppt`、`rhppt`、`rpost`、`file` 或插件类全名（如 Kafka） |
| `port`               | int          | 无          | ✅    | 服务端监听端口                                                                      |
| `clients`            | List<Client> | 无          | ✅    | 允许连接的客户端账户列表，包含字段 `user` 和 `password`                                        |
| `relayScConfig`      | ScConfig     | null       | ❌    | 中继模式下嵌套一个完整的客户端配置（可实现链式转发）                                                   |
| `addonsPath`         | String       | `./addons` | ❌    | 插件目录路径                                                                       |
| `heartbeatTimeout`   | long         | -1         | ❌    | 心跳超时时间(ms)，超过未收到心跳将强制重启连接                                                    |
| `initSessionTimeout` | long         | 30000      | ❌    | 建立连接后与目标端口握手的最大超时(ms)                                                        |
| `sessionTimeout`     | long         | 120000     | ❌    | 空闲会话超时(ms)，超时未确认将强制关闭连接                                                      |
| `messageQueueSize`   | int          | 2048       | ❌    | 每个会话消息队列的最大长度，超出将强制断开                                                        |
| `maxReturnBodySize`  | long         | 10MB       | ❌    | 每个请求最大返回数据体积（字节）                                                             |
| `passwordRetryNum`   | int          | 5          | ❌    | 密码允许重试次数，超过后账号锁定直到重启                                                         |
| `lifecycle`          | String       | null       | ❌    | 自定义生命周期实现类，允许实现初始化/销毁钩子                                                      |

### 客户端认证配置

```yaml
clients:
  - user: user1
    password: 123456
  - user: admin
    password: admin123
```

---

### 协议专用配置项（服务端）

#### `post`

| 配置项                | 类型   | 默认值   | 说明                 |
| ------------------ | ---- | ----- | ------------------ |
| `waitResponseTime` | long | 10000 | 等待真实端响应的最大时间(ms)   |
| `replyDelayTime`   | long | 0     | 延迟回复时间(ms)，用于调试等场景 |
| `bossGroupNum`     | int  | 1     | Netty boss 线程池大小   |
| `workerGroupNum`   | int  | 0     | Netty worker 线程池大小 |

#### `websocket`

| 配置项              | 类型  | 默认值 | 说明                 |
| ---------------- | --- | --- | ------------------ |
| `bossGroupNum`   | int | 1   | Netty boss 线程池大小   |
| `workerGroupNum` | int | 0   | Netty worker 线程池大小 |

#### `hppt`

| 配置项                 | 类型  | 默认值 | 说明                 |
| ------------------- | --- | --- | ------------------ |
| `lengthFieldLength` | int | 3   | 包头长度字段使用的字节数（1-4）  |
| `bossGroupNum`      | int | 1   | Netty boss 线程池大小   |
| `workerGroupNum`    | int | 0   | Netty worker 线程池大小 |

#### `rhppt`

| 配置项                 | 类型     | 默认值 | 说明          |
| ------------------- | ------ | --- | ----------- |
| `host`              | String | 无   | 客户端可访问的服务地址 |
| `port`              | int    | 无   | 客户端连接端口     |
| `lengthFieldLength` | int    | 3   | 包长度字段所占字节数  |

#### `rpost`

| 配置项         | 类型     | 默认值 | 说明                          |
| ----------- | ------ | --- | --------------------------- |
| `serverUrl` | String | 无   | 客户端启动的 HTTP 服务地址（如反向代理 URL） |

#### `file` 尚不成熟

| 配置项       | 类型     | 默认值 | 说明       |
| --------- | ------ | --- | -------- |
| `fileDir` | String | 无   | 文件传输共享目录 |

---

## ✅ 客户端配置：sc.yml

### 基础配置项

| 配置项               | 类型            | 默认值         | 是否必填 | 说明                 |
| ----------------- | ------------- | ----------- | ---- | ------------------ |
| `type`            | String        | 无           | ✅    | 协议类型，需与服务端一致       |
| `clientUser`      | String        | 无           | ✅    | 客户端用户名，服务端校验用      |
| `clientPassword`  | String        | 无           | ✅    | 客户端密码              |
| `forwards`        | List<Forward> | 无           | ✅    | 本地端口转发规则列表         |
| `localHost`       | String        | `127.0.0.1` | ❌    | 本地监听 IP 地址         |
| `workerGroupNum`  | int           | 0 (CPU核数)   | ❌    | Netty worker 线程数   |
| `maxSendBodySize` | int           | 10MB        | ❌    | 单次发送最大包体大小         |
| `heartbeatPeriod` | long          | 120000      | ❌    | 心跳间隔(ms)，设置为 0 可关闭 |
| `isRelay`         | boolean       | false       | ❌    | 是否启用中继转发模式         |
| `addonsPath`      | String        | `./addons`  | ❌    | 插件目录路径             |
| `lifecycle`       | String        | null        | ❌    | 自定义生命周期实现类         |

### 转发规则配置（Forward）

| 字段           | 类型     | 说明         |
| ------------ | ------ | ---------- |
| `localHost`  | String | 本地监听 IP    |
| `localPort`  | int    | 本地监听端口     |
| `remoteHost` | String | 服务端访问的目标地址 |
| `remotePort` | int    | 服务端访问的目标端口 |

---

### 协议专用配置项（客户端）

#### `post`

| 配置项             | 类型     | 默认值 | 说明              |
| --------------- | ------ | --- | --------------- |
| `serverUrl`     | String | 无   | 服务端 HTTP 接口 URL |
| `sendSleepTime` | long   | 5   | 每次请求后的等待间隔(ms)  |

#### `websocket`

| 配置项              | 类型     | 默认值   | 说明           |
| ---------------- | ------ | ----- | ------------ |
| `serverUrl`      | String | 无     | WebSocket 地址 |
| `pingInterval`   | long   | 30000 | 发送 ping 间隔   |
| `workerGroupNum` | int    | 0     | worker线程数    |

#### `hppt`

| 配置项                 | 类型     | 默认值 | 说明        |
| ------------------- | ------ | --- | --------- |
| `host`              | String | 无   | 服务端地址     |
| `port`              | int    | 无   | 服务端端口     |
| `lengthFieldLength` | int    | 3   | 包长字段所占字节数 |
| `workerGroupNum`    | int    | 0   | Netty线程数  |

#### `rhppt`

| 配置项                 | 类型  | 默认值 | 说明          |
| ------------------- | --- | --- | ----------- |
| `port`              | int | 无   | 本地反向服务端口    |
| `lengthFieldLength` | int | 3   | 包头长度字段所占字节数 |

#### `rpost`

| 配置项              | 类型  | 默认值 | 说明           |
| ---------------- | --- | --- | ------------ |
| `port`           | int | 无   | 启动 HTTP 服务端口 |
| `bossGroupNum`   | int | 1   | Boss 线程池大小   |
| `workerGroupNum` | int | 0   | Worker 线程池大小 |

#### `file`

| 配置项             | 类型     | 默认值 | 说明             |
| --------------- | ------ | --- | -------------- |
| `sendDir`       | String | 无   | 本地发送文件目录       |
| `receiveDir`    | String | 无   | 本地接收文件目录       |
| `sendSleepTime` | long   | 200 | 每次文件片段发送间隔(ms) |

---

## 🚀 高级功能说明

### 中继模式 尚不成熟

* `ss.yml` 中配置 `relayScConfig` 字段，即可将当前服务端作为客户端连接下一个服务端，实现链式转发。

### 插件系统

* 通过配置 `addonsPath` 可加载插件（如 Kafka、Redis 等），插件需打包为 `.jar` 放入此目录。

### 生命周期钩子

* 可在 `ss.yml` 和 `sc.yml` 中通过 `lifecycle` 配置类路径，实现初始化与销毁逻辑，适合接入监控、日志等框架。

