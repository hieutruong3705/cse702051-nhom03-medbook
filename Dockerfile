
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app


COPY pom.xml .


RUN mvn dependency:go-offline -B


COPY src ./src


RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache dumb-init


RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001

WORKDIR /app


COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

USER spring


EXPOSE 8080


ENTRYPOINT ["dumb-init", "--"]


CMD ["java", "-jar", "app.jar"]