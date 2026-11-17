# FAT Lab · 模拟磁盘文件系统

Java 21 / Spring Boot 3.5.16 / 原生浏览器界面。128 × 64 字节模拟磁盘，FAT 显式链接，双缓冲区，多级目录和全部 11 项课设操作。

## 直接运行

1. 确认安装 Java 21 或更新版本，`java -version` 可用。
2. 双击 `start.cmd`。脚本启动本机服务并打开 http://127.0.0.1:8080 。
3. 首次点击“初始化磁盘”。正式数据保存在项目 `data/disk.img`，重启不会自动格式化。
4. 完成操作后点击“关闭全部并保存”，双击 `stop.cmd` 停止后台服务。

启动脚本使用 `release/fat-lab.jar`，无需 Maven、Node、Docker 或联网。日志在 `data/server.log` 和 `data/server-error.log`。端口占用时脚本会停止，不会结束其他程序。

也可前台运行：`java -jar release/fat-lab.jar`。此时按 Ctrl+C 正常退出；`stop.cmd` 只管理启动脚本创建的进程。更换端口：`java -jar release/fat-lab.jar --server.port=8081`；指定独立磁盘：`--fat.disk=target/demo/disk.img`。

## 构建和测试

双击 `build.cmd`：运行完整测试并打包到 `release/fat-lab.jar`。需要 JDK 21、Maven 和首次下载依赖的网络；本地 Maven 缓存保存在项目 `.m2`。

- `test.cmd`：自动化测试加独立主函数测试。
- `java -jar release/fat-lab.jar --self-test`：仅运行独立主函数测试，无需启动服务器。
- Maven 报告：`target/surefire-reports`；交付时保留的记录：`docs/test-results`。

## 文档入口

- [操作手册](docs/操作手册.md)：完整按钮操作、规则与故障处理。
- [课程设计报告](docs/课程设计报告.md)：需求、概要、详细设计、调试、说明、实际测试结果。
- [测试记录](docs/测试记录.md)：自动化及浏览器验收结果。
- [答辩演示脚本](docs/答辩演示脚本.md)：5～8 分钟演示及原理问答。
- [接口说明](docs/API.md)：HTTP 接口及核心方法。

## 源码阅读顺序

`core/BlockDisk.java` → `core/Fat.java` → `core/Directory.java` → `core/FileSystem.java` → `ApiController.java` → `static/app.js`。

不将整张磁盘放入内存；文件内容输出数组、FAT 链列表和状态 DTO 不属于整盘缓存。状态查看逐块读取，不刷走教学缓冲区。目录项长度是盘块数，打开表长度是内容字节数，二者不可混用。

## 提交

交付文件夹包含 `src`、`pom.xml`、`release`、`scripts`、四个 `.cmd` 脚本、`docs` 和本说明。不要提交 `.m2`、`target` 或私人演示磁盘。按指导书用真实“学号姓名”命名最终文件夹，不压缩。报告首页的身份、分工、自我评价需本人填写；不将自动生成的说明当作个人经历。

限制：只做末尾追加、绝对路径、固定 8 项目录；没有断电恢复、事务日志、用户认证或远程部署。异常底层 I/O 后应停止操作并检查镜像，不能保证断电或强杀后的镜像一致性。
