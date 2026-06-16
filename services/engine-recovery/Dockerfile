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
LABEL description="Engine Recovery — handles recovery/compensation logic"

RUN addgroup -S recovery && adduser -S recovery -G recovery

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN chown -R recovery:recovery /app
USER recovery

EXPOSE 8083

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.output.ansi.enabled=ALWAYS"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

