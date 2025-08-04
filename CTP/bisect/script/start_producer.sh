#!/bin/bash
#
# Start script for Bisect Producer
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CTP_BISECT_HOME="$(dirname "$SCRIPT_DIR")"

# Check if configuration file exists
CONFIG_FILE="$CTP_BISECT_HOME/conf/bisect_producer.conf"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found: $CONFIG_FILE"
    echo "Creating default configuration..."
    mkdir -p "$CTP_BISECT_HOME/conf"
    cat > "$CONFIG_FILE" << EOF
# Bisect Producer Configuration
listen_port=8089
cubrid_src_dir=/home/cubrid/cubrid
shell_tc_dir=/home/cubrid/cubrid-testcases-private-ex
build_arg=-g ninja -m debug build
build_dir=build_x86_64_debug
work_dir=/tmp/bisect_work
consumer_port=8090
EOF
    echo "Please edit $CONFIG_FILE and run again"
    exit 1
fi

# Source configuration
source "$CONFIG_FILE"

# Export configuration as environment variables
export BISECT_LISTEN_PORT="${listen_port:-8089}"
export BISECT_CUBRID_SRC_DIR="${cubrid_src_dir:-/home/cubrid/cubrid}"
export BISECT_SHELL_TC_DIR="${shell_tc_dir:-/home/cubrid/cubrid-testcases-private-ex}"
export BISECT_BUILD_ARG="${build_arg:--g ninja -m debug build}"
export BISECT_BUILD_DIR="${build_dir:-build_x86_64_debug}"
export BISECT_WORK_DIR="${work_dir:-/tmp/bisect_work}"
export BISECT_CONSUMER_PORT="${consumer_port:-8090}"

# Create necessary directories
mkdir -p "$BISECT_WORK_DIR"
mkdir -p "$CTP_BISECT_HOME/log"

# Check if producer is already running
PID_FILE="$CTP_BISECT_HOME/bisect_producer.pid"
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Bisect Producer is already running (PID: $PID)"
        exit 1
    else
        echo "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Start the producer
LOG_FILE="$CTP_BISECT_HOME/log/bisect_producer.log"
echo "Starting Bisect Producer..."
echo "Configuration:"
echo "  Listen Port: $BISECT_LISTEN_PORT"
echo "  CUBRID Source: $BISECT_CUBRID_SRC_DIR"
echo "  Shell TC Dir: $BISECT_SHELL_TC_DIR"
echo "  Build Args: $BISECT_BUILD_ARG"
echo "  Log File: $LOG_FILE"

# Update the Python script to use environment variables
cat > "$CTP_BISECT_HOME/src/bisect_producer_wrapper.py" << 'EOF'
#!/usr/bin/env python3
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Override configuration with environment variables
import bisect_producer
bisect_producer.CONFIG.update({
    'listen_port': int(os.environ.get('BISECT_LISTEN_PORT', 8089)),
    'cubrid_src_dir': os.environ.get('BISECT_CUBRID_SRC_DIR', '/home/cubrid/cubrid'),
    'shell_tc_dir': os.environ.get('BISECT_SHELL_TC_DIR', '/home/cubrid/cubrid-testcases-private-ex'),
    'build_arg': os.environ.get('BISECT_BUILD_ARG', '-g ninja -m debug build'),
    'build_dir': os.environ.get('BISECT_BUILD_DIR', 'build_x86_64_debug'),
    'work_dir': os.environ.get('BISECT_WORK_DIR', '/tmp/bisect_work'),
    'consumer_port': int(os.environ.get('BISECT_CONSUMER_PORT', 8090))
})

if __name__ == '__main__':
    bisect_producer.main()
EOF

chmod +x "$CTP_BISECT_HOME/src/bisect_producer_wrapper.py"

# Start in background
nohup python3 "$CTP_BISECT_HOME/src/bisect_producer_wrapper.py" >> "$LOG_FILE" 2>&1 &
PID=$!

# Save PID
echo $PID > "$PID_FILE"

# Wait a moment and check if it started successfully
sleep 2
if ps -p "$PID" > /dev/null 2>&1; then
    echo "Bisect Producer started successfully (PID: $PID)"
    echo "Logs: tail -f $LOG_FILE"
else
    echo "Failed to start Bisect Producer"
    rm -f "$PID_FILE"
    tail -20 "$LOG_FILE"
    exit 1
fi
