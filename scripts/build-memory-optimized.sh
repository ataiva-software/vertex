#!/bin/bash

# Memory-optimized build script for Eden
# This script builds the project in stages to avoid memory exhaustion

set -e

echo "🔧 Starting memory-optimized build process..."

# Clean any existing locks
echo "🧹 Cleaning Gradle locks..."
./scripts/clean-gradle-locks.sh

# Build shared libraries first (they're dependencies for everything else)
echo "📚 Building shared libraries..."
./gradlew :shared:core:build --no-daemon --max-workers=1 -x test
./gradlew :shared:auth:build --no-daemon --max-workers=1 -x test
./gradlew :shared:crypto:build --no-daemon --max-workers=1 -x test
./gradlew :shared:config:build --no-daemon --max-workers=1 -x test
./gradlew :shared:database:build --no-daemon --max-workers=1 -x test
./gradlew :shared:events:build --no-daemon --max-workers=1 -x test
./gradlew :shared:testing:build --no-daemon --max-workers=1 -x test

echo "🏗️ Building services..."
# Build services one by one to avoid memory issues
./gradlew :services:api-gateway:build --no-daemon --max-workers=1 -x test
./gradlew :services:vault:build --no-daemon --max-workers=1 -x test
./gradlew :services:hub:build --no-daemon --max-workers=1 -x test
./gradlew :services:flow:build --no-daemon --max-workers=1 -x test
./gradlew :services:task:build --no-daemon --max-workers=1 -x test
./gradlew :services:monitor:build --no-daemon --max-workers=1 -x test
./gradlew :services:sync:build --no-daemon --max-workers=1 -x test
./gradlew :services:insight:build --no-daemon --max-workers=1 -x test

echo "💻 Building clients..."
# Build clients (skip native targets that cause memory issues)
./gradlew :clients:cli:compileKotlinJvm --no-daemon --max-workers=1
./gradlew :clients:web:build --no-daemon --max-workers=1 -x test

echo "✅ Memory-optimized build completed successfully!"
echo ""
echo "📝 Next steps:"
echo "  - Run tests: ./scripts/test-memory-optimized.sh"
echo "  - Start services: docker-compose up -d"
echo "  - Try CLI: ./gradlew :clients:cli:run --args='--help'"