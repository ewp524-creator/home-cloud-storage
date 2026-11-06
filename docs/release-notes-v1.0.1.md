# File Home v1.0.1

这是一个部署兼容性补丁版本。

## 修复

- 修复后端 Dockerfile 仍引用 `0.0.1-SNAPSHOT` JAR 名称的问题
- Docker 构建现在自动匹配当前版本的后端可执行 JAR
- 统一后端、前端和命令行客户端版本号为 `1.0.1`

## 升级

已经运行 v1.0.0 的用户可以保留 Docker 数据卷，拉取 v1.0.1 后执行：

```bash
./scripts/filehome.sh stop
./scripts/filehome.sh start
```

数据卷不会因为停止或重新启动而删除。

## 发布资产

- `filehome-v1.0.1-backend.jar`
- `filehome-v1.0.1-frontend-dist.zip`
- `filehome-v1.0.1-fhterm.zip`
- `filehome-v1.0.1-checksums.txt`

功能和限制与 v1.0.0 相同。
