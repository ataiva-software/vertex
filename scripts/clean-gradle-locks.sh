#!/bin/bash

# Clean Gradle locks script
# This script cleans up stale Gradle daemon locks that can cause build conflicts

echo "🧹 Cleaning Gradle locks and caches..."

# Stop any running Gradle daemons
./gradlew --stop

# Clean up lock files
find ~/.gradle -name "*.lock" -type f -delete 2>/dev/null || true
find ~/.gradle -name "*.lck" -type f -delete 2>/dev/null || true

# Clean up daemon directories with stale processes
for daemon_dir in ~/.gradle/daemon/*/; do
    if [ -d "$daemon_dir" ]; then
        daemon_pid_file="$daemon_dir/daemon.pid"
        if [ -f "$daemon_pid_file" ]; then
            daemon_pid=$(cat "$daemon_pid_file" 2>/dev/null)
            if [ -n "$daemon_pid" ] && ! kill -0 "$daemon_pid" 2>/dev/null; then
                echo "Cleaning stale daemon directory: $daemon_dir"
                rm -rf "$daemon_dir"
            fi
        fi
    fi
done

# Clean build caches
./gradlew clean --no-daemon

echo "✅ Gradle locks and caches cleaned successfully"