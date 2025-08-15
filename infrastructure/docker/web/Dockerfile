# Multi-stage build for Eden Web UI
FROM openjdk:17-jdk-slim as builder

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy gradle files for dependency resolution
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./

# Make gradlew executable
RUN chmod +x ./gradlew

# Copy shared modules that web depends on
COPY shared/ shared/

# Copy web client code
COPY clients/ clients/

# Build the Kotlin/JS application
RUN ./gradlew :clients:web:jsBrowserProductionWebpack --no-daemon

# Production stage
FROM nginx:alpine

# Copy built assets from builder stage
COPY --from=builder /app/clients/web/build/distributions /usr/share/nginx/html

# Copy nginx configuration
COPY infrastructure/docker/web/nginx.conf /etc/nginx/conf.d/default.conf

# Create nginx user and set permissions
RUN addgroup -g 1001 -S nginx && \
    adduser -S -D -H -u 1001 -h /var/cache/nginx -s /sbin/nologin -G nginx -g nginx nginx && \
    chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    chown -R nginx:nginx /etc/nginx/conf.d

# Switch to non-root user
USER nginx

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3000/ || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]