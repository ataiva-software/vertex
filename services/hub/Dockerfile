# Multi-stage build for Eden Hub Service
FROM gradle:8.4-jdk17 AS builder

# Set working directory
WORKDIR /app

# Copy build files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties ./

# Copy source code
COPY src ./src

# Build the application
RUN gradle build --no-daemon -x test

# Runtime stage
FROM openjdk:17-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r eden && useradd -r -g eden eden

# Set working directory
WORKDIR /app

# Copy built application
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to app user
RUN chown -R eden:eden /app

# Switch to app user
USER eden

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Set JVM options for container
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]