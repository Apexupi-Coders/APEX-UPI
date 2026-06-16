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
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
=======
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Install Maven (container build-time dependency)
RUN apk add --no-cache maven

# Copy pom and source
COPY pom.xml ./
COPY src ./src

# Build jar (use JDK21 toolchain to avoid Lombok/Javac incompatibilities on host JDKs)
RUN mvn -q -DskipTests package

# Identify built jar
RUN ls -la target

EXPOSE 8080

CMD ["sh", "-c", "java -jar target/*.jar"]



>>>>>>> c24d976 (Initial commit)
