## Multi-stage build for optimal image size
#FROM maven:3.9-eclipse-temurin-17 AS build
#
#WORKDIR /app
#
## Copy pom.xml and download dependencies
#COPY pom.xml .
#RUN mvn dependency:go-offline -B
#
## Copy source code and build
#COPY src ./src
#RUN mvn clean package -DskipTests
#
## Runtime stage
#FROM eclipse-temurin:17-jre-alpine
#
#WORKDIR /app
#
## Create non-root user for security
#RUN addgroup -S spring && adduser -S spring -G spring
#USER spring:spring
#
## Copy the built JAR from build stage
#COPY --from=build --chown=spring:spring /app/target/immunization-validator-*.jar app.jar
#
## Expose port
#EXPOSE 8080
#
## Health check
#HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/validate/health || exit 1
#
## Run the application
#ENTRYPOINT ["java", "-jar", "app.jar"]
#
# ─── STAGE 1: BUILD ──────────────────────────────────────────────────────────
# Use Maven + Java 21 to compile and package the app into a JAR.
# This is a "build stage" — it won't end up in the final image, keeping it small.
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory inside the build container.
# All subsequent commands run from here.
WORKDIR /app

# Copy only pom.xml first (not the source code).
# Why? Docker caches each layer. If pom.xml hasn't changed, Maven won't
# re-download all dependencies on the next build — saves 2-3 minutes per run.
COPY pom.xml .

# Download all Maven dependencies into the container's local .m2 cache.
# -B = batch mode (no interactive prompts, cleaner CI logs)
# go-offline = pre-fetch everything so the actual build has no network calls
RUN mvn dependency:go-offline -B

# Now copy the source code. This layer changes on every commit, so it
# always runs — but at least the dependency layer above is cached.
COPY src ./src

# Build the JAR. -DskipTests because tests run separately in the CI pipeline
# (see backend.yml Step 3). Running them here would double the test time.
RUN mvn clean package -DskipTests

# ─── STAGE 2: RUNTIME ────────────────────────────────────────────────────────
# Use a minimal JRE-only image (not JDK) for the final container.
# eclipse-temurin:21-jre-alpine is ~90MB vs ~600MB for the full JDK build image.
# "alpine" = stripped-down Linux with only essential tools — smaller attack surface.
FROM eclipse-temurin:21-jre-alpine

# Working directory for the runtime container
WORKDIR /app

# Security: create a non-root user and group called "spring".
# Running as root inside a container is a security risk —
# if the app is compromised, the attacker gets root on the container.
# addgroup -S = system group, adduser -S = system user (no home dir, no shell)
RUN addgroup -S spring && adduser -S spring -G spring

# Switch all subsequent commands to run as this non-root user
USER spring:spring

# Copy ONLY the compiled JAR from Stage 1 (the build stage).
# The Maven source, pom.xml, and .m2 cache are NOT copied — they stay in Stage 1.
# --chown=spring:spring ensures the spring user owns the file (can read/execute it)
# The wildcard (*) handles the version in the filename: immunization-validator-1.0.0.jar
COPY --from=build --chown=spring:spring /app/target/immunization-validator-*.jar app.jar

# Document that the container listens on port 8080.
# This doesn't actually open the port — docker-compose and ECS task definitions
# do that. It's metadata that tells other tools what port to expect.
EXPOSE 8080

# Container-level health check. ECS and docker-compose both use this to decide
# if the container is healthy before routing traffic to it.
# --interval=30s  = check every 30 seconds
# --timeout=3s    = fail if no response within 3 seconds
# --start-period=40s = don't fail during the first 40s (Spring Boot startup time)
# --retries=3     = mark UNHEALTHY only after 3 consecutive failures
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider \
  http://localhost:8080/api/v1/validate/health || exit 1

# The command that runs when the container starts.
# Using ENTRYPOINT (not CMD) so the JAR always runs — it can't be overridden
# accidentally by passing arguments to docker run.
ENTRYPOINT ["java", "-jar", "app.jar"]
