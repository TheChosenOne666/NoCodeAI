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
