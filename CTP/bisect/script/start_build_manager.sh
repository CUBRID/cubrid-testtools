#!/bin/bash
#
# Start script for Build Manager
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CTP_BISECT_HOME="$(dirname "$SCRIPT_DIR")"

# Check if configuration file exists
CONFIG_FILE="$CTP_BISECT_HOME/conf/build_manager.conf"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found: $CONFIG_FILE"
    echo "Creating default configuration..."
    mkdir -p "$CTP_BISECT_HOME/conf"
    cat > "$CONFIG_FILE" << EOF
# Build Manager Configuration
listen_port=8091
cache_dir=/var/cache/cubrid-builds
build_workers=4
max_cache_size_gb=100
build_timeout_minutes=30
predictor_enabled=true

# Remote build workers (optional)
# worker_endpoints=http://worker1:8092,http://worker2:8092
EOF
    echo "Please edit $CONFIG_FILE and run again"
    exit 1
fi

# Source configuration
source "$CONFIG_FILE"

# Export configuration as environment variables
export BUILD_MANAGER_LISTEN_PORT="${listen_port:-8091}"
export BUILD_MANAGER_CACHE_DIR="${cache_dir:-/var/cache/cubrid-builds}"
export BUILD_MANAGER_BUILD_WORKERS="${build_workers:-4}"
export BUILD_MANAGER_MAX_CACHE_SIZE_GB="${max_cache_size_gb:-100}"
export BUILD_MANAGER_BUILD_TIMEOUT_MINUTES="${build_timeout_minutes:-30}"
export BUILD_MANAGER_PREDICTOR_ENABLED="${predictor_enabled:-true}"
export BUILD_MANAGER_WORKER_ENDPOINTS="${worker_endpoints:-}"

# Create necessary directories
mkdir -p "$BUILD_MANAGER_CACHE_DIR"
mkdir -p "$CTP_BISECT_HOME/log"

# Check if build manager is already running
PID_FILE="$CTP_BISECT_HOME/build_manager.pid"
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Build Manager is already running (PID: $PID)"
        exit 1
    else
        echo "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Start the build manager
LOG_FILE="$CTP_BISECT_HOME/log/build_manager.log"
echo "Starting Build Manager..."
echo "Configuration:"
echo "  Listen Port: $BUILD_MANAGER_LISTEN_PORT"
echo "  Cache Directory: $BUILD_MANAGER_CACHE_DIR"
echo "  Build Workers: $BUILD_MANAGER_BUILD_WORKERS"
echo "  Max Cache Size: ${BUILD_MANAGER_MAX_CACHE_SIZE_GB}GB"
echo "  Log File: $LOG_FILE"

# Update the Python script to use environment variables
cat > "$CTP_BISECT_HOME/src/build_manager_wrapper.py" << 'EOF'
#!/usr/bin/env python3
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Override configuration with environment variables
import build_manager
build_manager.CONFIG.update({
    'listen_port': int(os.environ.get('BUILD_MANAGER_LISTEN_PORT', 8091)),
    'cache_dir': os.environ.get('BUILD_MANAGER_CACHE_DIR', '/var/cache/cubrid-builds'),
    'build_workers': int(os.environ.get('BUILD_MANAGER_BUILD_WORKERS', 4)),
    'max_cache_size_gb': int(os.environ.get('BUILD_MANAGER_MAX_CACHE_SIZE_GB', 100)),
    'build_timeout_minutes': int(os.environ.get('BUILD_MANAGER_BUILD_TIMEOUT_MINUTES', 30)),
    'predictor_enabled': os.environ.get('BUILD_MANAGER_PREDICTOR_ENABLED', 'true').lower() == 'true',
    'worker_endpoints': [url.strip() for url in os.environ.get('BUILD_MANAGER_WORKER_ENDPOINTS', '').split(',') if url.strip()]
})

if __name__ == '__main__':
    build_manager.main()
EOF

chmod +x "$CTP_BISECT_HOME/src/build_manager_wrapper.py"

# Start in background
nohup python3 "$CTP_BISECT_HOME/src/build_manager_wrapper.py" >> "$LOG_FILE" 2>&1 &
PID=$!

# Save PID
echo $PID > "$PID_FILE"

# Wait a moment and check if it started successfully
sleep 2
if ps -p "$PID" > /dev/null 2>&1; then
    echo "Build Manager started successfully (PID: $PID)"
    echo "Logs: tail -f $LOG_FILE"
else
    echo "Failed to start Build Manager"
    rm -f "$PID_FILE"
    tail -20 "$LOG_FILE"
    exit 1
fi
