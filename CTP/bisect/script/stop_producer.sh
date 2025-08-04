#!/bin/bash
#
# Stop script for Bisect Producer
#

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CTP_BISECT_HOME="$(dirname "$SCRIPT_DIR")"

PID_FILE="$CTP_BISECT_HOME/bisect_producer.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "Bisect Producer is not running (no PID file found)"
    exit 0
fi

PID=$(cat "$PID_FILE")

if ps -p "$PID" > /dev/null 2>&1; then
    echo "Stopping Bisect Producer (PID: $PID)..."
    kill "$PID"
    
    # Wait for process to stop
    count=0
    while ps -p "$PID" > /dev/null 2>&1 && [ $count -lt 10 ]; do
        sleep 1
        count=$((count + 1))
    done
    
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Process did not stop gracefully, forcing..."
        kill -9 "$PID"
    fi
    
    rm -f "$PID_FILE"
    echo "Bisect Producer stopped"
else
    echo "Bisect Producer is not running (process not found)"
    rm -f "$PID_FILE"
fi
