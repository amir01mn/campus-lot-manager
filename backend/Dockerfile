# syntax=docker/dockerfile:1

# ---- build: compile with the Maven wrapper and unpack the Spring Boot layers ----
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace

# Dependencies first, so this layer is reused until pom.xml (or the wrapper) changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Tests are skipped here because CI runs the full suite (Testcontainers needs Docker, which a build doesn't have).
COPY src src
RUN ./mvnw -B -q package -Dmaven.test.skip=true \
    && java -Djarmode=tools -jar target/campus-parking-*.jar extract --layers --launcher --destination target/extracted

# ---- runtime: slim JRE, non-root, layered for cache-friendly pulls ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app

# Least to most frequently changing.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

# Size the heap from the container's memory limit; exit (and let the orchestrator restart) on OOM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=6 \
    CMD wget -qO /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher \"$@\"", "--"]
