# Multi-stage build for the Insight Service

# Stage 1: Build the application
FROM gradle:7.6.1-jdk11 AS build

WORKDIR /app

# Copy build configuration files
COPY build.gradle.kts settings.gradle.kts ./

# Copy source code
COPY src ./src

# Build the application
RUN gradle build --no-daemon -x test

# Stage 2: Create the runtime image
FROM openjdk:11-jre-slim

WORKDIR /app

# Set environment variables
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV APP_ENV="prod"

# Create directory for logs
RUN mkdir -p /app/logs

# Copy the built JAR file from the build stage
COPY --from=build /app/build/libs/*.jar /app/insight-service.jar

# Copy configuration files
COPY --from=build /app/src/main/resources/application.conf /app/config/application.conf
COPY --from=build /app/src/main/resources/logback.xml /app/config/logback.xml

# Expose the application port
EXPOSE 8080

# Set the entry point
ENTRYPOINT ["java", "-jar", "/app/insight-service.jar"]