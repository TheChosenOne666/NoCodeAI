# 设计文档

## 部署产物持久化（COS）

### 1. 架构决策
- **源码/产物存对象存储**：腾讯云 COS（公有读），而非本地临时盘或 PostgreSQL BLOB。
- **前端直接引 COS 直链**：`{COS_DEPLOY_HOST}/code-deploy/{deployKey}/index.html`，不经 Java 代理，支持全屏、可被他人访问。
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
COS_DEPLOY_HOST = import.meta.env.VITE_COS_DEPLOY_HOST
getDeployUrl(deployKey) =
  COS_DEPLOY_HOST ? `${COS_DEPLOY_HOST}/code-deploy/${deployKey}/`
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
- **前端 `.env.production` 必须配 `VITE_COS_DEPLOY_HOST=https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com`**。
  - 未配时 `getDeployUrl` 回退到 `API_BASE_URL/static/{deployKey}/`，对应 Railway 后端 `StaticResourceController`。
  - Railway 容器**无持久化目录**（`tmp/code_deploy` 不存在），回退路径**必定 404**。精品案例"查看作品"上线前一直就是这个原因。
- 后端 `cos.client.*` 配置已通过 Railway 环境变量注入（变量名 `COS_ACCESS_KEY` / `COS_SECRET_KEY` / `COS_REGION` / `COS_BUCKET` / `COS_HOST`，由 prod profile `${COS_*}` 占位符消费）。
- 一次性迁移 `CosDeployMigrationRunner` 已将本地 `tmp/code_deploy/*` 全部上传 COS（27 个 deployKey、144 文件），生产 COS 桶 `xiaolou-bi-1382226492` 公有读。
- 联调验证（2026-08-09）：`https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/code-deploy/jkJ12P/index.html` 返回 `200 / text/html / 449B`。
- Cloudflare Pages（前端）部署项目 `xiaolou-nocode`，生产域名 `https://xiaolou-nocode.pages.dev`。
- **COS 直链必须精确到 `index.html`**（2026-08-09 修正）：COS 是对象存储、不是静态网站服务，访问 `.../code-deploy/{deployKey}/`（目录）会返回 `NoSuchKey`（404 XML）。前端 `getDeployUrl` 的 COS 分支已改为拼 `.../code-deploy/{deployKey}/index.html`（验证 200）；后端 `StaticResourceController` 回退分支由 Spring 欢迎页自动补 `index.html`，无需改动。

### 9. 调试检查清单（精品案例 404 排查）
1. 浏览器 DevTools → Network → 点击"查看作品"，看实际请求 URL 是 COS 直链还是 Railway `/api/static/...`。
2. 若跳到 Railway `/api/static/...`：前端 `.env.production` 缺 `VITE_COS_DEPLOY_HOST` 或 Cloudflare Pages 未用最新 dist 重新部署。
3. 若跳到 COS 但返回 404/403：COS 桶对象缺失（迁移未跑 / 上传失败）或 bucket 权限非公有读。

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
