# Railway 部署：用 Dockerfile 显式装 JDK 21 + Maven，绕开 Nixpacks 自动探测不可控问题
# cache-bust: 2026-08-08-4 强制 Railway 完全重建并清除所有旧 startCommand 与 .class 缓存
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 先单独下载依赖以利用 Docker 缓存
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline -DskipTests || true

# 强制清理 target 目录，避免 Docker layer 缓存的旧 .class 干扰新打包
RUN rm -rf /app/target

# 复制源码并打包
COPY src ./src
RUN chmod +x mvnw && ./mvnw -B package -DskipTests

# 运行镜像：基于 Ubuntu 安装 JRE21 + Google Chrome（截图功能需要 Chrome 本体）
FROM ubuntu:22.04
WORKDIR /app

# 安装 JRE、Chrome 及依赖；清理 apt 缓存减小镜像体积
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        wget \
        gnupg \
        ca-certificates \
        libnss3 \
        libatk-bridge2.0-0 \
        libatk1.0-0 \
        libcups2 \
        libdrm2 \
        libxkbcommon0 \
        libxcomposite1 \
        libxdamage1 \
        libxfixes3 \
        libxrandr2 \
        libgbm1 \
        libasound2 \
        libpango-1.0-0 \
        libcairo2 \
        libatspi2.0-0 \
    && wget -qO- https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-chrome-stable \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 拷贝构建产物
COPY --from=builder /app/target/xiaolou-nocode-backend-0.0.1-SNAPSHOT.jar app.jar

# Railway 会注入 PORT 环境变量；prod profile 会读取
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-Xmx512m -Xss512k"
# 截图工具通过此变量定位 Chrome 本体
ENV CHROME_BIN=/usr/bin/google-chrome-stable

EXPOSE 8080
ENTRYPOINT ["sh","-c","exec java $JAVA_TOOL_OPTIONS -jar app.jar --server.port=${PORT:-8080}"]