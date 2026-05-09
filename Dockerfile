# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Build app
COPY src src
RUN mvn -q -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Render provides PORT; default for local docker run
ENV PORT=8080
EXPOSE 8080

COPY --from=build /app/target/prime-api-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]