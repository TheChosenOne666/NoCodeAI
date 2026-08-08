# 设计文档

## 部署产物持久化（COS）

### 1. 架构决策
- **源码/产物存对象存储**：腾讯云 COS（公有读），而非本地临时盘或 PostgreSQL BLOB。
- **前端直接引 COS 静态网站直链**：`{COS_DEPLOY_HOST}/code-deploy/{deployKey}/`，其中 `COS_DEPLOY_HOST` 必须是 **cos-website 域名**（`*.cos-website.ap-shanghai.myqcloud.com`），不经 Java 代理，支持全屏、可被他人访问。
- **为何用 cos-website 而非默认域名**：腾讯云 2024-01 后新建桶的默认域名（`*.cos.ap-shanghai.myqcloud.com`）强制返回 `Content-Disposition: attachment` + `x-cos-force-download: true`，浏览器会下载而非展示（且无备案域名无法关闭该策略）。cos-website 静态网站域名不带强制下载头，且会自动补 `index.html`，是免备案的展示方案。
- **回退策略**：COS 未配置时（`cosManager == null`，条件 bean 不创建）回退本地盘，保证本地开发与异常环境可运行。

### 2. COS 对象键约定
| 用途 | 前缀 | 示例 |
|---|---|---|
| 部署产物（dist） | `code-deploy/{deployKey}/` | `code-deploy/vue_123/index.html` |
| 生成源码 | `code-source/{codeGenType}_{appId}/` | `code-source/vue_123/src/App.vue` |

`deployKey = {codeGenType}_{appId}`，与前端 `getDeployUrl(deployKey)` 拼接一致。

### 3. 关键流程

#### 3.1 deployApp（部署）
```
1. 校验权限（仅 owner）
2. 取 deployKey = codeGenType + "_" + appId
3. 本地构建 Vue 项目（vue_project_{appId}）→ dist
4. 源目录存在性检查（本地 code_output 或 vue_project）
5. COS 可用：uploadDir("code-deploy/"+deployKey, dist)，返回 buildPublicUrl("code-deploy/"+deployKey+"/index.html")
   COS 不可用：复制到 CODE_DEPLOY_ROOT_DIR/{deployKey}，返回 CODE_DEPLOY_HOST/{deployKey}/
6. 更新 app.deployKey / deployedTime
7. 异步生成封面截图
```

#### 3.2 removeById（删除应用）
```
1. 查 app.deployKey / codeGenType
2. cosManager.deleteDir("code-deploy/"+deployKey)
3. cosManager.deleteDir("code-source/"+codeGenType+"_"+appId)
4. 删对话历史 → 删 app
```
异常仅记日志，不阻断删除。

#### 3.3 生成完成自动同步
`VueProjectGenStreamManager` 生成完成后，若 app 已部署过且存在 dist，自动 `uploadDir` 到 COS（或回退本地盘），保证"查看作品"展示最新内容。

### 4. 前端预览 URL
`env.ts`：
```
COS_DEPLOY_HOST = import.meta.env.VITE_COS_DEPLOY_HOST   // 必须是 cos-website 域名
getDeployUrl(deployKey) =
  COS_DEPLOY_HOST ? `${COS_DEPLOY_HOST}/code-deploy/${deployKey}/`   // 静态网站自动补 index.html
                  : `${API_BASE_URL}/static/${deployKey}/`
```
实时预览（生成中）仍走 `/api/static`（getStaticPreviewUrl），不变。

### 5. 配置项
| 配置 | 说明 |
|---|---|
| `cos.client.host` | COS 公有读域名，同时作为预览域名 |
| `cos.client.accessKey` / `secretKey` / `region` / `bucket` | COS 凭证 |
| `CODE_DEPLOY_HOST` | JVM 系统属性覆盖预览域名（已废弃硬编码 localhost） |
| `VITE_COS_DEPLOY_HOST` | 前端构建期注入的 COS 公有读域名 |
| `migrate.cos.deploy` | 一次性迁移开关（true 触发本地→COS 迁移） |

### 6. 一次性迁移
`CosDeployMigrationRunner`（`@ConditionalOnProperty(migrate.cos.deploy=true)`）：
遍历本地 `tmp/code_deploy/*` 目录，递归上传到 `code-deploy/{deployKey}/`。
执行后日志打印 `[迁移完成] 共处理 N 个 deployKey`，随后移除该配置重新部署。
注意：App 表中 deployKey 需与生产环境一致，前端才能拼出正确直链。

### 7. 破坏性变更提示
- 前端 `getDeployUrl` 行为变更：配置了 `VITE_COS_DEPLOY_HOST` 后，预览地址从 `/api/static/{deployKey}/` 变为 COS 直链。
- 删除应用会同步删 COS 资源（不可恢复），删除前需确认。

### 8. 生产环境部署要点（2026-08-09 实测）
- **前端 `.env.production` 配 `VITE_COS_DEPLOY_HOST` 指向 Cloudflare Worker 代理域名**（见下方方案 A），由 Worker 反向代理 COS 并剥离强制下载头。
  - 未配时 `getDeployUrl` 回退到 `API_BASE_URL/static/{deployKey}/`，对应 Railway 后端 `StaticResourceController`。
  - Railway 容器**无持久化目录**（`tmp/code_deploy` 不存在），回退路径**必定 404**。精品案例"查看作品"上线前一直就是这个原因。
- **强制下载根因**（2026-08-09 实测确认）：腾讯云对 2024-01 后新建桶的**所有域名**（含默认域名 `*.cos.ap-shanghai.myqcloud.com` 与静态网站域名 `*.cos-website.ap-shanghai.myqcloud.com`）统一返回 `Content-Disposition: attachment` + `x-cos-force-download: true`。无备案域名无法关闭该策略。实测：开启静态网站后 cos-website 域名虽能自动补 `index.html`（200/text/html），**但强制下载头依旧存在** → 方案 B（cos-website）**无效**。
- **最终方案 A：Cloudflare Worker 代理剥离下载头**（2026-08-09 采用）。Cloudflare Worker 作为反向代理：请求上游 COS（仍用 cos-website 域名以自动补 index.html），剥离 `Content-Disposition` / `x-cos-force-download`，浏览器直接展示。Worker 路由例如 `cos-view.xiaolou-nocode.workers.dev`，前端 `VITE_COS_DEPLOY_HOST` 指向它。详情见下方 §8.1。
- 后端 `cos.client.*` 配置已通过 Railway 环境变量注入（变量名 `COS_ACCESS_KEY` / `COS_SECRET_KEY` / `COS_REGION` / `COS_BUCKET` / `COS_HOST`，由 prod profile `${COS_*}` 占位符消费）。
- 一次性迁移 `CosDeployMigrationRunner` 已将本地 `tmp/code_deploy/*` 全部上传 COS（27 个 deployKey、144 文件），生产 COS 桶 `xiaolou-bi-1382226492` 公有读。
- Cloudflare Pages（前端）部署项目 `xiaolou-nocode`，生产域名 `https://xiaolou-nocode.pages.dev`。

#### 8.1 Cloudflare Worker 代理实现（方案 A）
- 路由：`https://cos-view.xiaolou-nocode.workers.dev/{path}` → 代理到 `https://xiaolou-bi-1382226492.cos-website.ap-shanghai.myqcloud.com/{path}`。
- Worker 逻辑：fetch 上游，新建 Response，复制 body 与 content-type 等头，**删除** `content-disposition` 与 `x-cos-force-download`。
- CORS：因前端（Cloudflare Pages）与 Worker 同主域或需跨域，Worker 需返回 `Access-Control-Allow-Origin: *`（仅供预览展示）。
- 前端：`.env.production` 设 `VITE_COS_DEPLOY_HOST=https://cos-view.xiaolou-nocode.workers.dev`，`getDeployUrl` 仍返回 `.../code-deploy/{deployKey}/`（静态网站自动补 index.html）。

### 9. 调试检查清单（精品案例查看作品排查）
1. 浏览器 DevTools → Network → 点击"查看作品"，看实际请求 URL 是 Worker 代理直链还是 Railway `/api/static/...`。
2. 若跳到 Railway `/api/static/...`：前端 `.env.production` 缺 `VITE_COS_DEPLOY_HOST` 或 Cloudflare Pages 未用最新 dist 重新部署。
3. 若 Worker 返回 5xx：Worker 上游地址错误（应为 cos-website 域名）或 Worker 未部署。
4. 若返回 404/403：COS 桶对象缺失（迁移未跑 / 上传失败）或 bucket 权限非公有读。
5. 若仍被下载：Worker 未正确删除 `content-disposition` / `x-cos-force-download` 头，检查 Worker 代码。

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
