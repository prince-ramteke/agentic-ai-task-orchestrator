# syntax=docker/dockerfile:1

# --- Build stage: compile and package with JDK 21 + Maven ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first: copy only the POM, resolve, then copy sources.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage: minimal JRE, non-root ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as an unprivileged user (never root).
RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /workspace/target/agentic-ai-task-orchestrator-*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS=""
# Default to the containerised profile; override at runtime as needed.
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# NOTE: The full Docker Compose stack (PostgreSQL, Redis, Ollama, Prometheus, Grafana,
# frontend) is intentionally NOT defined yet — it arrives in Milestone 12. This Dockerfile
# only establishes a reproducible, production-shaped image for the backend.
