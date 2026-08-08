# 任务拆解与进度

## M5 部署产物持久化（COS 对象存储）

### 背景
原部署方案将生成源码写入 `tmp/code_output/{deployKey}`、构建产物写入 `tmp/code_deploy/{deployKey}`，均为本地磁盘。
Railway 容器文件系统为临时盘（ephemeral），容器重启后两个目录均丢失，导致：
- 点"查看作品"预览空白
- 点"部署"报"应用代码不存在，请先生成代码"
- AI 生成的应用经不起重启，核心链路不可用

### 目标
将部署产物（构建后的 dist）持久化到腾讯云 COS 公有读，使 Railway 重启后"查看作品/部署"仍可访问，不依赖本地临时盘。
对齐业界 NoCode 平台做法：源码/产物存对象存储，前端直接引公有读直链。

### 需求描述
1. 部署时优先将构建产物上传 COS（`code-deploy/{deployKey}/`），返回 COS 公有读直链。
2. 未配置 COS 时回退本地盘，保证健壮性。
3. 删除应用时清理 COS 对应 `code-deploy/{deployKey}/` 与 `code-source/{codeGenType}_{appId}/`。
4. 生成完成后（已部署过的应用）自动同步最新 dist 到 COS。
5. 前端"查看作品"优先用 COS 公有读直链（`VITE_COS_DEPLOY_HOST`），未配置回退 `/api/static`。
6. 提供一次性迁移脚本，将本地已有 `tmp/code_deploy/*` 迁移上 COS。

### 进度
- [x] CosManager 扩展 `uploadDir` / `deleteDir` / `buildPublicUrl`
- [x] AppConstant 新增 `CODE_DEPLOY_COS_PREFIX` / `CODE_SOURCE_COS_PREFIX`，`CODE_DEPLOY_HOST` 支持环境变量注入
- [x] AppServiceImpl.deployApp 改为构建后上传 COS 并返回公有直链
- [x] AppServiceImpl.removeById 删除时清理 COS 目录
- [x] VueProjectGenStreamManager 构建后自动同步 dist 到 COS
- [x] 前端 env.ts `getDeployUrl` 改用 COS 公有直链（回退 /api/static）
- [x] 一次性迁移脚本 CosDeployMigrationRunner（`migrate.cos.deploy=true` 触发）
- [x] CosManager 单元测试（5 例全过）
- [x] 联调步骤文档化（见 design.md §部署持久化联调）
- [x] **生产环境闭环**（2026-08-09）：Railway 后端 cos.client.* 环境变量就位 + 本地 tmp/code_deploy 一次性迁移上 COS（27 deployKey / 144 文件）+ 前端 .env.production 配 `VITE_COS_DEPLOY_HOST` 并 Cloudflare Pages 重新部署；COS 直链 `code-deploy/jkJ12P/index.html` 验证 200。详见 design.md §8。
- [x] **COS 目录 404 修正**（2026-08-09）：前端 `getDeployUrl` COS 分支由 `.../code-deploy/{deployKey}/` 改为 `.../code-deploy/{deployKey}/index.html`，因为 COS 非静态网站服务、目录 URL 会 `NoSuchKey`（404 XML）。重新构建 + Cloudflare Pages 部署验证通过。
- [x] **强制下载问题修复——改用 cos-website 域名**（2026-08-09）：默认域名 `*.cos.ap-shanghai.myqcloud.com` 对 2024-01 后新桶强制返回 `Content-Disposition: attachment` 导致点击"查看作品"被下载白屏（无备案无法关闭）。方案 B：前端 `.env.production` 的 `VITE_COS_DEPLOY_HOST` 改为 `https://xiaolou-bi-1382226492.cos-website.ap-shanghai.myqcloud.com`，`getDeployUrl` COS 分支去掉硬拼的 `index.html`（由静态网站服务自动补）。前置：需在 COS 控制台开启「静态网站」（索引 `index.html`）。代码改动已完成，待用户开启静态网站 + 重新 Cloudflare 部署后生效。
- [x] **强制下载问题最终修复——Cloudflare Worker 反向代理剥头**（2026-08-09，方案 A）：方案 B 已实测**无效**（cos-website 域名虽能自动补 index.html 返回 200，但强制下载头依然存在 —— 腾讯云对所有域名统一下发该头，无备案关不掉）。最终方案：在 Cloudflare 创建 Worker `cos-view-proxy`（代码见 `cloudflare-worker/cos-view-proxy.js`），反向代理 COS 上游（仍走 cos-website 域名以自动补 index.html），复制响应体但删除 `content-disposition` 与 `x-cos-force-download`，浏览器直接展示。前端 `VITE_COS_DEPLOY_HOST` 指向 Worker `https://cos-view-proxy.1526655958.workers.dev`。Worker 已部署、前端已 Cloudflare Pages 部署，等用户在浏览器验证。

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
