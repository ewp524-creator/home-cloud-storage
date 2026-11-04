# File Home 前端操作与后端接口设计

## 1. 文档目的

本文档汇总当前前端已经实现或演示过的用户操作，说明这些操作的来源，并给出后续 SpringBoot + MinIO 后端建议使用的 REST 接口。

当前前端状态：

- 数据以 `src/data/mockData.js` 和 `useFileManager.js` 中的内存数据为主，刷新页面后重置。
- 视图模式、浮动终端位置、尺寸和字号保存在浏览器 `localStorage`。
- 上传文件使用浏览器 `File` 对象保存在内存中。
- 文件夹 ZIP 使用 JSZip 在浏览器生成；模拟文件写入占位文本，真实上传文件写入原始内容。
- 分享链接、访问统计和 MinIO 服务状态目前为演示数据。
- 永久删除、移动、复制、递归分享等操作目前只修改前端内存状态。

数据库 DDL：

- [`database/file_home_schema.sql`](../database/file_home_schema.sql)

## 2. 功能来源

| 编号 | 需求来源 | 形成的前端能力 |
|---|---|---|
| R1 | 课题审批表 | SpringBoot、Vue 3、MinIO、MySQL、文件预览、分享与权限隔离 |
| R2 | 首次前端方案要求 | 文件库、网格文件卡片、分享列表、上传、右键菜单 |
| R3 | 视觉调整要求 | 浅色国际化工作台、文件树、统一分享中心和回收站 |
| R4 | 文件树与终端要求 | 左侧目录树、浮动命令终端、命令导航 |
| R5 | 浮动终端要求 | 可拖动、可缩放、字号调节、位置与尺寸记忆 |
| R6 | 文件夹能力要求 | 文件夹 ZIP、递归分享、拖拽移动、`download`、`upload` |
| R7 | 文件操作命令要求 | `cp`、`mv`、右键复制、剪贴板粘贴、`Ctrl+C`、`Ctrl+V` |
| R8 | 删除命令要求 | `delfile`、`deldir`、递归删除确认提示 |

## 3. 用户操作与后端映射

| 编号 | 用户操作 | 操作入口 | 当前前端行为 | 后端接口 |
|---|---|---|---|---|
| U01 | 登录 | 登录页 | 模拟登录并进入工作台 | `POST /auth/login` |
| U02 | 注册 | 登录页注册标签 | 模拟创建账户 | `POST /auth/register` |
| U03 | 退出登录 | 右上角头像菜单 | 返回登录页 | `POST /auth/logout` |
| U04 | 查看当前用户 | 头像、侧栏用户卡片 | 展示内存中的用户信息 | `GET /users/me` |
| U05 | 查看存储空间 | 侧栏、概览栏 | 根据内存文件大小计算 | `GET /users/me/storage` |
| U06 | 浏览文件库 | 侧栏、面包屑、文件树 | 根据 `parentId` 切换当前目录 | `GET /files?parentId=` |
| U07 | 查看最近使用 | 侧栏 | 筛选近 7 天文件 | `GET /files/recent` |
| U08 | 查看收藏 | 侧栏 | 筛选 `starred=true` | `GET /files/starred` |
| U09 | 查看标签文件 | 侧栏标签 | 按前端标签筛选 | `GET /files?tag=` |
| U10 | 搜索 | 顶部搜索框 | 对文件名与标签做本地过滤 | `GET /files?q=` |
| U11 | 类型筛选 | 文件区筛选条 | 按文档、媒体、代码等分组过滤 | `GET /files?type=` |
| U12 | 排序 | 文件区排序菜单 | 按修改时间、名称、大小排序 | `GET /files?sort=` |
| U13 | 网格/列表切换 | 文件区视图按钮 | 切换前端布局 | 前端本地偏好，无接口 |
| U14 | 单击选择 | 文件卡片、列表行 | 只选中，不打开详情 | 前端本地状态 |
| U15 | 双击文件夹 | 文件卡片、列表行、文件树 | 进入文件夹 | `GET /files?parentId=` |
| U16 | 双击文件 | 文件卡片、列表行 | 打开预览 | `GET /files/{id}/preview` |
| U17 | 查看详细信息 | 右键菜单 | 打开文件详情抽屉 | `GET /files/{id}` |
| U18 | 上传文件 | 顶部按钮、侧栏按钮、拖拽、终端 `upload` | 显示模拟上传进度并加入文件列表 | 上传会话接口 |
| U19 | 新建文件夹 | 顶部按钮、侧栏加号、文件树 | 在当前目录创建文件夹 | `POST /folders` |
| U20 | 重命名 | 右键菜单、详情抽屉 | 修改文件名或文件夹名 | `PATCH /files/{id}` |
| U21 | 收藏/取消收藏 | 文件卡片星标、右键菜单 | 修改 `starred` | `PATCH /files/{id}/star` |
| U22 | 拖拽移动 | 卡片、列表、文件树 | 将文件或文件夹移动到目标文件夹 | `POST /files/move` |
| U23 | 右键复制 | 文件/文件夹右键菜单 | 写入前端内部剪贴板 | 前端本地状态 |
| U24 | 粘贴 | 文件夹右键菜单、`Ctrl+V` | 递归复制剪贴板项目到目标目录 | `POST /files/copy` |
| U25 | 多选 | `Ctrl`、`Shift`、复选框、`Ctrl+A` | 形成批量操作集合 | 前端本地状态 |
| U26 | 批量下载 | 选择工具条 | 单文件直接下载，多项目生成 ZIP | 下载接口 |
| U27 | 批量分享 | 选择工具条 | 一次选择一个项目进入分享 | `POST /shares` |
| U28 | 批量删除 | 选择工具条、`Delete` | 移入回收站 | `POST /trash` |
| U29 | 移到回收站 | 右键菜单、详情抽屉 | 软删除，保留 30 天 | `POST /trash` |
| U30 | 恢复文件 | 回收站 | 清除删除状态 | `POST /trash/{id}/restore` |
| U31 | 永久删除 | 回收站、`delfile`、`deldir` | 从内存中移除 | 永久删除接口 |
| U32 | 清空回收站 | 回收站 | 批量永久删除 | `DELETE /trash` |
| U33 | 下载文件 | 右键菜单、详情抽屉、终端 `download` | 使用原始 File 或占位内容下载 | `GET /files/{id}/download` |
| U34 | 下载文件夹 ZIP | 右键菜单、详情抽屉、分享中心、终端 | 保留目录层级生成 ZIP | `GET /folders/{id}/download` |
| U35 | 创建或修改分享 | 右键菜单、详情抽屉、分享中心 | 同一文件只保留一条分享记录；已有记录时更新有效期、权限和提取码 | `POST /shares`，幂等更新 |
| U36 | 分享文件夹 | 文件夹右键菜单 | 当前及未来子项递归进入分享范围 | `POST /shares`，`recursive=true` |
| U37 | 复制分享链接 | 详情抽屉、分享中心 | 写入系统剪贴板 | 前端行为 |
| U38 | 撤销分享 | 分享中心 | 标记分享失效 | `DELETE /shares/{id}` |
| U39 | 下载分享内容 | 分享中心 | 下载文件或文件夹 ZIP | `GET /public/shares/{token}/download` |
| U40 | 文件树导航 | 左侧文件树 | 按目录树切换当前目录 | `GET /folders/tree` |
| U41 | 终端窗口移动 | 拖动终端标题栏 | 前端绝对定位 | 前端本地偏好 |
| U42 | 终端窗口缩放 | 拖动右下角手柄 | 修改终端宽高 | 前端本地偏好 |
| U43 | 终端字号调整 | `A-`、`A+` | 修改终端字号 | 前端本地偏好 |
| U44 | 预览多格式文件 | 双击、右键预览、详情预览 | 从 MinIO 读取真实内容；媒体支持 Range，代码/文本只读高亮 | `GET /files/{id}/preview-access`、`GET /media/{id}` |
| U45 | 通知与帮助 | 顶部按钮 | 当前为静态演示 | 可选通知接口 |
| U46 | 终端文件分享 | `sharefile <file>` | 依次输入分享时间和提取码，创建或更新文件分享 | `POST /shares` |
| U47 | 终端文件夹分享 | `sharedir <folder>` | 依次输入分享时间和提取码，创建或更新文件夹分享 | `POST /shares`，`recursive=true` |
| U48 | 终端修改分享 | `modshare <item>` | 回车保留当前有效期/提取码，也可输入新值 | `POST /shares` |
| U49 | 终端取消分享 | `ushare <item>` | 二次确认后撤销分享并同步文件状态 | `DELETE /shares/{id}` |

## 4. 终端命令映射

| 命令 | 参数 | 当前行为 | 后端需要的能力 |
|---|---|---|---|
| `ls` | 无 | 列出当前目录内容 | `GET /files?parentId=` |
| `cd` | `<folder>`、`..`、`/`、`~` | 切换当前目录 | 查询目录或使用已加载树 |
| `open` | `<name>` | 打开文件预览或进入文件夹 | 预览接口或目录查询 |
| `cp` | `<source> [destination]` | 递归复制文件或文件夹 | `POST /files/copy` |
| `mv` | `<source> <destination>` | 移动或重命名 | `POST /files/move`、`PATCH /files/{id}` |
| `delfile` | `<file>` | 永久删除文件 | `DELETE /files/{id}/permanent` |
| `deldir` | `<folder>` | 提示后递归永久删除文件夹 | `DELETE /folders/{id}/permanent?recursive=true` |
| `download` | `[name]` | 下载文件或文件夹 ZIP | 文件下载接口或文件夹 ZIP 接口 |
| `upload` | 无 | 唤起本机文件选择器 | 上传会话接口 |
| `sharefile` | `<file>` | 输入 1-30 天有效期和 4-12 位提取码后创建或更新文件分享 | `POST /shares` |
| `sharedir` | `<folder>` | 输入 1-30 天有效期和 4-12 位提取码后创建或更新文件夹分享 | `POST /shares` |
| `modshare` | `<file-or-folder>` | 修改有效期和提取码，回车可保留可回显的现有值 | `POST /shares` |
| `ushare` | `<file-or-folder>` | 二次确认后撤销分享 | `DELETE /shares/{id}` |
| `find` | `<keyword>` | 打开文件库并设置搜索词 | `GET /files?q=` |
| `mkdir` | `<name>` | 新建文件夹 | `POST /folders` |
| `home` | 无 | 返回文件库根目录 | `GET /files?parentId=root` |
| `recent` | 无 | 打开最近使用 | `GET /files/recent` |
| `starred` | 无 | 打开收藏 | `GET /files/starred` |
| `trash` | 无 | 打开回收站 | `GET /trash` |
| `pwd` | 无 | 显示当前路径 | 前端本地状态 |
| `help` | 无 | 显示命令帮助 | 前端静态内容 |
| `clear` | 无 | 清空终端输出 | 前端本地状态 |
| `exit` | 无 | 关闭终端 | 前端本地状态 |

`deldir` 当前确认文案：

```text
你文件夹内的文件也会被清掉，你确定吗？
```

只有输入 `y`、`yes`、`是`、`确认` 或 `确定` 才会执行删除。

## 5. API 基础约定

- API 前缀：`/api/v1`
- 认证方式：JWT Access Token，请求头 `Authorization: Bearer <token>`
- 用户身份：后端从 Token 中获取 `userId`，不能信任前端传入的 `ownerId`
- ID：字符串 UUID 或 Snowflake
- 时间：ISO 8601 UTC
- 普通请求：`Content-Type: application/json`
- 上传分片：`Content-Type: application/octet-stream`
- 幂等：创建目录、复制、分享和上传初始化建议支持 `Idempotency-Key`
- 并发控制：重命名/移动可增加 `version` 或 `If-Match`
- 分页：`page`、`pageSize`，返回 `items`、`total`、`page`、`pageSize`
- 统一响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "req_..."
}
```

## 6. 核心数据模型

| 表 | 关键字段 | 说明 |
|---|---|---|
| `users` | `id`, `username`, `email`, `password_hash`, `display_name`, `avatar`, `created_at` | 用户账户 |
| `file_entries` | `id`, `owner_id`, `parent_id`, `kind`, `name`, `storage_key`, `mime_type`, `size`, `extension`, `starred`, `status`, `version`, `created_at`, `updated_at`, `deleted_at` | 文件与文件夹统一树节点 |
| `shares` | `id`, `owner_id`, `root_entry_id`, `token`, `access_code_hash`, `permission`, `recursive`, `expires_at`, `revoked_at`, `created_at` | 分享根节点与公共访问规则 |
| `upload_sessions` | `id`, `owner_id`, `parent_id`, `file_name`, `mime_type`, `size`, `chunk_size`, `storage_key`, `status` | MinIO 分片上传会话 |
| `upload_parts` | `session_id`, `part_number`, `etag`, `size` | 分片 ETag 记录 |
| `file_tags` | `file_id`, `tag_id` | 文件标签关系 |
| `tags` | `id`, `owner_id`, `name`, `color` | 标签 |
| `activities` | `id`, `owner_id`, `action`, `entry_id`, `metadata`, `created_at` | 最近动态 |

关键约束：

- `file_entries.kind` 为 `FILE` 或 `FOLDER`。
- 文件夹没有实际 MinIO 对象，使用 `parent_id` 表达层级。
- `status` 建议为 `ACTIVE`、`TRASHED`。
- 同目录下文件名冲突时返回 `409`，或由前端明确要求自动生成副本名称。
- 移动或复制文件夹前，必须检查目标不能位于源文件夹自身子树。
- 软删除保留 MinIO 对象；永久删除必须同时删除数据库节点和 MinIO 对象。
- 文件夹分享通过 `root_entry_id + recursive=true` 动态计算子树，新增子项也会自动进入分享范围。

## 7. 认证与用户接口

| 方法 | 路径 | 用途 | 主要请求 | 主要响应 |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | 注册 | `username`, `email`, `password`, `displayName` | `userId` |
| POST | `/api/v1/auth/login` | 登录 | `account`, `password` | `accessToken`, `refreshToken`, `user` |
| POST | `/api/v1/auth/refresh` | 刷新 Token | `refreshToken` | 新 Token |
| POST | `/api/v1/auth/logout` | 退出 | 无 | `204` |
| GET | `/api/v1/users/me` | 当前用户 | 无 | 用户资料 |
| PATCH | `/api/v1/users/me` | 修改资料 | 可修改字段 | 更新后的用户 |
| GET | `/api/v1/users/me/storage` | 存储统计 | 无 | `usedBytes`, `quotaBytes`, `fileCount`, `folderCount` |

## 8. 文件与文件夹接口

| 方法 | 路径 | 用途 | 主要请求参数 | 主要响应 |
|---|---|---|---|---|
| GET | `/api/v1/files` | 目录列表、搜索、筛选 | `parentId`, `q`, `type`, `tag`, `sort`, `page`, `pageSize` | 文件列表、面包屑、统计 |
| GET | `/api/v1/files/{id}` | 文件详情 | 路径 ID | 元数据、所属目录、分享状态 |
| GET | `/api/v1/files/recent` | 最近使用 | `limit` | 最近文件列表 |
| GET | `/api/v1/files/starred` | 收藏列表 | `page`, `pageSize` | 收藏文件列表 |
| GET | `/api/v1/folders/tree` | 文件树 | `rootId`, `depth` | 树形节点 |
| POST | `/api/v1/folders` | 新建文件夹 | `parentId`, `name` | 新文件夹 |
| PATCH | `/api/v1/files/{id}` | 重命名 | `name` | 更新后的节点 |
| PATCH | `/api/v1/files/{id}/star` | 收藏状态 | `starred` | 更新后的节点 |
| POST | `/api/v1/files/move` | 移动 | `ids`, `targetParentId` | 成功项、冲突项 |
| POST | `/api/v1/files/copy` | 复制 | `ids`, `targetParentId`, `nameOverrides` | 新节点 ID、成功项、冲突项 |
| POST | `/api/v1/trash` | 移入回收站 | `ids` | 成功项 |
| GET | `/api/v1/trash` | 回收站列表 | 分页参数 | 删除项目、剩余保留天数 |
| POST | `/api/v1/trash/restore` | 批量恢复 | `ids` | 成功项 |
| DELETE | `/api/v1/trash/{id}` | 永久删除单项 | `id` | `204` |
| DELETE | `/api/v1/trash` | 清空回收站 | 无 | 删除数量 |
| DELETE | `/api/v1/files/{id}/permanent` | `delfile` 永久删除文件 | `id` | `204`，仅允许 `FILE` |
| DELETE | `/api/v1/folders/{id}/permanent` | `deldir` 递归删除文件夹 | `recursive=true` | 删除数量，仅允许 `FOLDER` |

## 9. 上传接口

推荐使用 MinIO Multipart Upload。

| 方法 | 路径 | 用途 | 主要请求 | 主要响应 |
|---|---|---|---|---|
| POST | `/api/v1/uploads` | 初始化上传 | `name`, `size`, `mimeType`, `parentId`, `sha256`, `chunkSize` | `uploadId`, `chunkSize`, 已上传分片 |
| GET | `/api/v1/uploads/{uploadId}` | 查询断点 | 路径参数 | 已上传分片、状态 |
| PUT | `/api/v1/uploads/{uploadId}/parts/{partNumber}` | 上传分片 | 二进制 Body、`ETag` | 分片 ETag |
| POST | `/api/v1/uploads/{uploadId}/complete` | 完成上传并创建文件节点 | `parts` 列表 | `fileId`, `name`, `size` |
| DELETE | `/api/v1/uploads/{uploadId}` | 取消上传 | 路径参数 | 清理 MinIO 临时分片 |

上传注意：

- `parentId` 必须是当前用户拥有的文件夹。
- 后端应校验文件大小、MIME 类型和扩展名。
- 同一文件可使用 `sha256` 做秒传判断。
- 客户端断线后通过 `GET /uploads/{uploadId}` 恢复。

## 10. 下载与预览接口

| 方法 | 路径 | 用途 | 说明 |
|---|---|---|---|
| GET | `/api/v1/files/{id}/download` | 下载单个文件 | 返回流或 302 到 MinIO 预签名 URL |
| GET | `/api/v1/folders/{id}/download` | 下载文件夹 | 流式 ZIP，保留目录层级 |
| GET | `/api/v1/files/{id}/preview-access` | 获取预览票据 | 返回 10 分钟短期 URL、预览类型、MIME、大小和 Range 能力 |
| GET/HEAD | `/api/v1/media/{id}?ticket=` | 读取文件内容 | 支持单段 HTTP `Range`、`206`、`Content-Range` 和 `416` |
| GET | `/api/v1/files/{id}/download` | 下载原文件 | 流式读取 MinIO，不使用占位内容 |

文件夹 ZIP 建议由后端使用流式 `ZipOutputStream` 生成，不能把整个 ZIP 一次性放进内存。压缩包内文件名需要做路径安全处理。

## 11. 分享接口

| 方法 | 路径 | 用途 | 主要请求 | 主要响应 |
|---|---|---|---|---|
| POST | `/api/v1/shares` | 创建或更新分享 | `rootEntryId`, `permission`, `days`, `accessCode`, `recursive` | `shareId`, `shareUrl`, `accessCode`, `accessCodeConfigured` |
| GET | `/api/v1/shares` | 分享列表 | `status`, `q`, 分页 | 分享记录、根节点、访问量 |
| GET | `/api/v1/shares/{id}` | 分享详情 | 路径参数 | 分享设置和统计 |
| DELETE | `/api/v1/shares/{id}` | 撤销分享 | 路径参数 | `204` |
| GET | `/api/v1/public/shares/{token}` | 打开分享页 | `token` | 分享根节点和子项 |
| POST | `/api/v1/public/shares/{token}/verify` | 校验提取码 | `accessCode` | 临时访问凭证 |
| POST | `/api/v1/public/shares/{token}/preview-access` | 获取分享预览票据 | `accessCode` | 短期媒体 URL，分享撤销后立即失效 |
| GET | `/api/v1/public/shares/{token}/download` | 下载分享内容 | `accessCode` 或临时凭证 | 文件流或文件夹 ZIP |

分享注意：

- `token` 与提取码必须分开，不能把提取码直接当作公开链接 Token。
- 对外分享 URL 固定为 `/s/{token}`，其中 `token` 是服务端生成的随机字符。
- 数据库只保存 `token_hash`，不保存可用于拼接链接的明文 Token。
- 无提取码的分享允许匿名查看真实预览；设置提取码后必须先校验才能取得预览票据。
- 下载必须先通过提取码校验，再要求用户登录或注册。
- `share_access_log.viewer_user_id` 在成功下载时必须非空。
- 数据库保存提取码哈希，不保存明文。
- 文件夹分享使用递归权限，访问时动态查询子树。
- 撤销分享后，子文件和子文件夹立即失去分享访问权限。
- 分享访问和下载计数应在后端原子更新。
- 公共接口需要防暴力破解和限流。

推荐下载流程：

```text
1. GET  /public/shares/{token}             匿名读取分享预览
2. POST /public/shares/{token}/verify      校验提取码，返回短期 verificationTicket
3. 用户未登录时跳转登录/注册
4. GET  /public/shares/{token}/download
   Authorization: Bearer <accessToken>
   X-Share-Ticket: <verificationTicket>
```

成功下载必须记录 `share_access_log.viewer_user_id`，匿名用户不能执行下载。

## 12. 活动、标签与通知接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/activities` | 最近动态 |
| GET | `/api/v1/tags` | 标签列表 |
| POST | `/api/v1/tags` | 新建标签 |
| PUT | `/api/v1/files/{id}/tags` | 替换文件标签 |
| GET | `/api/v1/notifications` | 通知列表，可选 |
| PATCH | `/api/v1/notifications/read` | 标记通知已读，可选 |

## 13. 主要错误码

| HTTP | 错误码 | 场景 |
|---|---|---|
| 400 | `INVALID_ARGUMENT` | 参数格式错误 |
| 401 | `UNAUTHORIZED` | 未登录或 Token 过期 |
| 403 | `FORBIDDEN` | 无资源权限 |
| 404 | `NOT_FOUND` | 文件、文件夹或分享不存在 |
| 409 | `NAME_CONFLICT` | 同目录名称冲突 |
| 409 | `INVALID_MOVE` | 文件夹移动进自身子树 |
| 413 | `FILE_TOO_LARGE` | 超过单文件限制 |
| 422 | `TYPE_MISMATCH` | 例如 `delfile` 指向文件夹 |
| 429 | `TOO_MANY_REQUESTS` | 分享提取码或公共接口限流 |
| 500 | `STORAGE_ERROR` | MinIO 或数据库异常 |

## 14. 前端服务层建议

后端接入后建议新增以下模块，页面组件不直接调用 `fetch`：

```text
src/services/
├─ authApi.js
├─ filesApi.js
├─ uploadsApi.js
├─ downloadsApi.js
├─ sharesApi.js
├─ previewsApi.js
└─ activitiesApi.js
```

`useFileManager.js` 保留为前端状态编排层，将当前的同步内存操作替换为异步 API 调用，并保留乐观更新、加载态、失败回滚和重试。

## 15. 后端实现优先级

1. 认证、当前用户、存储统计。
2. 文件列表、文件树、创建目录、重命名、搜索。
3. MinIO 分片上传、断点续传、下载。
4. 移动、复制、软删除、恢复、永久删除。
5. 文件夹 ZIP。
6. 文件分享与文件夹递归分享。
7. 预览 URL、缩略图和 HTTP Range。
8. 活动、标签、通知和访问统计。

## 16. 需要后端确认的契约

- 文件 ID 使用 UUID 还是 Snowflake。
- 单文件上传上限、分片大小和允许的 MIME 类型。
- 大文件夹复制和 ZIP 下载是否改为异步任务。
- 分享 Token 是否使用随机 UUID，提取码是否固定 4-6 位。
- 文件夹递归分享是否允许额外排除某些子项。
- 永久删除是立即删除 MinIO 对象，还是进入延迟清理队列。
- 同一目录重名时是返回 `409`，还是自动生成“副本”名称。
