# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./
COPY src/ ./src/

RUN mvn -B -DskipTests package

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="PSP Engineering <platform@psp.demo>"
LABEL description="Transaction Orchestrator (cbs) — orchestrates TPS flows using Kafka/Redis/HTTP"

RUN addgroup -S orchestrator && adduser -S orchestrator -G orchestrator

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN chown -R orchestrator:orchestrator /app
USER orchestrator

EXPOSE 8082

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.output.ansi.enabled=ALWAYS"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

