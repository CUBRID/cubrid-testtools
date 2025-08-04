#!/bin/bash
#
# Start script for Bisect Consumer
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CTP_BISECT_HOME="$(dirname "$SCRIPT_DIR")"

# Check if configuration file exists
CONFIG_FILE="$CTP_BISECT_HOME/conf/bisect_consumer.conf"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found: $CONFIG_FILE"
    echo "Creating default configuration..."
    mkdir -p "$CTP_BISECT_HOME/conf"
    cat > "$CONFIG_FILE" << EOF
# Bisect Consumer Configuration
listen_port=8090
work_dir=/tmp/bisect_consumer
cubrid_install_dir=/tmp/cubrid_test
EOF
    echo "Please edit $CONFIG_FILE and run again"
    exit 1
fi

# Source configuration
source "$CONFIG_FILE"

# Export configuration as environment variables
export BISECT_LISTEN_PORT="${listen_port:-8090}"
export BISECT_WORK_DIR="${work_dir:-/tmp/bisect_consumer}"
export BISECT_CUBRID_INSTALL_DIR="${cubrid_install_dir:-/tmp/cubrid_test}"

# Create necessary directories
mkdir -p "$BISECT_WORK_DIR"
mkdir -p "$CTP_BISECT_HOME/log"

# Check if consumer is already running
PID_FILE="$CTP_BISECT_HOME/bisect_consumer.pid"
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Bisect Consumer is already running (PID: $PID)"
        exit 1
    else
        echo "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Start the consumer
LOG_FILE="$CTP_BISECT_HOME/log/bisect_consumer.log"
echo "Starting Bisect Consumer..."
echo "Configuration:"
echo "  Listen Port: $BISECT_LISTEN_PORT"
echo "  Work Directory: $BISECT_WORK_DIR"
echo "  Log File: $LOG_FILE"

# Update the Python script to use environment variables
cat > "$CTP_BISECT_HOME/src/bisect_consumer_wrapper.py" << 'EOF'
#!/usr/bin/env python3
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Override configuration with environment variables
import bisect_consumer
bisect_consumer.CONFIG.update({
    'listen_port': int(os.environ.get('BISECT_LISTEN_PORT', 8090)),
    'work_dir': os.environ.get('BISECT_WORK_DIR', '/tmp/bisect_consumer'),
    'cubrid_install_dir': os.environ.get('BISECT_CUBRID_INSTALL_DIR', '/tmp/cubrid_test')
})

if __name__ == '__main__':
    bisect_consumer.main()
EOF

chmod +x "$CTP_BISECT_HOME/src/bisect_consumer_wrapper.py"

# Start in background
nohup python3 "$CTP_BISECT_HOME/src/bisect_consumer_wrapper.py" >> "$LOG_FILE" 2>&1 &
PID=$!

# Save PID
echo $PID > "$PID_FILE"

# Wait a moment and check if it started successfully
sleep 2
if ps -p "$PID" > /dev/null 2>&1; then
    echo "Bisect Consumer started successfully (PID: $PID)"
    echo "Logs: tail -f $LOG_FILE"
else
    echo "Failed to start Bisect Consumer"
    rm -f "$PID_FILE"
    tail -20 "$LOG_FILE"
    exit 1
fi
