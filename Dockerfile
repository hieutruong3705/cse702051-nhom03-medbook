# MedBook: ảnh chạy gồm backend Spring Boot và giao diện Vue đã build sẵn.
# Ba giai đoạn: (1) Node build giao diện, (2) Maven đóng gói, (3) JRE tối thiểu, chạy bằng người dùng thường.

# ---------- 1. Giao diện ----------
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# vite.config.js xuất ra ../src/main/resources/static, tức /build/src/main/resources/static
RUN npm run build

# ---------- 2. Backend ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
# Luôn dùng giao diện vừa build ở giai đoạn 1, không phụ thuộc bản static đang nằm trong repository
RUN rm -rf src/main/resources/static
COPY --from=frontend /build/src/main/resources/static ./src/main/resources/static
RUN mvn clean package -DskipTests -B

# ---------- 3. Chạy ----------
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache dumb-init \
    && addgroup -g 1001 -S spring \
    && adduser -S spring -u 1001 -G spring \
    && mkdir -p /app/uploads \
    && chown -R spring:spring /app
WORKDIR /app
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar
USER spring
EXPOSE 8080
VOLUME ["/app/uploads"]
# Điểm cuối công khai, không cần đăng nhập; 60 giây đầu dành cho Flyway và khởi động JVM
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/api/v1/system/status > /dev/null || exit 1
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-jar", "app.jar"]
