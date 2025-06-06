# Multi-stage build for the Insight Service - Optimized for performance

# Stage 1: Build cache dependencies
FROM gradle:7.6.1-jdk11 AS deps

WORKDIR /app

# Copy only the files needed for dependency resolution
COPY build.gradle.kts settings.gradle.kts ./

# Download dependencies to cache them
RUN gradle dependencies --no-daemon

# Stage 2: Build the application
FROM deps AS build

# Copy source code
COPY src ./src

# Build the application with optimizations
RUN gradle build --no-daemon -x test \
    -Dorg.gradle.caching=true \
    -Dorg.gradle.parallel=true \
    -Dkotlin.compiler.execution.strategy=in-process

# Stage 3: Create a minimal runtime image
FROM eclipse-temurin:11-jre-alpine AS runtime

WORKDIR /app

# Set environment variables for performance
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication -XX:+OptimizeStringConcat -XX:+UseCompressedOops -XX:+AlwaysPreTouch"
ENV APP_ENV="prod"

# Create necessary directories
RUN mkdir -p /app/logs /app/config /app/reports

# Install required packages
RUN apk --no-cache add curl tzdata

# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar /app/insight-service.jar

# Copy configuration files
COPY --from=build /app/src/main/resources/application.conf /app/config/application.conf
COPY --from=build /app/src/main/resources/logback.xml /app/config/logback.xml

# Expose the application port
EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Set the entry point with optimized JVM flags
ENTRYPOINT ["java", "-server", "-jar", "/app/insight-service.jar"]