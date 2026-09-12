# ============================================================
# STAGE 1: Build - Dùng Maven + JDK 21
# ============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml trước để tận dụng Docker layer cache
COPY pom.xml .

# Tải dependencies (cache layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build file .jar (bỏ test để build nhanh)
RUN mvn clean package -DskipTests

# ============================================================
# STAGE 2: Runtime - Chỉ giữ JRE, image nhỏ gọn
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# Cài dumb-init để xử lý signal đúng cách
RUN apk add --no-cache dumb-init

# Tạo user non-root để tăng bảo mật
RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001

WORKDIR /app

# Copy file .jar từ stage 1
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

# Chuyển sang user non-root
USER spring

# Cổng ứng dụng
EXPOSE 8080

# Entrypoint dùng dumb-init
ENTRYPOINT ["dumb-init", "--"]

# Lệnh chạy
CMD ["java", "-jar", "app.jar"]