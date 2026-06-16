<<<<<<< HEAD
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
=======
# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven project first for dependency caching
COPY pom.xml ./
COPY src/ ./src/

RUN mvn -B -DskipTests package


# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="PSP Engineering <platform@psp.demo>"
LABEL description="NPCI Adapter — bridges PSP Switch (Kafka/Redis) to NPCI UPI network (HTTP/XML)"

# Create non-root user for security
RUN addgroup -S npci && adduser -S npci -G npci

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

RUN chown -R npci:npci /app
USER npci

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.output.ansi.enabled=ALWAYS"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

>>>>>>> c24d976 (Initial commit)
