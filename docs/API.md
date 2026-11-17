# 接口说明

仅监听 `127.0.0.1:8080`，页面与 API 同源。无数据库、无跨域开放。

## HTTP

- `GET /api/state`：返回 ready、error、diskPath。已加载时附 entries、blocks、free、opened、buffers、logs。
- `GET /api/blocks/{id}`：返回 id 和 hex。范围 0～127；优先展示缓冲中的最新字节。
- `POST /api/operations`：JSON 命令，成功返回 `{ok:true,result:...,state:...}`；业务错误 HTTP 400 `{ok:false,error:...}`；底层 I/O 错误 HTTP 500。

示例：

```json
{"operation":"create_file","path":"/doc/a.tx","attribute":4}
```

```json
{"operation":"write_file","path":"/doc/a.tx","content":"hello","count":5}
```

| operation | 字段 |
|---|---|
| initialize | confirmed:true |
| md / dir / rd / close_file / delete_file / typefile | path |
| create_file | path，attribute 默认 4 |
| open_file | path，mode 为 r 或 w |
| read_file | path，count 默认 64 |
| write_file | path，content，count 默认 UTF-8 全部字节 |
| change | path，attribute |
| close_all | 无 |

read_file 与 typefile 的 result 包含 text、hex、bytes、validUtf8；非法或部分 UTF-8 不使用替换字符掩盖原始数据，而返回提示和 hex。dir 的 result 是目录项列表，其余操作 result 为完成提示。框架级 JSON 解析错误可能使用 Spring 默认错误格式，前端显示通用“请求失败”。

## 核心 Java API

`FileSystem(Path)` 加载已有镜像；未初始化或损坏时可查询状态，操作报错。所有公开方法同步于实例监视器。HTTP 操作与操作后的状态在同一个锁内完成。

`initialize(boolean)`、`md(String)`、`dir(String)`、`rd(String)`、`create_file(String,int)`、`open_file(String,String)`、`read_file(String,int)`、`write_file(String,byte[],int)`、`close_file(String)`、`delete_file(String)`、`typefile(String)`、`change(String,int)`、`close_all()`、`state()`、`block(int)`。

使用 try-with-resources 可在正常退出时关闭全部文件并同步镜像。`FsException` 表示规则错误，`IOException` 表示底层存储错误。
