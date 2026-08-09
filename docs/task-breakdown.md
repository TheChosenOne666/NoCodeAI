# 任务拆解与进度

## Bug 修复：代码生成 SSE 报错"系统错误"

### 背景
2026-08-09 用户在前端"生成一个不超过30行代码的个人博客网站"时，AI 回复直接显示"系统错误"。

### 根因
1. **本地模型名失效**：`application-local.yml` 中 `spring.ai.openai.model-name` 配置为 `deepseek-v3-2-251201`，火山引擎（Volces）返回 `InvalidEndpointOrModel.NotFound`，该模型 ID 已不可用。
2. **前端 SSE 字段解析错误**：`AppChatPage.vue` 中解析 SSE 数据时使用了 `parsed.d`，但后端 `StreamMessage` 序列化字段名为 `data`，导致正常片段无法拼接。
3. **SSE 错误响应中文乱码**：`GlobalExceptionHandler.handleSseError` 先 `setContentType("text/event-stream")` 再 `setCharacterEncoding("UTF-8")`，在某些 Servlet 容器下客户端按错误编码解码，导致"系统错误"显示为乱码。
4. **前端 onerror 误判重连为正常关闭**：原逻辑把 `EventSource.readyState === CONNECTING` 当成正常关闭并刷新预览，反而掩盖了连接异常。

### 修复内容
- `src/main/resources/application-local.yml`：将模型名改为 `${CHAT_MODEL:deepseek-v4-flash-ga-260731}`，支持环境变量覆盖并恢复本地可用默认模型。
- `src/main/java/.../exception/GlobalExceptionHandler.java`：先 `setCharacterEncoding("UTF-8")` 再 `setContentType("text/event-stream;charset=UTF-8")`，确保 SSE 错误事件中文不乱码。
- `xiaolou-nocode-frontend/src/pages/app/AppChatPage.vue`：
  - 修正 SSE 消息解析字段为 `parsed.data`；
  - 修正 `EventSource.onerror`：触发 onerror 即按错误处理，不再把 CONNECTING 状态误判为正常关闭。

### 验证
- Python 脚本复现：SSE 返回 `event: business-error` + 模型不存在错误信息；修复模型名后应能正常收到 `ai_response` 片段。
- 前端验证：浏览器 DevTools → Network 中 `code?appId=...` eventsource 应持续收到 `data: {"type":"ai_response","data":"..."}` 并正确拼接显示。

### 后续建议
- `application-local.yml` 中 `api-key` 仍为硬编码，建议改为 `${ARK_API_KEY:<默认值>}` 并通过环境变量注入生产环境。
- 单体版代码生成未接入 M3-3 的 AI 配置表，建议后续让 `/chat/gen/code` 优先读取数据库 AI 配置，实现运行时模型切换与错误分类提示。

## M5 精品案例作品持久化（MySQL）

### 背景
原部署方案将构建产物写入 `tmp/code_deploy/{deployKey}`，为本地磁盘。
Railway 容器文件系统为临时盘（ephemeral），容器重启后目录丢失，导致"查看作品"在精品案例（公开、跨会话访问）场景返回 404/白屏。

> 历史方案已废弃：曾尝试腾讯云 COS 公有读直链（cos-website 仍带强制下载头，浏览器下载而非展示）与 Cloudflare Worker 代理（workers.dev 国内被墙，超时失败），均不可行，已全部回退。

### 目标
将已部署的精品案例作品（前端 dist）按文件存入 MySQL `app_deploy_asset` 表，用户在无持久化盘的生产环境访问"查看作品"时由后端直接查库返回，前端 URL 逻辑不变。

### 需求描述
1. 新增 `app_deploy_asset` 表（deploy_key, file_path, content_type, file_size, content LONGBLOB）。
2. `StaticResourceController` 本地盘未命中时回退查库返回二进制内容（content_type 按文件设定）。
3. 仅导入 7 个精品案例（`app.priority=99`）的作品，普通用户部署作品仍走本地盘。
4. 前端 `getDeployUrl` 行为不变，无需改动。
5. 提供幂等导入脚本，可重复将本地 `tmp/code_deploy/{deployKey}` 导入 DB。

### 进度
- [x] 建表 SQL：`ai-no-code-app/src/main/resources/sql/app_deploy_asset.sql`
- [x] 实体 `AppDeployAsset` + Mapper `AppDeployAssetMapper.selectByDeployKeyAndPath`（显式 `@TableField` 映射，因 `map-underscore-to-camel-case: false`）
- [x] `StaticResourceController` 增加 DB 回退读取（本地盘优先 → 查库 → 404）
- [x] 导入脚本 `tmp_import/import_featured_assets.js`（Node + mysql2，仅导入 7 个精品案例，幂等）
- [x] 已导入 38 个文件到 `app_deploy_asset`（H0wUnd, wLxkTw, r7BaUv, I00Oyc, jkJ12P, WVsVTS, rpkcN3）
- [x] Maven 编译通过（`-pl ai-no-code-app`）
- [x] DB 数据完整性验证（38 条、content 可读、content_type 正确）
- [ ] 后端接口联调（需先启动 Nacos + 后端，见联调步骤）

### 联调步骤
1. 启动本地 Redis（项目依赖，如 `xiongda-redis` 容器），再启动后端：`cd <项目根> && mvn spring-boot:run`（端口 8123，单体项目，无 Nacos 依赖）。
2. 浏览器打开前端本地 5173，进入首页 → 精品案例 → 点任一作品"查看作品"。
3. 前端请求 `http://localhost:8123/api/static/{deployKey}/`；本地若 `tmp/code_deploy/{deployKey}` 存在则走本地盘，重命名该目录后可验证回退到 DB（返回 200 HTML、Content-Type 正确）。
4. 生产验证：无本地盘环境直接走 DB 路径；浏览器应正常全屏展示作品，不再白屏/下载。
5. 排错：若 404，查库 `SELECT deploy_key,file_path,file_size FROM app_deploy_asset WHERE is_delete=0;` 应含对应记录，缺失则重跑导入脚本。

## M6 站点流量统计（友盟+ U-Web，仅平台前端）

### 背景
需要在生产环境统计"零代码平台"官网的访问流量（PV/UV/来源等），便于演示与运营复盘。
友盟+ U-Web 提供异步加载的统计脚本，全站一处引入即可。

### 需求描述
1. 仅平台前端（`xiaolou-nocode-frontend`）接入友盟统计，AI 生成的作品站点不接入。
2. 统计脚本放在 `index.html` 的 `</head>` 前，站点 ID 固定为平台账户 `019fe2bb-abb8-7b42-9d36-1a970b9526f5`。
3. 后端不做任何改动（统计纯前端行为）。

### 进度
- [x] `xiaolou-nocode-frontend/index.html` 在 `</head>` 前注入友盟 U-Web 统计脚本
- [x] 后端改动已确认不做（仅平台前端、作品站点不统计）

### 联调步骤
1. 前端构建部署（Railway / 本地 `npm run build`）后，打开平台首页。
2. 浏览器 DevTools → Network，确认请求 `https://umengit-cdn.uemc.net/umeng-web.js` 返回 200。
3. 登录友盟+ 控制台（对应站点 ID），等待几分钟确认有实时访客数据上报。
