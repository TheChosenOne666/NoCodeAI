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
