# Railway 部署：用 Dockerfile 显式装 JDK 21 + Maven，绕开 Nixpacks 自动探测不可控问题
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# 先单独下载依赖以利用 Docker 缓存
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline -DskipTests || true

# 复制源码并打包
COPY src ./src
RUN chmod +x mvnw && ./mvnw -B package -DskipTests

# 运行镜像（与构建期一致，使用 JDK 21）
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 拷贝构建产物
COPY --from=builder /app/target/xiaolou-nocode-backend-0.0.1-SNAPSHOT.jar app.jar

# Railway 会注入 PORT 环境变量；prod profile 会读取
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-Xmx512m -Xss512k"

EXPOSE 8080
ENTRYPOINT ["sh","-c","exec java $JAVA_TOOL_OPTIONS -jar app.jar --server.port=${PORT:-8080}"]