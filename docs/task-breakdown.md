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
3. 导入 7 个精品案例（`app.priority=99`）的作品入库。
4. 前端 `getDeployUrl` 行为不变，无需改动。
5. 提供幂等导入脚本，可重复将本地 `tmp/code_deploy/{deployKey}` 导入 DB。

### 进度
- [x] 建表 SQL：`src/main/resources/sql/app_deploy_asset.sql`
- [x] 实体 `AppDeployAsset` + Mapper `AppDeployAssetMapper.selectByDeployKeyAndPath`（显式 `@TableField` 映射，因 `map-underscore-to-camel-case: false`）
- [x] `StaticResourceController` 增加 DB 回退读取（本地盘优先 → 查库 → 404）
- [x] 导入脚本 `tmp_import/import_featured_assets.js`（Node + mysql2，仅导入 7 个精品案例，幂等）
- [x] 已导入 38 个文件到 `app_deploy_asset`（H0wUnd, wLxkTw, r7BaUv, I00Oyc, jkJ12P, WVsVTS, rpkcN3）
- [x] Maven 编译通过（`-pl ai-no-code-app`）
- [x] DB 数据完整性验证（38 条、content 可读、content_type 正确）
- [x] **【2026-08-10 扩展】普通用户「部署」也入库**：`AppServiceImpl.deployApp` 由"复制文件到本地 `tmp/code_deploy/{deployKey}`"改为"遍历源目录（HTML 项目根 / Vue dist）递归写入 `app_deploy_asset` 表（先删同 deployKey 旧记录再批量插入），访问 URL 仍为 `/api/static/{deployKey}/`，由 `StaticResourceController` 查库返回，彻底摆脱本地磁盘与 localhost 绑定"。即部署产物存储方式与精品案例完全一致。
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

## M7-2 未部署预览持久化（重新进入不再丢失，2026-08-10）

### 背景
线上（Cloudflare/Railway）用户反馈：AI 生成后可正常预览，但**重新进入应用后预览没了**。
根因：未部署预览依赖 `tmp/code_output`（Railway 临时盘），容器重启/重建后目录清空 → `/api/static/preview/...` 404。
需求：**无论是否部署，生成后原本能预览，下次点进来也要保持展示该预览**（持久化）。

### 需求描述
1. 生成完成时把产物写入 `app_deploy_asset` 表，deployKey = `preview_{appId}`，与已部署作品同源查库。
2. 前端未部署时预览 URL 改为 `/api/static/preview_{appId}/`（Vue 追加 `dist/index.html`），由 `StaticResourceController` 查库返回，不再依赖临时盘。
3. 已部署路径维持 `/api/static/{deployKey}/` 不变。

### 进度
- [x] `AppServiceImpl` 新增 `savePreviewAssets(appId, sourceDir)`（`@Override`，按 `preview_{appId}` 写库，先删后插）
- [x] `AppService` 接口声明 `savePreviewAssets(Long, File)`（补充 `import java.io.File`）
- [x] `AiCodeGeneratorFacade.processCodeStream` 的 `doOnComplete`：HTML/MULTI_FILE 落盘成功后调用 `appService.savePreviewAssets(appId, savedDir)`
- [x] `VueProjectGenStreamManager.onCompleteResponse`：Vue 构建成功后调用 `appService.savePreviewAssets(appId, distDir)`，且 `previewUrl` 改为 `/api/static/preview_{appId}/dist/index.html`
- [x] 前端 `env.ts` 的 `getStaticPreviewUrl` 路径改为 `/api/static/preview_{appId}/`（Vue 追加 `dist/index.html`）
- [x] `AppChatPage.vue` `updatePreview` 注释更新（未部署也持久化查库）；`preview-ready` 分支复用 `getStaticPreviewUrl`
- [x] 后端 Maven 编译通过（`mvn -q -o compile`）
- [x] 前端 type-check 通过（`vue-tsc --noEmit`）
- [x] 修复循环依赖：`AiCodeGeneratorFacade` 与 `VueProjectGenStreamManager` 注入 `AppService` 加 `@Lazy`，解决 Railway 启动失败
- [x] 修正 Cloudflare `_redirects`：规则由 `/api/static/preview/*` 改为 `/api/static/*`，匹配新的 `/api/static/preview_{appId}/` 路径
- [ ] 前后端重新部署 + 联调（步骤见下）

### 联调步骤
1. 后端重新部署到 Railway（代码改动需重新构建镜像/重启）。
2. 前端重新构建部署到 Cloudflare Pages（确认 `env.ts` 改动生效）。
3. 浏览器打开平台 → 进入任一应用聊天页 → 生成一段 HTML/Vue 代码 → 等待右侧预览加载成功。
4. **重新进入该应用**（刷新/关闭重开）→ 右侧预览应仍正常展示，不再 404（验证持久化）。
5. 排错：Railway 后端 Logs 应出现 `预览资源已持久化到数据库，deployKey=preview_{appId}，文件数=N`；
   直接访问 `/api/static/preview_{appId}/` 返回 200 HTML。若 404，确认 `app_deploy_asset` 表存在该 deployKey 记录。

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

## M7-3 跨域登录态失效修复（点「查看对话」跳回首页 / 获取应用信息失败，2026-08-10）

### 背景
生产环境（前端 Cloudflare `xiaolou-nocode.pages.dev`，后端 Railway `nocodeai-production.up.railway.app`）下，用户反馈：点应用「查看对话」后报错「获取应用信息失败」并直接跳回首页，此前一直正常、突然不行。

### 根因
前端 `VITE_API_BASE_URL` 在生产为 Railway 绝对域名（`https://nocodeai-production.up.railway.app/api`）。所有 API 请求（含 `getAppVoById`、`listAppChatHistory`）跨域发往 Railway 域，且 `request.ts` 设 `withCredentials: true`。

- 登录时 Railway 下发的 `SESSION` cookie 属于 Railway 域。
- 跨站请求（Cloudflare 页 → Railway 域）浏览器携带 cookie 受 `SameSite`/跨站限制，登录态 cookie 无法随请求到达 Railway → Railway 认为未登录。
- `listAppChatHistory` 等需登录接口返回 `code:40100`；`request.ts` 响应拦截器在 `code===40100` 时执行 `window.location.href = '/user/login?redirect=...'`，将用户踢回登录页（表现为「跳回首页 / 获取应用信息失败」）。
- 之前"正常"是因为旧版部分路径/缓存恰好命中，或 cookie 在窗口期内可携带；会话失效 / 重新部署后即稳定复现。

### 需求描述
让前端 API 请求与页面**同源**（Cloudflare 域），由 Cloudflare Pages Functions 反向代理到 Railway，使登录 cookie 自动同源携带、Railway 能识别登录态，从根上消除跨域 401。

### 修复内容
1. **新增 `functions/api/[[path]].js`**（Cloudflare Pages Functions catch-all 代理）：将 `/api/*` 同源反向代理到 Railway，原样转发 method / headers（含 `cookie`）/ body / query，并透传响应（含 `set-cookie`）。Functions 运行在服务端，可代理外部域（与 `_redirects` 的 200 rewrite 只能站内重写不同）。
2. **`.env.production`**：`VITE_API_BASE_URL` 由 `https://nocodeai-production.up.railway.app/api` 改为 `/api`（同源相对路径）。开发环境 `.env.development` 本就是 `/api`（Vite proxy），不受影响。
3. 既有 `functions/api/static/preview/[[path]].js` 仍保留（更具体路由优先于 catch-all，无冲突）。
4. 后端无需改动（CORS 已 `allowedOriginPatterns("*")` + `allowCredentials(true)`，会反射请求 Origin）。

### 进度
- [x] 新增 `functions/api/[[path]].js` 同源反代
- [x] `.env.production` `VITE_API_BASE_URL=/api`
- [x] 前端 `pure-build` 确认产物 JS 含 `gu="/api"`、无 Railway 绝对域名
- [x] 部署到 Cloudflare Pages（`91c79844.xiaolou-nocode.pages.dev`，自定义域已 promote 至同源版本）
- [x] 验证：同源 `/api/chatHistory` 经 Functions 返回 `200 application/json` 且透传 `set-cookie: SESSION`；Railway 实测接口正常
- [ ] 浏览器实测（用户用自己账号登录后点「查看对话」应不再跳首页）

### 破坏性变更提示
- 用户**需重新登录一次**：旧版登录态 cookie 存在 Railway 域，新版改为同源 Cloudflare 域后，浏览器在 Cloudflare 域无旧 cookie，首次进入会「未登录」；重新登录后 Railway 经 Functions 下发的 `set-cookie` 存入 Cloudflare 域，之后即正常。这是一次性迁移代价。

### 联调步骤
1. 浏览器打开 `https://xiaolou-nocode.pages.dev`，**先退出再重新登录**（清掉旧 Railway 域 cookie 影响）。
2. 进入「我的作品」→ 点任一应用「查看对话」→ 应正常进入聊天页、加载历史、右侧预览展示，不再报「获取应用信息失败」、不再跳回首页。
3. DevTools → Network：所有 `/api/...` 请求应发往 `xiaolou-nocode.pages.dev`（同源），不再发往 `railway.app`；`listAppChatHistory` 返回 `code:0`。
4. 排错：若仍跳登录页，确认浏览器访问的是最新部署（强制刷新 Ctrl+F5 清旧 JS 缓存）；确认登录接口返回 `code:0` 且响应带 `set-cookie`。

## M7-3.1 查看对话崩溃回归修复（codeGenType is not defined，2026-08-10）

### 背景
M7-3 同源代理部署后，用户点「查看对话」仍报「获取应用信息失败」并跳回首页，且硬刷新、换设备（手机微信）均复现——非缓存问题，是代码回归。

### 根因
`AppChatPage.vue` 的 `updatePreview` 函数（M7 preview 持久化提交引入）：
```js
const deployKey = appInfo.value?.deployKey
let newPreviewUrl: string
if (deployKey) {
  newPreviewUrl = getDeployUrl(deployKey)
} else {
  const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML  // ← 块级作用域
  newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
}
previewUrl.value = newPreviewUrl
previewReady.value = true
if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {  // ← 分支外用 codeGenType → ReferenceError
```
`codeGenType` 用 `const` 声明在 `else` 块内（块级作用域），但后面的 `if (codeGenType === ...)` 在分支外使用。
当应用**已部署**（有 `deployKey`）走 `if` 分支时，`codeGenType` 未定义，抛 `ReferenceError: codeGenType is not defined`，
被 `fetchAppInfo` 的 try 捕获 → 弹「获取应用信息失败」+ `router.push('/')` 跳回首页。
属于「接口正常（code:0）、但前端渲染阶段崩溃」的典型回归，与 M7-3 跨域问题无关。

### 修复内容
1. `AppChatPage.vue` `updatePreview`：将 `const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML` 提前到函数顶层作用域，使 `if (codeGenType === ...)` 在 `deployKey` 分支下也能正确取值。
2. 诊断期间临时加的分步 try/catch + `extractErrorDetail` 上屏逻辑已在确认根因后还原为原正常实现。

### 进度
- [x] `updatePreview` 把 `codeGenType` 提升为函数级变量（消除 ReferenceError）
- [x] 还原 `fetchAppInfo` 为正常实现（移除诊断代码）
- [x] 重新构建 + 部署（诊断版 `a1ece581` 验证通过 → 清理版 `26bcd815`）

### 破坏性变更提示
- 无。纯前端作用域 bug 修复，接口与数据结构未变。

### 联调步骤
1. 浏览器打开最新部署，登录后进入「我的作品」→ 点任一**已部署**应用「查看对话」→ 应正常进入聊天页、加载历史、右侧预览展示，不再报「获取应用信息失败」、不再跳回首页。
2. 同时验证**未部署**应用「查看对话」也正常（走 `getStaticPreviewUrl` 分支）。

## M7-4 登录/注册页背景图恢复（2026-08-10）

### 背景
用户反馈登录/注册页背景图消失，背景变成一张被拉伸的 logo。

### 根因
原背景大图 `src/assets/login-bg.png` 已在工作区与 git 历史中彻底丢失（`git cat-file` 对象不存在，无法恢复；早前某次「删除不必要文件」误删）。提交 `f704266` 临时用 `logo.png` 顶替 `loginBg` 引用，导致背景是一片被拉伸的 logo，视觉上等于「背景图没了」。

### 修复内容
1. 用 AI 生成一张风格契合的登录背景大图（淡蓝紫科技感 + 低代码积木元素 + 淡纹理），保存到 `src/assets/login-bg.png`（1536×1024，约 1.08MB）。
2. `UserLoginPage.vue`（原 `import loginBg from '@/assets/logo.png'`）与 `UserRegisterPage.vue` 的 `loginBg` 引用改回 `@/assets/login-bg.png`。
3. 两页 `.userLoginBg` 样式本就支持大图（`object-fit: cover`、左右渐变遮罩），无需改动。

### 进度
- [x] 生成 `src/assets/login-bg.png` 并替换引用
- [x] 前端 build + 部署（随 M7-3 同源版本一并上线 `91c79844`）
- [x] 验证部署后 `/assets/login-bg-*.png` 返回 `200 image/png`

### 联调步骤
1. 浏览器打开 `https://xiaolou-nocode.pages.dev/user/login`，应看到淡蓝紫科技感背景大图 + 左侧登录卡片，而非拉伸的 logo。
2. 注册页 `/user/register` 同款背景。

## M7-5 生产环境默认模型切换（deepseek-v4-flash-ga-260731 → doubao-seed-evolving，2026-08-10）

### 背景
用户要求将生产环境的 `deepseek-v4-flash-ga-260731` 模型全部替换为 `doubao-seed-evolving`。

### 改动范围（仅 `application-prod.yml`）
生产环境三类语言模型（`chat-model` / `streaming-chat-model` / `reasoning-streaming-chat-model`）的 `model-name` 默认值由 `deepseek-v4-flash-ga-260731` 改为 `doubao-seed-evolving`：
- 三处均通过 `${CHAT_MODEL:doubao-seed-evolving}` 引用，可经环境变量 `CHAT_MODEL` 覆盖；
- 文件末尾 `CHAT_MODEL` 环境变量默认值同步改为 `doubao-seed-evolving`（部署平台环境变量的兜底默认值）；
- `routing-chat-model` 维持 `doubao-seed-2-0-lite-260428` 不变（路由分类用小模型，非本次替换对象）。

### 不受影响项（确认过不改动）
- `application-local.yml`：本地开发默认仍保留 `deepseek-v4-flash-ga-260731`（用户仅要求改生产环境）。
- 数据库：经核查 `sql/*.sql` 与代码，模型名不落库，全部来自配置文件/环境变量，无持久化模型配置需同步修改。
- 前端：`.env.production` 仅含 API 地址与部署域名，无模型名硬编码。

### 进度
- [x] `application-prod.yml` 三处 `model-name` 默认值改为 `doubao-seed-evolving`
- [x] `application-prod.yml` 末尾 `CHAT_MODEL` 默认值改为 `doubao-seed-evolving`
- [x] 确认无数据库/前端残留 `deepseek-v4-flash-ga-260731`（生产路径已清空）
- [x] 文档同步（design.md §5 模型配置章节 + 本节）

### 破坏性变更提示
- 生产环境默认语言模型切换为 `doubao-seed-evolving`。若部署平台环境变量 `CHAT_MODEL` 已显式设置旧值，需同步改为 `doubao-seed-evolving`（或直接删除该环境变量改用文件默认值）。
- 若 `doubao-seed-evolving` 在火山引擎方舟的 endpoint 未开通，会返回 `InvalidEndpointOrModel.NotFound`，首次请求即报错，上线前需确认 endpoint 可用。

### 验证步骤
1. 重新构建后端部署到 Railway，或直接改 Railway 环境变量 `CHAT_MODEL=doubao-seed-evolving`（若平台已设）。
2. 触发一次问答/代码生成，观察后端日志 `modelName=doubao-seed-evolving`（或在 ark 控制台确认调用模型）。
3. 验证生成质量符合预期（doubao-seed-evolving 为对话/代码通用模型，替代 deepseek-v4-flash 应无缝）。

