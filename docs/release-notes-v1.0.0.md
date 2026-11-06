# File Home v1.0.0

File Home 是一个面向个人和中小团队的私有云文件管理系统，采用 Spring
Boot、Vue、MySQL、MinIO、Redis、Kafka 和 Nginx 构建。

## 核心功能

- 用户注册、图形验证码、登录、退出和账号停用
- FH_SESSION HttpOnly 服务端会话、CSRF 防护和会话撤销
- 个人资料、头像、偏好设置和密码修改
- 文件与文件夹上传、下载、重命名、移动、复制和收藏
- MinIO 大文件分片上传、SHA-256 校验和断点续传
- 文件夹 ZIP 下载和 HTTP Range 流式读取
- 文件树、搜索、筛选、排序和回收站
- 随机令牌分享、提取码和登录后下载
- 图片、音视频、PDF、文本、代码和 DOCX 只读预览
- Kafka 异步活动审计和数据库降级写入
- Windows PowerShell 启动脚本和 Linux Docker Compose 部署
- 认证命令行客户端 `fhterm`

## 安全特性

- 浏览器会话令牌只通过 HttpOnly Cookie 传递
- Redis 保存一次性图形验证码，验证后立即作废
- PBKDF2 密码哈希和分享提取码校验
- 短期 HMAC 预览票据和资源所有权校验
- 登录、注册、分享、验证码和下载接口限流
- CSP、CSRF、`nosniff`、CORS 和点击劫持防护
- 密钥与本地存储路径通过被忽略的配置提供

## 技术栈

- Spring Boot 4.0.6 / Java 17
- Vue 3 / Vite 8
- MyBatis / MySQL 8
- MinIO / Redis / Kafka / Nginx

## 发布资产

- `filehome-v1.0.0-backend.jar`：可执行后端应用
- `filehome-v1.0.0-frontend-dist.zip`：生产前端静态资源
- `filehome-v1.0.0-fhterm.zip`：命令行客户端
- `filehome-v1.0.0-checksums.txt`：SHA-256 校验值

GitHub 自动生成的 Source code 压缩包包含完整源码、部署脚本和文档。

## 运行要求

- Java 17
- MySQL 8
- Redis 8 或兼容版本
- MinIO
- Kafka 4
- Nginx

详细启动方法见仓库 `README.md` 和 `backend/README.md`。

## 已知限制

- 当前默认部署是单节点架构，不是企业级高可用集群
- 未集成病毒扫描、MFA、邮件验证和密码找回
- 浏览器认证使用服务端会话，不是浏览器端 JWT
- CLI 使用独立 JWT Bearer 认证通道
- 大规模并发和容量指标仍需在目标校园网络中重新压测

## 升级说明

这是首个正式发布版本。部署前请通过环境变量或本地忽略配置提供数据库、
Redis、MinIO、JWT、分享令牌和存储目录等参数，不要提交 `.env` 或
`.secrets` 内容。
