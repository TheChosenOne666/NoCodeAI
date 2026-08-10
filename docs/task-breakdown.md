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

## M7-6 AI 多轮续写生成超时修复（完整 HTML 回传导致上下文膨胀，2026-08-10）

### 背景
用户在对话中让 AI「去掉左边说明面板，让游戏占满屏幕」「继续」等续写指令时，前端长时间转圈后报「AI生成错误」。
排查日志发现：`/api/static/preview_{appId}/index.html` 查询 `app_deploy_asset` 返回 `Total: 0`（未生成成功），且发给 AI 的请求体里 assistant 历史消息携带了**上一轮完整游戏 HTML**（含数千行 JS，十几 KB）。
根因：每次「继续/修改」都把上一轮完整 HTML 原样回传给大模型，历史上下文雪球式膨胀，单轮生成远超流式模型 120s 超时（`StreamingChatModelConfig` 第 40 行），流被中断→前端 `EventSource.onerror`→报「AI生成错误」；且超时中断使 `doOnComplete` 的 `savePreviewAssets` 不触发，预览库无记录。

### 改动范围（仅 `ChatHistoryServiceImpl.loadChatHistoryToMemory`）
- 加载历史到 `MessageWindowChatMemory` 时，对 **AI 历史消息做摘要压缩**（`summarizeAiMessage`）：保留前 800 字符摘要 + 追加指令「请基于用户新需求重新输出【完整】HTML 代码，不要续写或依赖被截断内容」；
- **用户历史消息原样保留**（用户指令很短，且必须完整才能让模型知道要改什么）；
- 摘要方法 `summarizeAiMessage` / `codeGenTypeOf` 改为包级可见便于单测；
- 不改变 `chat_history` 表的存储内容（仍存完整 HTML，便于审计/回放），仅在「回传模型」环节裁剪。

### 不受影响项（确认过不改动）
- 流式超时 `StreamingChatModelConfig` 的 120s 本次未调整（先做根因治理；若续写仍偶发超时，再配合方案 2 提到 300s 作缓冲）；
- 不改动前端、不改动 `app_deploy_asset` 持久化逻辑；
- `MessageWindowChatMemory` 每轮 `createAiCodeGeneratorService` 会 `clear()` 并以 DB 重建，故 Redis memory 里上轮完整 AI 消息下一轮被丢弃，不影响。

### 进度
- [x] `ChatHistoryServiceImpl` AI 历史消息摘要压缩（超长截断+重生成指令，用户消息原样）
- [x] 单元测试 `ChatHistoryServiceImplTest`（4 例全过：超长截断、短消息保留、空值、HTML 类型识别）
- [x] `mvn clean test` 通过；`mvn compile` 全项目编译通过
- [x] 文档同步（本节 + design.md §8 补充）

### 验证步骤
1. 后端重新部署后，进入任一应用对话，发「继续生成/去掉说明面板」等多轮续写指令；
2. 应能在 120s 内收到 `done` 并右侧预览更新，不再报「AI生成错误」；
3. Railway 日志确认 `app_deploy_asset` 查询返回 `Total: 1`（生成成功并落库）。

2. 触发一次问答/代码生成，观察后端日志 `modelName=doubao-seed-evolving`（或在 ark 控制台确认调用模型）。
3. 验证生成质量符合预期（doubao-seed-evolving 为对话/代码通用模型，替代 deepseek-v4-flash 应无缝）。

## M7-7 生成截断兜底 / 完成话术 / 预览必现（2026-08-10）

### 背景
用户反馈：AI 生成到一半（如 `function drawCloud...`）就停了，预览空白；且要求「每次生成完 AI 都要说完成话术」「每次生成完都要能预览」。
根因组合：
1. **模型输出被截断**：部分模型存在 max output token 上限，长 HTML 在标记闭合前被截断 → 原 `HtmlCodeParser` 只匹配 ```` ```html ... ``` ```` 闭合块，截断时无匹配 → 回退「整段当 HTML」但常含残缺 JS，运行即白屏。
2. **保存失败被静默吞**：`processCodeStream` 解析/保存异常仅在日志打印，`EventSource` 收到的是正常 `done` → 前端以为成功但 `app_deploy_asset` 无记录 → 预览空白、且用户无感知。
3. **缺完成话术**：提示词未约束「生成结束后用自然语言说明已完成」，用户无法区分「还在生成」与「已完成」。
4. **流式超时 120s 偏紧**：M7-6 压缩历史后仍偶发超时中断。

### 改动范围
- **后端 `HtmlCodeParser`**：新增未闭合兜底 `HTML_CODE_PATTERN_UNCLOSED`，`extractHtmlCode` 先匹配闭合块，失败则提取 ```` ```html ```` 之后到末尾内容，尽量挽救可运行代码。
- **后端 SSE 链路改造（`AiCodeGeneratorFacade` / `SimpleTextStreamHandler` / `StreamHandlerExecutor` / `AppService` / `AppServiceImpl` / `AppController`）**：
  - `generateAndSaveCodeStream` 返回 `Flux<ServerSentEvent<String>>`；`processCodeStream` 把每个 chunk 包成 `data: {"data": chunk}` 透传；流完成后 `Mono.fromRunnable` 执行解析保存，异常写入 `saveError` 再 `Mono.defer` 返回 `event: business-error`（带 `message`）或 `event: done`。
  - `SimpleTextStreamHandler.handle` 仅收集默认 message 事件（JSON `data` 字段）写入 AI 对话历史。
  - `StreamHandlerExecutor` VUE_PROJECT 分支过滤默认事件 → `JsonMessageStreamHandler` → 包回 SSE → 追加 `done`；HTML/MULTI_FILE 走 `SimpleTextStreamHandler`。
  - `AppController.chatToGenCode` 直接 `return appService.chatToGenCode(...)`（SSE 已含 data/done/business-error，不再二次拼接）。
- **后端 `StreamingChatModelConfig`**：`.timeout(Duration.ofSeconds(300))`（120s → 300s 缓冲）。
- **后端提示词 `code-gen-html-system-prompt.txt`**：
  - 第 9 条约束「仅输出 1 个 HTML 代码块」；
  - 新增第 10 条「生成结束后必须输出一段自然语言完成话术（如『已完成 XX 页面，可在右侧预览』）」；
  - 新增第 11 条「代码量控制：MVP 优先，避免超长导致截断」；
  - 新增第 12 条「若被截断，保证 `</html>` 与 ```` ``` ```` 完整闭合」。
- **前端 `AppChatPage.vue`**：
  - `done` 事件兜底：若 `fullContent` 不含 `</html>` 或 ```` ``` ````，把 AI 消息内容改为「生成内容不完整，请点击重新生成」并 `message.warning`。
  - `updatePreview`：HTML/VUE 统一先做 HEAD 请求确认预览文件存在，不存在则 `message.warning('预览文件尚未生成，请稍后重试')`。

### 进度
- [x] `HtmlCodeParser` 未闭合兜底
- [x] 后端 SSE 链路改造 + `business-error` 透传（保存失败不再静默）
- [x] 提示词约束（完成话术 + 代码量 + 截断兜底）
- [x] 流式超时 120s → 300s
- [x] 前端 `done` 兜底文案 + 预览 HEAD 校验
- [x] `mvn compile` 全编译通过（含修复 `AppService.java` 重复 import）
- [x] `ChatHistoryServiceImplTest` 4 例全过
- [ ] 前后端联调（步骤见下）

### 验证步骤
1. 后端重新部署（Railway）后，进入任一应用对话，发送较长需求（如「做一个带粒子动画的登录页」）；
2. 生成期间右侧应实时显示代码流；结束后 AI 消息末尾应出现完成话术；
3. 右侧预览应成功渲染（HEAD 校验通过），不再空白；
4. 若故意构造解析失败，前端应弹出 `business-error` 提示而非静默空白；
5. 多轮「继续生成」稳定不报「AI生成错误」，且每次都落库可预览。

## M7-8 预览资源 upsert 修复重复生成唯一键冲突（2026-08-10）

### 背景
用户连续对同一应用重新生成（如「生成一个简单的可玩的跳一跳小游戏」多次）时，保存阶段抛出：
```
java.sql.SQLIntegrityConstraintViolationException: Duplicate entry 'preview_2086705705764442113-index.html'
for key 'app_deploy_asset.uk_deploy_path'
```
表 `app_deploy_asset` 唯一键为 `uk_deploy_path(deploy_key, file_path)`。`savePreviewAssets` / `saveDeployAssets` 原逻辑是「先按 `deploy_key` 删除全部旧记录，再批量 `insert`」。在流式生成完成的 `Mono.fromRunnable` 异步上下文或重复触发生成时，删除与插入之间存在竞态/时序问题，导致同 `(deploy_key, file_path)` 已存在记录，新 `insert` 直接撞唯一键。

### 改动范围
- **`AppDeployAssetMapper`**：新增 `upsertBatch(@Param("list") List<AppDeployAsset>)` 注解方法，使用 `INSERT ... ON DUPLICATE KEY UPDATE`（按 `uk_deploy_path` 命中则更新 `content_type/file_size/content/is_delete/update_time`，否则插入）。import 补充 `java.util.List`、`org.apache.ibatis.annotations.Insert`。
- **`AppServiceImpl`**：`savePreviewAssets` 与 `saveDeployAssets` 均移除「先 `delete` 再循环 `insert`」，改为调用 `appDeployAssetMapper.upsertBatch(assets)`。彻底消除竞态导致的 Duplicate entry，且天然支持重复生成同应用的覆盖更新。

### 进度
- [x] `AppDeployAssetMapper.upsertBatch`（@Insert + ON DUPLICATE KEY UPDATE）
- [x] `AppServiceImpl` 两处保存逻辑改用 upsert（移除先删后插）
- [x] `mvn clean test-compile` 全编译通过
- [ ] 前后端联调（步骤见下）

### 验证步骤
1. Railway 重新部署后，对同一应用连续两次生成不同需求（如先「跳一跳小游戏」、再「贪吃蛇小游戏」）；
2. 第二次生成应正常落库并在右侧预览更新，不再报 `Duplicate entry`；
3. 查询 `app_deploy_asset` 中 `deploy_key = preview_{appId}` 的记录数应等于本次生成文件数（旧文件被覆盖而非叠加，无重复 `file_path`）。

## M7-10 部署失败：应用代码不存在（2026-08-10）

### 背景
用户点击「部署」后，后端返回「应用代码不存在，请先生成代码」。但右侧预览已正常显示，说明生成产物已作为 `preview_{appId}` 持久化到数据库，只是 `tmp/code_output/{codeGenType}_{appId}` 本地源目录已被清理（Railway 等无状态容器磁盘丢失）。

### 根因
`AppServiceImpl.deployApp` 第 6 步直接检查本地源目录 `CODE_OUTPUT_ROOT_DIR/{codeGenType}_{appId}`，不存在即抛异常；未利用数据库中已存在的预览资源。

### 改动范围
- **`AppDeployAssetMapper`**：新增 `selectListByDeployKey(@Param("deployKey"))` 注解方法，按 deploy_key 查询全部未删除资源。
- **`AppServiceImpl`**：
  - `deployApp` 中本地源目录不存在时，改为调用 `copyPreviewAssetsToDeploy(appId, deployKey)` 从 `preview_{appId}` 复制全部资源到新 deployKey。
  - 新增私有方法 `copyPreviewAssetsToDeploy`：读取 preview 资源列表，改写 deployKey 后批量 `upsertBatch`。
  - 仅当 preview 也不存在时才返回「应用代码不存在，请先生成代码」。

### 进度
- [x] `AppDeployAssetMapper.selectListByDeployKey`
- [x] `AppServiceImpl.copyPreviewAssetsToDeploy` 与 `deployApp` 分支改造
- [x] `mvn test` 全编译通过
- [ ] 前后端联调（步骤见下）

### 验证步骤
1. 确保 `app_deploy_asset` 中存在 `deploy_key = preview_{appId}` 记录（生成一次即可）。
2. 删除或重命名本地 `tmp/code_output/{codeGenType}_{appId}` 目录，模拟无状态容器。
3. 前端点「部署」，应返回 `/api/static/{deployKey}/` URL 且 200。
4. 查库确认 `app_deploy_asset` 中新增 `deploy_key = {deployKey}` 的记录，内容与 preview 一致。

## M7-9 Spring MVC 下 SSE 无实时打字机效果 / Cloudflare 524 超时（2026-08-10）

### 背景
用户反馈：生成很慢，一直转圈，没有像打字机一样的流式输出；网络请求返回 Cloudflare `524` 超时（约 2.1 分钟后断开），EventSource 响应大小始终为 0 B。

根因：项目依赖的是 `spring-boot-starter-web`（Spring MVC），不是 WebFlux。在 Spring MVC 中直接返回 `Flux<ServerSentEvent<T>>` 不会被真正流式输出——Spring 会等待整个 `Flux` 完成后再序列化成一个响应体。当生成耗时较长时，Cloudflare 边缘到 Railway 源站长时间收不到数据，触发 524 超时；前端也看不到逐字输出。

### 改动范围
- **`AppController`**：
  - 新增 import：`SseEmitter`、`Disposable`、`StandardCharsets`、`TimeUnit`。
  - `/chat/gen/code` 与 `/gen/stream/{appId}` 返回类型从 `Flux<ServerSentEvent<...>>` 改为 `SseEmitter`。
  - 新增私有方法 `subscribeToSseEmitter(Flux<ServerSentEvent<T>>, long, TimeUnit)`：
    - 创建 `SseEmitter` 并设置 300s 超时。
    - 订阅 Flux，把每个 `ServerSentEvent` 的 `event/data/id/comment` 实时推送到 emitter。
    - data 使用 `MediaType.TEXT_PLAIN` 按字符串原样发送（避免额外 JSON 转义）。
    - Flux 出错时发送 `business-error` 事件并 `completeWithError`。
    - emitter 完成/超时/错误时取消 Flux 订阅，避免资源泄漏。
  - 两处 endpoint 获取 `Flux` 后调用 `subscribeToSseEmitter` 返回 emitter。
- **Cloudflare Functions 代理 `functions/api/[[path]].js`**：检测到响应 `Content-Type` 包含 `text/event-stream` 时，补充 `cache-control: no-cache, no-transform` 和 `x-accel-buffering: no`，进一步防止边缘/浏览器缓冲 SSE 流。

### 进度
- [x] `AppController` `/chat/gen/code` 改为 `SseEmitter`
- [x] `AppController` `/gen/stream/{appId}` 改为 `SseEmitter`
- [x] 新增 `subscribeToSseEmitter` 桥接工具方法
- [x] Cloudflare Functions 代理补充 SSE 防缓冲头
- [x] `mvn clean test-compile` 全编译通过
- [ ] 前后端联调（步骤见下）

### 验证步骤
1. 后端重新部署后，在应用对话中发送「生成一个简单的跳一跳小游戏」；
2. 应能在 1~2 秒内看到 AI 消息像打字机一样逐字/逐段出现，而不是长时间空白后一次性出现；
3. 浏览器 Network 中 `/api/app/chat/gen/code` 请求的 `Type` 为 `eventsource`，`Size` 应随时间递增，不再保持 0 B；
4. 生成完成后应收到 `done` 事件，右侧预览正常渲染，不再出现 Cloudflare 524；
5. 对 Vue 项目类型，右侧代码实时展示 `/api/app/gen/stream/{appId}` 也应逐文件/逐 chunk 更新。

## M7-10 生产日志 rate limit / 生成中途截断（2026-08-10）

### 背景
用户反馈：生成到一半断开（如跳一跳小游戏的 `update()` 函数中间停止）；Railway 日志出现 `rate limit of 500 logs/sec reached ... Messages dropped: 14`。

根因组合：
1. **生产日志量过大**：`application-prod.yml` 中 `langchain4j.open-ai.*.log-requests/responses: true`，流式场景下每个 chunk 都可能打印日志，叠加 MyBatis SQL debug 日志，瞬间超过 Railway 500 logs/sec 限制，关键日志被丢弃。
2. **模型输出 token 上限不明**：`StreamingChatModelConfig` 未配置 `maxTokens`，使用模型默认值；复杂 HTML/JS 游戏容易在逻辑中间被截断。
3. **SseEmitter 发送异常处理粗糙**：客户端主动断开（刷新/关闭页面）时 `emitter.send` 抛异常被 `completeWithError`，可能误报错误；且没有 `completed` 标志，存在重复完成风险。
4. **前端不完整检测太宽松**：只检查 `</html>` 是否存在，模型截断但保留闭合标签时无法提示用户「继续生成」。

### 改动范围
- **`application-prod.yml`**：
  - 新增 `logging.level`：root 为 WARN，`com.xiaolou.xiaolouainocodebackend` 为 INFO，MyBatis / Spring JDBC 为 WARN。
  - `langchain4j.open-ai.*.log-requests/responses` 全部改为 `false`。
  - `streaming-chat-model` 增加 `max-tokens: 32768`。
- **`StreamingChatModelConfig`**：新增 `private Integer maxTokens` 并在 builder 中 `.maxTokens(maxTokens)`。
- **`AppController.subscribeToSseEmitter`**：
  - 新增 `completed` 原子标志 + `cleanup` 方法，避免重复完成/取消订阅。
  - `emitter.send` 异常区分 `IOException`（客户端断开，静默清理）与其他异常（`completeWithError`）。
  - `onError/onTimeout` 日志级别改为 warn/debug，减少噪音。
- **前端 `AppChatPage.vue` done 事件兜底**：
  - 从「是否包含 `</html>` 或 ```` ``` ````」改为「是否以 `</html>` 结尾 + Markdown 代码块是否闭合」。
  - 不完整时 AI 消息内容提示「请发送「继续生成」让 AI 补全剩余部分」，并 `message.warning`。

### 进度
- [x] 生产环境关闭 langchain4j request/response 日志
- [x] 生产环境调高 MyBatis/Spring JDBC 日志级别
- [x] `StreamingChatModelConfig` 支持 `maxTokens` 并配置 32768
- [x] `AppController` SseEmitter 异常处理优化
- [x] 前端 done 事件不完整检测增强
- [x] `mvn clean test-compile` 全编译通过
- [ ] 前后端联调（步骤见下）

### 验证步骤
1. Railway 重新部署后，再次生成「跳一跳小游戏」；
2. Railway 日志不应再出现 `rate limit of 500 logs/sec`；
3. 生成应能输出完整 HTML/JS（或即使截断也会明确提示「生成内容不完整，请发送继续生成补全」）；
4. 若仍截断，发送「继续生成」应能补全剩余代码并正常预览；
5. 刷新页面或关闭标签不会在后端留下 error 级日志。

