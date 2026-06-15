FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q -U || mvn dependency:go-offline -q -U || mvn dependency:go-offline -q -U
COPY src ./src
RUN mvn package -DskipTests -q -U || mvn package -DskipTests -q -U || mvn package -DskipTests -q -U

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
