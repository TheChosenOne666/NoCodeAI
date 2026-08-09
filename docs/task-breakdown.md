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

## M7 生成后即时预览修复（/api/static/preview）

### 背景
对话生成 HTML/Vue 代码后，右侧"生成后的网页展示"iframe 预览请求 `/api/static/{codeGenType}_{appId}/` 在生产环境稳定返回 404。
根因：生成代码落盘于 `CODE_OUTPUT_ROOT_DIR`（`tmp/code_output`），而预览路由 `serveStaticResource` 从 `CODE_DEPLOY_ROOT_DIR`（`tmp/code_deploy`）读取——两目录分离。部署资源与生成输出本就分目录，原设计把预览也指向部署目录是错误耦合。

### 需求描述
1. 新增独立预览路由 `GET /api/static/preview/{sourceDir}/**`，从 `CODE_OUTPUT_ROOT_DIR` 读取生成产物。
2. 抽取 `serveFromRoot(rootDir, prefix, dbKey, request)` 共用逻辑，部署资源保留 DB 回退，预览分支不查库。
3. 前端 `getStaticPreviewUrl` 改为 `/api/static/preview/{codeGenType}_{appId}/`，Vue 项目仍追加 `dist/index.html`。
4. 不影响部署/`getDeployUrl` 逻辑。

### 进度
- [x] `StaticResourceController` 新增 `servePreviewResource` + 抽取 `serveFromRoot`
- [x] 前端 `env.ts` 的 `getStaticPreviewUrl` 路径前缀改为 `/static/preview/`
- [x] `serveStaticResource` 404 时回退 `code_output`（兼容未部署前端）/api/static/{type}_{appId}/ 旧路径
- [x] 单测 `StaticResourceControllerTest`（命中返回 Resource、缺失返回 404、旧路径回退命中），3 例全过
- [x] 后端 Maven 编译通过（`mvn -q -o compile`）
- [ ] 前后端联调（步骤见下）

### 联调步骤
1. 后端重新部署到 Railway（代码已推 NoCodeAI/main 的对应修复提交）。
2. 前端重新构建部署（Cloudflare Pages：xiaolou-nocode.pages.dev，确认其部署源与本次改动同步）。
3. 浏览器打开平台 → 进入任一应用聊天页 → 生成一段 HTML 代码。
4. DevTools → Network 观察预览 iframe 请求应为 `https://nocodeai-production.up.railway.app/api/static/preview/html_<appId>/`，状态 200 且返回生成的 HTML。
5. 排错：若仍 404，去 Railway 后端 Logs 搜 `保存成功，路径为：` 确认文件落盘目录；对比预览请求的实际路径是否一致（应为 `code_output/html_<appId>`）。

## M7-1 前端编辑模式失效修复 + 输入框清空/保留逻辑

### 背景
生产环境（前端 Cloudflare `xiaolou-nocode.pages.dev`，后端 Railway `nocodeai-production.up.railway.app`）下，点击应用聊天页"编辑模式"按钮无任何反应、鼠标无变化；且发送消息后输入框内容在生成结束后仍保留。

### 根因
1. **编辑模式跨域失效**：预览 iframe 的 `src` 由各环境 `VITE_API_BASE_URL`（生产为 `https://nocodeai-production.up.railway.app/api`）拼接成绝对域名 URL。前端页面托管在 Cloudflare 域，iframe 指向 Railway 域 → 跨域 → `iframe.contentDocument` 为 `null`（同源策略保护）→ `VisualEditor.injectEditScript` 注入脚本时 `waitForIframeLoad` 进入 `catch` 被静默吞掉，脚本永远注入不了，编辑模式完全失效。本地开发 `.env.development` 用 `/api` 相对路径，同源所以本地正常、生产失效。
2. **输入框清空时机错误**：原 `sendMessage` 在发送瞬间立即 `userInput.value = ''`，无论生成成功或失败都清空。需求是"生成成功清空、生成失败保留以便重试"。

### 需求描述
1. 预览 iframe 的 `src` 改为**相对路径** `/api/static/...`，保证与前端页面同源，恢复 `contentDocument` 访问，使 `VisualEditor` 脚本可注入。
2. 涉及位置：`env.ts` 的 `getStaticPreviewUrl`（去掉 `API_BASE_URL` 前缀，直接返回 `/api/static/preview/...`）；`AppChatPage.vue` 的 `handleCodeGenStreamEvent` 的 `preview-ready` 分支（原把 `streamEvent.url` 拼成 `API_BASE_URL` 绝对域名）改为保留相对路径。
3. 输入框逻辑：发送时不立即清空，记录 `lastUserInput`；生成成功（`done` / Vue `preview-ready`）清空 `userInput`；生成失败（`business-error` / `handleError` / Vue `error`）恢复 `userInput = lastUserInput`。

### 进度
- [x] `env.ts` `getStaticPreviewUrl` 改相对路径
- [x] `AppChatPage.vue` `preview-ready` 分支改相对路径（含 `?t=` 时间戳防缓存）
- [x] `AppChatPage.vue` 新增 `lastUserInput` ref，`sendMessage` 不再立即清空
- [x] 成功路径（`done` / `preview-ready`）清空 `userInput`；失败路径（`business-error` / `handleError` / `error`）恢复 `userInput`
- [x] 前端 type-check + build 通过（`npm run build`）
- [x] 已 commit `0fd84a0` 并 push `nocode/master`（Cloudflare 自动部署）
- [ ] 前后端联调（步骤见下）

### 联调步骤
1. Cloudflare Pages 部署完成后，浏览器打开平台前端（生产 `xiaolou-nocode.pages.dev` 或本地 `npm run dev`）。
2. 进入任一应用聊天页 → 生成一段 HTML/Vue 代码 → 等待右侧预览 iframe 加载完成。
3. 点击"编辑模式"按钮 → 鼠标移到预览页面元素上应出现虚线选中框、点击元素右侧弹出编辑面板（验证跨域修复生效）。
4. 输入框测试：输入一段提示词发送 → 生成成功后输入框应清空；若故意触发失败（如限流/网络断开），输入框应保留刚输入的内容以便重试。

## M7-2 Cloudflare Pages 生产环境预览显示为平台首页

### 背景
M7-1 把预览 iframe `src` 改为相对路径 `/api/static/preview/...` 后，编辑模式跨域问题修复。但在 Cloudflare Pages 生产环境部署后，右侧预览 iframe 显示为平台首页（No Code 零代码导航条），而非生成的网页。

### 根因
Cloudflare Pages 是纯静态托管，当前端请求相对路径 `/api/static/preview/...` 时，Pages 找不到该路径对应的静态文件，会按默认行为 fallback 返回前端 `index.html`，于是 iframe 加载的是平台首页。

本地开发时 Vite dev server 会把 `/api/...` 代理到 Java 后端；生产环境没有自动代理，需要显式把该路径 rewrite 到 Railway 后端。

### 修复内容
在前端 `public/_redirects` 添加 Cloudflare Pages rewrite 规则：

```txt
/api/static/preview/*  https://nocodeai-production.up.railway.app/api/static/preview/:splat  200
```

- 状态码 `200` 表示 rewrite（透明代理），用户无感知，且 iframe 仍与前端页面同源，`contentDocument` 可访问。
- 构建后 `dist/_redirects` 会随前端产物一起部署到 Cloudflare Pages。

### 进度
- [x] 新增 `public/_redirects` 预览路径 rewrite 规则
- [x] 重新构建验证 `dist/_redirects` 已生成
- [x] 文档沉淀（本节）
- [ ] 生产验证（步骤见下）

### 联调步骤
1. 将新的 `dist/` 目录手动上传到 Cloudflare Pages（或触发 git 部署）。
2. 浏览器打开平台 → 进入应用聊天页 → 生成 HTML/Vue 代码。
3. DevTools → Network 中观察预览 iframe 请求的 URL 仍为 `/api/static/preview/<type>_<appId>/...`（同源），但响应内容应是后端生成的网页，状态 200。
4. 若仍显示平台首页，检查 Cloudflare Pages 部署是否包含 `dist/_redirects`，或规则目标域名是否仍为当前 Railway 生产域名。

## Bug 修复：生产环境代码生成报 `Redis NOAUTH Authentication required`

### 背景
2026-08-09 单体项目部署上线后，前端发起代码生成请求（`/app/chat/gen/code`）时后端抛 `RuntimeException`，日志根因为 `redis.clients.jedis.exceptions.JedisAccessControlException: NOAUTH Authentication required.`。调用链：`AppController.chatToGenCode` → `AppServiceImpl.chatToGenCode` → `AiCodeGeneratorFacade.generateAndSaveCodeStream` → `MessageWindowChatMemory.messages` → `RedisChatMemoryStore.getMessages` → Jedis 连接 Redis 未认证。

### 根因
`RedisChatMemoryStoryConfig` 在构造 LangChain4j 的 `RedisChatMemoryStore` 时只注入了 `host / port / ttl`，未透传 `spring.data.redis` 的 `username / password`。生产 Redis 开启了密码认证，因此读取聊天记忆时连接被拒。
- Spring 的 `StringRedisTemplate`（HTTP Session 存储）已正确读取 `spring.data.redis.password`，故登录态正常；
- 唯独 LangChain4j 的聊天记忆存储由 `RedisChatMemoryStore` 独立建连，遗漏了密码，导致未授权错误。

### 修复内容
- `src/main/java/.../config/RedisChatMemoryStoryConfig.java`：
  - 新增绑定字段 `username`、`password`（`@Value("${spring.data.redis.username:}")` / `"${spring.data.redis.password:}"`）；
  - builder 增加 `.user(username).password(password)`，使记忆存储复用与 `spring.data.redis` 一致的账号密码。

### 验证
- 本地单体启动后端（依赖 Redis，如 `xiongda-redis` 容器），若该容器已设密码，前端发起代码生成应不再抛 `NOAUTH`；若本地 Redis 无密码，`.user/.password` 为空串，行为与修复前一致（向后兼容）。
- 生产验证：重新构建镜像部署后，前端发起对话生成代码，后端日志不再出现 `JedisAccessControlException: NOAUTH`；`/api/app/chat/gen/code` 正常返回 SSE 片段。

### 破坏性变更提示
- 无公开接口签名变更。
- 仅在 Redis 启用密码的生产环境修复认证缺失；本地无密码 Redis 不受影响。

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
