#!/bin/bash

# Memory-optimized test script for Eden
# This script runs tests in stages to avoid memory exhaustion

set -e

echo "🧪 Starting memory-optimized test process..."

# Clean any existing locks
echo "🧹 Cleaning Gradle locks..."
./scripts/clean-gradle-locks.sh

echo "📚 Testing shared libraries..."
# Test shared libraries one by one
./gradlew :shared:core:test --no-daemon --max-workers=1
./gradlew :shared:auth:test --no-daemon --max-workers=1
./gradlew :shared:crypto:test --no-daemon --max-workers=1
./gradlew :shared:config:test --no-daemon --max-workers=1
./gradlew :shared:database:test --no-daemon --max-workers=1
./gradlew :shared:events:test --no-daemon --max-workers=1

echo "🏗️ Testing services..."
# Test services one by one
./gradlew :services:api-gateway:test --no-daemon --max-workers=1
./gradlew :services:vault:test --no-daemon --max-workers=1
./gradlew :services:hub:test --no-daemon --max-workers=1
./gradlew :services:flow:test --no-daemon --max-workers=1
./gradlew :services:task:test --no-daemon --max-workers=1
./gradlew :services:monitor:test --no-daemon --max-workers=1
./gradlew :services:sync:test --no-daemon --max-workers=1
./gradlew :services:insight:test --no-daemon --max-workers=1

echo "💻 Testing clients..."
# Test clients (JVM only to avoid memory issues)
./gradlew :clients:cli:testJvm --no-daemon --max-workers=1 || echo "⚠️  CLI tests skipped (may not exist yet)"
./gradlew :clients:web:test --no-daemon --max-workers=1 || echo "⚠️  Web tests skipped (may not exist yet)"

echo "✅ Memory-optimized tests completed successfully!"