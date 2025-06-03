# Multi-stage build for Eden services
FROM openjdk:17-jdk-slim as builder

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./

# Copy shared libraries
COPY shared/ shared/

# Copy services
COPY services/ services/

# Build the specific service with memory optimization
ARG SERVICE_NAME
ENV GRADLE_OPTS="-Xmx2048m -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:+UseStringDeduplication"
RUN echo "Building service: ${SERVICE_NAME} with memory optimization" && \
    ./gradlew :services:${SERVICE_NAME}:build --no-daemon -x test \
    --max-workers=1 \
    --no-parallel \
    --build-cache \
    --gradle-user-home=/tmp/.gradle \
    --org.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=512m"

# Runtime stage
FROM eclipse-temurin:17-jre

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN addgroup --system eden && adduser --system --group eden

# Set working directory
WORKDIR /app

# Copy built JAR from builder stage
ARG SERVICE_NAME
COPY --from=builder /app/services/${SERVICE_NAME}/build/libs/*.jar app.jar

# Change ownership to eden user
RUN chown -R eden:eden /app

# Switch to non-root user
USER eden

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]