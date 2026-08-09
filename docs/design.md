# 设计文档

## SSE 错误处理与编码规范

### 1. 场景
`/app/chat/gen/code` 返回 `text/event-stream`，用于实时推送 AI 生成的代码片段。当 AI 调用失败或发生未预期异常时，后端通过 `GlobalExceptionHandler` 写入 `business-error` 事件，前端监听并展示错误。

### 2. 数据格式
- 正常片段：`data: {"type":"ai_response","data":"<代码片段>"}\n\n`
- 业务错误：`event: business-error\ndata: {"error":true,"code":50000,"message":"系统错误"}\n\n`
- 结束：`event: done\ndata: {}\n\n`

### 3. 编码约定
- 后端写入 SSE 前必须先调用 `response.setCharacterEncoding("UTF-8")`，再设置 `Content-Type: text/event-stream;charset=UTF-8`，否则中文字符在客户端可能解码为乱码。
- 前端解析 `data` 字段（而非 `d`），按 `parsed.data` 追加内容。

### 4. 错误处理
- 后端：`RuntimeException` 等未预期异常统一返回"系统错误"，避免把底层异常详情暴露给前端；具体堆栈记录到服务端日志。
- 前端：`business-error` 事件展示具体 message；`EventSource.onerror` 触发时视为连接异常，直接显示"生成失败，请重试"，不将 CONNECTING 状态误判为正常关闭。

### 5. 模型配置
- 本地开发默认模型通过 `application-local.yml` 的 `spring.ai.openai.model-name` 指定，当前默认值为 `deepseek-v4-flash-ga-260731`，支持 `CHAT_MODEL` 环境变量覆盖。
- 若模型 ID 失效，火山引擎会返回 `InvalidEndpointOrModel.NotFound`，前端表现为"系统错误"。

## 精品案例作品持久化（MySQL）

### 1. 架构决策
- **精品案例作品存 MySQL**：新增 `app_deploy_asset` 表，将已部署的精品案例前端产物（dist 文件）按文件入库，而非依赖本地临时盘或 COS。
- **为何不依赖本地盘**：生产环境（Railway）文件系统为临时盘（ephemeral），容器重启后 `tmp/code_deploy` 目录丢失，导致"查看作品"返回 404/白屏。
- **为何不用 COS**：腾讯云 2024-01 后新建桶默认域名及 cos-website 域名均强制 `Content-Disposition: attachment`，浏览器会下载而非展示；Cloudflare Worker 代理方案在国内被墙（workers.dev 无法访问）。经评估后采用 MySQL 直存方案，最稳、零额外基础设施依赖。
- **回退策略**：`StaticResourceController` 优先读本地盘（本地开发体验不变），本地盘不存在时回退查 `app_deploy_asset` 表，前端 URL 逻辑无需任何改动。
- **适用范围**：仅导入精品案例（`app.priority = 99` 的 7 个作品）。普通用户部署的作品仍走本地盘 / 后续部署流程，不强制入库（避免 BLOB 体积膨胀）。

### 2. 表结构（app_deploy_asset）
| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | 主键 |
| deploy_key | VARCHAR(64) | 部署标识，对应 `app.deployKey` |
| file_path | VARCHAR(512) | 文件相对路径，如 `index.html` / `assets/xxx.js` |
| content_type | VARCHAR(128) | MIME 类型 |
| file_size | BIGINT | 字节数 |
| content | LONGBLOB | 文件二进制内容 |
| create_time / update_time | DATETIME | 时间戳 |
| is_delete | TINYINT | 逻辑删除 |

建表 SQL：`ai-no-code-app/src/main/resources/sql/app_deploy_asset.sql`。

### 3. 关键流程

#### 3.1 StaticResourceController.serveStaticResource（查看作品）
```
1. 解析 deployKey 与资源相对路径
2. 目录访问（无尾斜杠）→ 301 重定向到带 / 的 URL
3. 默认路径 → index.html
4. 本地盘命中（PREVIEW_ROOT_DIR/{deployKey}/{path} 存在）→ 直接返回文件
5. 本地盘未命中 → 查 app_deploy_asset 表（deploy_key + file_path）
   - 命中：以 ByteArrayResource + content_type 返回二进制内容
   - 未命中：404
```
注意：因 `map-underscore-to-camel-case: false`，实体字段用 `@TableField` 显式映射列名。

### 4. 前端预览 URL（不变）
`env.ts`：
```
getDeployUrl(deployKey) = `${API_BASE_URL}/static/${deployKey}/`
```
前端无需改动；生产环境访问 `/api/static/{deployKey}/` 由后端自动从 DB 取作品返回。

### 8. 生成后即时预览（/api/static/preview）
- **场景**：用户在对话中生成 HTML/Vue 代码后，右侧"生成后的网页展示"iframe 需要即时预览刚生成的作品。
- **落盘位置**：生成流程（`AiCodeGeneratorFacade.processCodeStream` 的 `doOnComplete`）把作品写入
  `CODE_OUTPUT_ROOT_DIR/{codeGenType}_{appId}/index.html`，其中 `CODE_OUTPUT_ROOT_DIR = user.dir/tmp/code_output`。
- **早期 404 根因**：原预览 URL 指向 `/api/static/{codeGenType}_{appId}/`，由 `StaticResourceController.serveStaticResource`
  从 `PREVIEW_ROOT_DIR = CODE_DEPLOY_ROOT_DIR`（`user.dir/tmp/code_deploy`）读取；但生成代码在 `code_output`、
  部署资源在 `code_deploy`，**两目录分离**导致预览永远 404（本地因目录曾一致而偶发可用，生产环境稳定复现）。
- **修复（2026-08-09）**：新增独立预览路由 `GET /api/static/preview/{sourceDir}/**`，由
  `StaticResourceController.servePreviewResource` 从 `CODE_OUTPUT_ROOT_DIR` 读取；
  前端 `getStaticPreviewUrl` 改为返回 `/api/static/preview/{codeGenType}_{appId}/`。
  抽取 `serveFromRoot(rootDir, prefix, dbKey, request)` 共用逻辑，预览分支**不查库**（预览内容来自生成输出目录，非持久化部署资源）。
- **注意（Railway 临时盘）**：`CODE_OUTPUT_ROOT_DIR` 同样位于 Railway 临时文件系统，容器重启 / 新实例会清空，
  因此「即时预览」仅在**同一次运行、生成后立即查看**有效；跨重启预览需重新生成或走部署流程（写 `code_deploy` + 查库回退）。
- **前端改动**：`xiaolou-nocode-frontend/src/config/env.ts` 的 `getStaticPreviewUrl` 路径前缀由 `/static/` 改为 `/static/preview/`。
- **旧路径兼容（2026-08-09 补充）**：`serveStaticResource` 在部署目录与 DB 均 404 时，回退到 `CODE_OUTPUT_ROOT_DIR/{deployKey}` 读取，
  使**未更新前端的旧请求** `/api/static/{type}_{appId}/` 也能预览，避免前端未部署时预览失效。
- **编辑模式需同源（2026-08-09 补充）**：可视化编辑（`VisualEditor` 向预览 iframe 注入脚本）依赖 `iframe.contentDocument`，
  跨域时该属性为 `null`（同源策略），脚本注入被静默吞掉，编辑模式完全失效。量产环境前端在 Cloudflare（`xiaolou-nocode.pages.dev`）、
  预览 iframe 若用 `VITE_API_BASE_URL` 拼接的绝对 Railway 域名则跨域。修复：`getStaticPreviewUrl` 与 `AppChatPage.vue` 的
  `preview-ready` 分支均改为**相对路径** `/api/static/preview/...`，保证 iframe 与前端页面同源。API 请求（fetch/EventSource）仍用 `API_BASE_URL` 绝对域名，不受影响。
- **Cloudflare Pages rewrite（2026-08-10 补充）**：相对路径 `/api/static/preview/...` 在生产环境会命中 Cloudflare Pages 的静态托管，
  而 Pages 上不存在该目录，会 fallback 返回前端 `index.html`，导致预览显示平台首页。修复：在 `public/_redirects` 添加
  `200` rewrite 规则，把 `/api/static/preview/*` 代理到 Railway 后端 `https://nocodeai-production.up.railway.app/api/static/preview/:splat`。
  状态码 `200` 为透明代理，用户/iframe 无感知，仍保持与前端页面同源，编辑模式继续可用。

### 9. 输入框清空 / 保留策略（2026-08-09）
- **场景**：应用聊天页发送提示词后，期望"生成成功则清空输入框、生成失败则保留输入以便重试"。
- **实现**：`sendMessage` 发送时不再立即清空，仅记录 `lastUserInput`；成功路径（`done` 事件 / Vue `preview-ready`）置 `userInput = ''`；
  失败路径（`business-error` / `handleError` / Vue `error`）恢复 `userInput = lastUserInput`，并释放 `isGenerating` 锁。

### 5. 导入脚本
`tmp_import/import_featured_assets.js`（Node + mysql2）：
- 仅导入 7 个精品案例 deployKey：`H0wUnd, wLxkTw, r7BaUv, I00Oyc, jkJ12P, WVsVTS, rpkcN3`
- 递归读取 `tmp/code_deploy/{deployKey}` 下所有文件，规范化路径（统一 `/`），按扩展名推断 content_type
- 先 `DELETE FROM app_deploy_asset WHERE deploy_key = ?` 再 `INSERT`，保证幂等可重跑
- 运行前先执行建表 SQL（IF NOT EXISTS 幂等）
- 运行：`cd tmp_import && npm i mysql2 && node import_featured_assets.js`
- 数据库连接：host=localhost, port=3306, user=root, password=123456, database=nocode（与 application.yml 一致）

当前已导入 38 个文件（截至 2026-08-09）。

### 6. 破坏性变更提示
- 无前端/公开接口签名变更，`getDeployUrl` 行为不变。
- 新增数据库表 `app_deploy_asset`，需在部署前执行建表 SQL。
- 删除应用目前不会级联清 `app_deploy_asset`（精品案例为固定数据，删除风险低；如需要可后续补 `removeById` 清理）。

### 7. 调试检查清单
1. 启动 Nacos（项目依赖，本地需 `127.0.0.1:8848`）与后端（`mvn -pl ai-no-code-app spring-boot:run`，端口 8123）。
2. 浏览器 DevTools → Network → 点击精品案例"查看作品"，请求 `http://localhost:8123/api/static/{deployKey}/` 应返回 200 且为 HTML。
3. 若本地 `tmp/code_deploy/{deployKey}` 存在，走本地盘；重命名该目录后再次访问应回退到 DB 返回（验证 DB 路径生效）。
4. 直接查库验证：`SELECT deploy_key, file_path, file_size FROM app_deploy_asset WHERE is_delete=0;` 应含 38 条记录。

## 站点流量统计（友盟+ U-Web，仅平台前端）

### 范围
仅 `xiaolou-nocode-frontend` 接入友盟统计；AI 生成的作品站点不接入（避免统计噪音、且站点 ID 与平台账户绑定）。

### 实现
- 在 `xiaolou-nocode-frontend/index.html` 的 `</head>` 前注入：
  ```html
  <script src="https://umengit-cdn.uemc.net/umeng-web.js" website-id="019fe2bb-abb8-7b42-9d36-1a970b9526f5" async></script>
  ```
- 纯前端行为，后端无改动。站点 ID 为平台友盟账户固定值。

### 验证
构建部署后，浏览器 Network 确认 `umeng-web.js` 返回 200；友盟控制台对应站点可见实时数据。
