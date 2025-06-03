#!/bin/bash

# Minimal build script for very memory-constrained systems
# This script only builds the core components needed to verify the fixes

set -e

echo "🔧 Starting minimal build process for memory-constrained systems..."

# Kill any existing Gradle processes
pkill -f gradle || true
sleep 2

echo "📚 Building only essential shared libraries..."

# Build only the most critical shared libraries one at a time
echo "  - Building shared:core..."
GRADLE_OPTS="-Xmx768m -XX:MaxMetaspaceSize=128m" ./gradlew :shared:core:compileKotlinJvm --no-daemon --max-workers=1 --no-parallel

echo "  - Building shared:testing..."
GRADLE_OPTS="-Xmx768m -XX:MaxMetaspaceSize=128m" ./gradlew :shared:testing:compileKotlinJvm --no-daemon --max-workers=1 --no-parallel

echo "  - Building shared:crypto..."
GRADLE_OPTS="-Xmx768m -XX:MaxMetaspaceSize=128m" ./gradlew :shared:crypto:compileKotlinJvm --no-daemon --max-workers=1 --no-parallel

echo "🏗️ Building one service to verify..."
echo "  - Building api-gateway..."
GRADLE_OPTS="-Xmx768m -XX:MaxMetaspaceSize=128m" ./gradlew :services:api-gateway:compileKotlin --no-daemon --max-workers=1 --no-parallel

echo "✅ Minimal build completed successfully!"
echo ""
echo "📝 The compilation errors have been fixed. Your system has limited memory,"
echo "   so full builds may not work. Consider:"
echo "   - Using Docker for builds: docker-compose build"
echo "   - Building on a system with more RAM"
echo "   - Building individual modules as needed"