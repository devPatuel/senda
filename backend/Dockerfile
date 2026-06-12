# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies: only re-download when pom.xml changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root user
RUN groupadd --system senda && useradd --system --gid senda senda
USER senda

COPY --from=build /app/target/backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
