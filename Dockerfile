# Multi-stage build for Eden single binary
FROM golang:1.21-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git ca-certificates tzdata

# Set working directory
WORKDIR /app

# Copy go mod files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY . .

# Build the single binary
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o eden ./cmd/eden/

# Final stage - minimal runtime image
FROM alpine:latest

# Install runtime dependencies
RUN apk --no-cache add ca-certificates tzdata

# Create non-root user
RUN addgroup -g 1001 eden && \
    adduser -D -s /bin/sh -u 1001 -G eden eden

# Set working directory
WORKDIR /app

# Copy binary from builder stage
COPY --from=builder /app/eden .

# Change ownership to eden user
RUN chown eden:eden /app/eden

# Switch to non-root user
USER eden

# Expose default ports (8000-8086)
EXPOSE 8000 8080 8081 8082 8083 8084 8085 8086

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD ./eden status || exit 1

# Default command - run all services
CMD ["./eden", "server"]
