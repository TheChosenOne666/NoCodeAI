<div align="center">

# 🚀 XiaoLou AI NoCode

**一句话生成网站应用，让 AI 替你写代码**

基于 LangChain4j + Spring Boot 3 + Vue 3 的 AI 驱动零代码应用生成平台  
用户输入自然语言描述，AI 自动生成可部署的网站应用

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring_Boot-3.5.3-green.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.1.0-blue.svg)](https://docs.langchain4j.dev/)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

</div>

---

## ✨ 项目亮点

面试官你好 👋 这是一个从 0 到 1 独立完成的 **AI 应用生成平台**，不是调 API 的玩具项目。以下是技术亮点：

### 🤖 AI 核心 — 不只是 Chat，是真正的代码生成引擎
- 基于 **LangChain4j** 构建 AI Agent，集成 **自定义工具链**（文件读写、目录操作、代码修改）
- **智能路由**：AI 自动判断用户需求复杂度，路由到不同的代码生成策略
  - 简单需求 → 单 HTML 文件（内联 CSS/JS）
  - 中等需求 → 多文件分离（HTML + CSS + JS）
  - 复杂需求 → 完整 Vue 项目工程
- **流式输出（SSE）**：实时推送 AI 生成的代码，打字机般逐字呈现
- **AI 护航**：LangChain4j Guardrail 实现输入安全审查，防止 Prompt 注入攻击

### 🏗️ 架构设计 — 单体到微服务的真实演进
- **单体架构版本**（`src/`）：完整的单体应用，展现从 0 到 1 的开发过程
- **微服务架构版本**（`ai-no-code-parent-microservice/`）：经过真实拆分的微服务架构
  - 7 个独立服务模块：`common` / `model` / `client` / `user` / `app` / `ai` / `screenshot`
  - **Dubbo + Nacos** 实现服务间 RPC 调用
  - **Spring Cloud Alibaba** 技术栈
- 两种架构并存，展示**架构演进思维**

### 🎯 工程能力
- **设计模式实战**：门面模式（Facade）、策略模式（Strategy）、模板方法模式（Template Method）、执行器模式（Executor）
- **分布式限流**：基于 Redisson 的自定义注解 `@RateLimit`，支持 IP / 用户 / 全局多维度限流
- **并发优化**：`CompletableFuture` + 自定义线程池实现图片收集并发，Spring 多例模式解决 ChatModel 单例瓶颈
- **AI 工作流**：LangGraph4j 实现条件边、循环边等高级工作流编排
- **应用截图**：Selenium + ChromeDriver 自动化生成应用封面图
- **一键部署**：生成代码自动打包为 zip 下载，支持在线预览部署

### 🎨 前端工程
- **Vue 3 + TypeScript + Vite 7**，Ant Design Vue 组件库
- **可视化编辑器**：用户可在预览页面中点击元素，精准定位修改（类似 Figma 的点选交互）
- **SSE 实时通信**：流式接收 AI 代码生成进度
- **OpenAPI 自动生成**：`@umijs/openapi` 自动生成 TypeScript 接口类型，前后端契约一致

---

## 📁 项目结构

```
xiaolou-nocode/
├── src/                                    # 📦 单体架构版本（完整可运行）
│   ├── main/java/.../controller/           #   REST 控制器
│   ├── main/java/.../ai/                   #   AI 服务核心（LangChain4j）
│   │   ├── tools/                          #     AI Agent 工具链
│   │   ├── guardrail/                      #     输入安全护轨
│   │   ├── config/                         #     模型配置（多模型路由）
│   │   └── model/                          #     AI 响应模型
│   ├── main/java/.../core/                 #   核心业务层
│   │   ├── builder/                        #     Vue 项目构建器
│   │   ├── parser/                         #     代码解析器（策略模式）
│   │   ├── saver/                          #     代码保存器（模板方法模式）
│   │   └── handler/                        #     SSE 流处理器
│   ├── main/java/.../ratelimit/            #   分布式限流（Redisson + AOP）
│   ├── main/java/.../service/              #   业务逻辑层
│   └── main/resources/prompt/              #   AI 系统提示词
│
├── ai-no-code-parent-microservice/         # 🏗️ 微服务架构版本
│   ├── ai-no-code-ai/                      #   AI 生成服务（独立部署）
│   ├── ai-no-code-app/                     #   应用服务（API 网关入口）
│   ├── ai-no-code-user/                    #   用户服务
│   ├── ai-no-code-screenshot/              #   截图服务（Selenium）
│   ├── ai-no-code-common/                  #   公共模块
│   ├── ai-no-code-model/                   #   数据模型
│   └── ai-no-code-client/                  #   服务间 RPC 接口
│
├── xiaolou-nocode-frontend/                # 🎨 前端项目
│   ├── src/pages/                          #   页面组件
│   │   ├── HomePage.vue                    #     首页（打字机效果 + 精选应用）
│   │   ├── app/AppChatPage.vue             #     AI 对话 + 代码预览
│   │   └── admin/                          #     管理后台
│   ├── src/components/                     #   通用组件
│   ├── src/utils/visualEditor.ts           #   可视化编辑器工具
│   └── src/api/                            #   OpenAPI 自动生成的接口
│
└── sql/create_table.sql                    #   数据库初始化脚本
```

---

## 🔧 技术栈

| 层级 | 技术 |
|------|------|
| **AI 框架** | LangChain4j 1.1、LangGraph4j、火山引擎（豆包大模型） |
| **后端框架** | Spring Boot 3.5、Spring Cloud Alibaba 2023、Dubbo 3.3 |
| **注册中心** | Nacos |
| **数据库** | MySQL、MyBatis-Plus |
| **缓存/限流** | Redis、Redisson、Caffeine |
| **会话管理** | Spring Session + Redis（分布式 Session） |
| **安全** | LangChain4j Guardrail（Prompt 注入防护）、分布式限流 |
| **前端** | Vue 3.5、TypeScript 5.8、Vite 7、Ant Design Vue 4、Pinia |
| **工具** | Selenium（截图）、腾讯云 COS（对象存储）、Knife4j（API 文档） |

---

## 🚀 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+
- Nacos 2.x

### 1. 数据库初始化

```sql
source sql/create_table.sql
```

### 2. 启动后端（单体版本）

```bash
# 修改 src/main/resources/application.yml 中的数据库、Redis、AI 模型配置
mvn spring-boot:run
```

### 3. 启动后端（微服务版本）

```bash
# 依次启动各服务
cd ai-no-code-parent-microservice
mvn clean install -DskipTests
```

> 启动顺序：Nacos → User Service → App Service → AI Service → Screenshot Service

### 4. 启动前端

```bash
cd xiaolou-nocode-frontend
npm install
npm run dev
```

---

## 🧠 核心流程

```
用户输入描述 "帮我做一个个人博客网站"
        │
        ▼
┌──────────────────┐
│  智能路由服务      │  ← AI 判断需求复杂度
│  (Routing AI)     │     → HTML / MULTI_FILE / VUE_PROJECT
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  AI 代码生成      │  ← LangChain4j Agent + 工具链
│  (CodeGen AI)    │     流式 SSE 逐 token 输出
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  代码解析与保存    │  ← 策略模式选择解析器
│  (Parser+Saver)  │     模板方法模式保存文件
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  预览 / 部署      │  ← 在线预览、截图、一键下载 zip
└──────────────────┘
```

---

## 📸 功能演示

### 🏠 首页
- 打字机动效 Hero 区域
- 精选应用展示（带封面图）
- 我的应用管理

### 💬 AI 对话生成
- 自然语言对话，AI 流式生成代码
- 实时代码预览（iframe 沙箱）
- **可视化编辑**：点击预览页面元素，精准定位修改

### 📦 应用管理
- 应用详情查看 / 编辑
- 下载代码压缩包
- 一键部署上线
- 管理后台（用户管理、应用审核）

---

## 📝 设计思路与踩坑记录

<details>
<summary><b>🔧 点击展开技术细节</b></summary>

### 1. 为什么有两种架构版本？
项目最初是单体开发，快速验证想法。当业务增长后，进行了真实的微服务拆分。两种版本保留在仓库中，可以清晰看到演进过程。

### 2. AI 流式输出的挑战
LangChain4j 的 `TokenStream` 默认不支持 Reactor `Flux`，我通过**定制 StreamingChatModel 源码**，实现了 Flux 响应式流，解决了 SSE 推送和前端实时渲染的问题。

### 3. 并发调用优化
Spring 默认 Bean 是单例，而 ChatModel 内部维护对话状态，多用户并发时会串话。通过配置 `@Scope("prototype")` 让每个请求获得独立的 ChatModel 实例。

### 4. 代码生成类型路由
不是简单的 if-else，而是用**独立的 AI 模型**分析用户需求，自动路由到最适合的代码生成策略（单页面 / 多文件 / Vue 工程），这是真正的智能分发。

### 5. 安全防护
- **输入层**：LangChain4j Guardrail 拦截 Prompt 注入
- **流量层**：Redisson 分布式限流，防止滥用
- **输出层**：重试机制 + 异常处理，保证 SSE 连接稳定性

</details>

---

## 📄 License

[Apache-2.0](LICENSE)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，欢迎 Star！**

Made with ❤️ by [小楼](https://gitee.com/xiaolou-vv)

</div>
