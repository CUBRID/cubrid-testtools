#!/bin/bash
#
# Start script for Build Worker
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CTP_BISECT_HOME="$(dirname "$SCRIPT_DIR")"

# Check if configuration file exists
CONFIG_FILE="$CTP_BISECT_HOME/conf/build_worker.conf"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found: $CONFIG_FILE"
    echo "Creating default configuration..."
    mkdir -p "$CTP_BISECT_HOME/conf"
    cat > "$CONFIG_FILE" << EOF
# Build Worker Configuration
listen_port=8092
cubrid_src_dir=/home/cubrid/cubrid
build_dir_prefix=build_x86_64
work_dir=/tmp/build_worker
max_concurrent_builds=2
git_update_interval=300
EOF
    echo "Please edit $CONFIG_FILE and run again"
    exit 1
fi

# Source configuration
source "$CONFIG_FILE"

# Export configuration as environment variables
export BUILD_WORKER_LISTEN_PORT="${listen_port:-8092}"
export BUILD_WORKER_CUBRID_SRC_DIR="${cubrid_src_dir:-/home/cubrid/cubrid}"
export BUILD_WORKER_BUILD_DIR_PREFIX="${build_dir_prefix:-build_x86_64}"
export BUILD_WORKER_WORK_DIR="${work_dir:-/tmp/build_worker}"
export BUILD_WORKER_MAX_CONCURRENT_BUILDS="${max_concurrent_builds:-2}"
export BUILD_WORKER_GIT_UPDATE_INTERVAL="${git_update_interval:-300}"

# Create necessary directories
mkdir -p "$BUILD_WORKER_WORK_DIR"
mkdir -p "$CTP_BISECT_HOME/log"

# Check if worker is already running
PID_FILE="$CTP_BISECT_HOME/build_worker.pid"
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Build Worker is already running (PID: $PID)"
        exit 1
    else
        echo "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Start the worker
LOG_FILE="$CTP_BISECT_HOME/log/build_worker.log"
echo "Starting Build Worker..."
echo "Configuration:"
echo "  Listen Port: $BUILD_WORKER_LISTEN_PORT"
echo "  CUBRID Source: $BUILD_WORKER_CUBRID_SRC_DIR"
echo "  Work Directory: $BUILD_WORKER_WORK_DIR"
echo "  Max Concurrent Builds: $BUILD_WORKER_MAX_CONCURRENT_BUILDS"
echo "  Log File: $LOG_FILE"

# Update the Python script to use environment variables
cat > "$CTP_BISECT_HOME/src/build_worker_wrapper.py" << 'EOF'
#!/usr/bin/env python3
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Override configuration with environment variables
import build_worker
build_worker.CONFIG.update({
    'listen_port': int(os.environ.get('BUILD_WORKER_LISTEN_PORT', 8092)),
    'cubrid_src_dir': os.environ.get('BUILD_WORKER_CUBRID_SRC_DIR', '/home/cubrid/cubrid'),
    'build_dir_prefix': os.environ.get('BUILD_WORKER_BUILD_DIR_PREFIX', 'build_x86_64'),
    'work_dir': os.environ.get('BUILD_WORKER_WORK_DIR', '/tmp/build_worker'),
    'max_concurrent_builds': int(os.environ.get('BUILD_WORKER_MAX_CONCURRENT_BUILDS', 2)),
    'git_update_interval': int(os.environ.get('BUILD_WORKER_GIT_UPDATE_INTERVAL', 300))
})

if __name__ == '__main__':
    build_worker.main()
EOF

chmod +x "$CTP_BISECT_HOME/src/build_worker_wrapper.py"

# Start in background
nohup python3 "$CTP_BISECT_HOME/src/build_worker_wrapper.py" >> "$LOG_FILE" 2>&1 &
PID=$!

# Save PID
echo $PID > "$PID_FILE"

# Wait a moment and check if it started successfully
sleep 2
if ps -p "$PID" > /dev/null 2>&1; then
    echo "Build Worker started successfully (PID: $PID)"
    echo "Logs: tail -f $LOG_FILE"
else
    echo "Failed to start Build Worker"
    rm -f "$PID_FILE"
    tail -20 "$LOG_FILE"
    exit 1
fi
