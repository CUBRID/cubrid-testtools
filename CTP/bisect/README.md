# CUBRID Bisect Tool

A standalone tool for finding the first failing commit for CUBRID shell tests using git bisect.

## Overview

This tool implements a distributed bisect workflow where:
1. **QAHome** (or any client) sends a JSON request with a commit range and list of failing tests
2. **Producer** node receives the request and for each test:
   - Uses git bisect to find the first bad commit
   - Builds CUBRID at each bisect step
   - Sends the build to a consumer node for testing
3. **Consumer** node receives builds and test requests, runs tests, and returns results
4. **Producer** aggregates results and sends them back via HTTP callback

## Architecture

```
QAHome/Client                Producer Node               Consumer Node(s)
     |                            |                            |
     |-- POST /bisect ----------->|                            |
     |   (commit range, tests)    |                            |
     |                            |                            |
     |                            |-- git bisect start         |
     |                            |-- build CUBRID             |
     |                            |-- POST /test ------------->|
     |                            |   (build package, test)    |
     |                            |                            |
     |                            |<-- test result ------------|
     |                            |   (pass/fail)              |
     |                            |                            |
     |                            |-- git bisect good/bad      |
     |                            |   (repeat until found)     |
     |                            |                            |
     |<-- POST callback ---------|                            |
         (bisect results)         |                            |
```

## Installation

1. Clone the repository:
```bash
cd /path/to/cubrid-testtools/CTP
```

2. Configure the Producer node:
```bash
cd bisect
cp conf/bisect_producer.conf.example conf/bisect_producer.conf
# Edit conf/bisect_producer.conf with your settings
```

3. Configure the Consumer node(s):
```bash
cd bisect
cp conf/bisect_consumer.conf.example conf/bisect_consumer.conf
# Edit conf/bisect_consumer.conf with your settings
```

## Configuration

### Producer Configuration (bisect_producer.conf)
```properties
# HTTP server port
listen_port=8089

# Path to CUBRID source repository
cubrid_src_dir=/home/cubrid/cubrid

# Path to shell test cases
shell_tc_dir=/home/cubrid/cubrid-testcases-private-ex

# CUBRID build arguments
build_arg=-g ninja -m debug build

# Build directory name
build_dir=build_x86_64_debug

# Working directory for bisect operations
work_dir=/tmp/bisect_work

# Consumer port (where to send test requests)
consumer_port=8090
```

### Consumer Configuration (bisect_consumer.conf)
```properties
# HTTP server port
listen_port=8090

# Working directory for test execution
work_dir=/tmp/bisect_consumer

# CUBRID installation directory
cubrid_install_dir=/tmp/cubrid_test
```

## Usage

### Starting the Services

On the Producer node:
```bash
cd /path/to/cubrid-testtools/CTP/bisect
./script/start_producer.sh
```

On the Consumer node(s):
```bash
cd /path/to/cubrid-testtools/CTP/bisect
./script/start_consumer.sh
```

### Stopping the Services

```bash
./script/stop_producer.sh
./script/stop_consumer.sh
```

### Sending a Bisect Request

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{
    "commitFormer": "19a9f15",
    "commitLatter": "e4c8127",
    "buildType": "debug",
    "workerIp": "192.168.1.100",
    "tests": [
      "shell/_06_issues/_12_2h/bug_bts_7583/cases/bug_bts_7583.sh",
      "shell/_06_issues/_14_1h/bug_bts_13331/cases/bug_bts_13331.sh"
    ],
    "callbackUrl": "http://qahome:8080/bisect/result",
    "originIp": "192.168.1.10"
  }' \
  http://producer-node:8089/bisect
```

### API Reference

#### Request Format
```json
{
  "commitFormer": "string",     // Known good commit (older)
  "commitLatter": "string",     // Known bad commit (newer)
  "buildType": "string",        // Build type: "debug" or "release"
  "workerIp": "string",         // IP address of consumer node
  "tests": ["string"],          // Array of test paths
  "callbackUrl": "string",      // URL to POST results
  "originIp": "string"          // IP of the requesting client
}
```

#### Response Format
```json
{
  "commitFormer": "string",
  "commitLatter": "string",
  "workerIp": "string",
  "generatedAt": "string",      // ISO 8601 timestamp
  "tests": [
    {
      "name": "string",         // Test path
      "status": "string",       // "found", "error"
      "firstBadCommit": "string", // Git commit hash (if found)
      "author": "string",       // Commit author (if found)
      "error": "string",        // Error message (if error)
      "runtimeMs": number       // Execution time in milliseconds
    }
  ]
}
```

## Testing

1. Start the callback receiver:
```bash
python3 test_callback_receiver.py
```

2. Start producer and consumer services

3. Run the test script:
```bash
./test_bisect.sh
```

## How It Works

1. **Git Bisect Process**: For each test, the producer creates a judge script that:
   - Builds CUBRID at the current commit
   - Packages the build
   - Sends it to the consumer for testing
   - Returns 0 (good) or 1 (bad) based on test result

2. **Binary Search**: Git bisect uses binary search to minimize the number of builds:
   - For N commits, requires at most log₂(N) builds
   - Example: 128 commits → maximum 7 builds

3. **Test Execution**: The consumer:
   - Receives the build package
   - Extracts and sets up CUBRID
   - Runs the shell test
   - Checks for "NOK" in the .result file
   - Returns pass/fail status

## Performance

Based on the original script, processing 10 tests took approximately 37 minutes:
- Average time per test: ~3.7 minutes
- This includes multiple builds per test (due to bisect)
- Actual time depends on:
  - Build speed
  - Test execution time
  - Number of commits in the range
  - Network latency between nodes

## Troubleshooting

### Check Logs
```bash
# Producer logs
tail -f log/bisect_producer.log

# Consumer logs
tail -f log/bisect_consumer.log
```

### Common Issues

1. **Build Failures**: Ensure all build dependencies are installed
2. **Test Not Found**: Verify shell_tc_dir path is correct
3. **Network Issues**: Check firewall rules for ports 8089/8090
4. **Git Issues**: Ensure git repository is clean and up to date

## Requirements

- Python 3.6+
- Git
- CUBRID build environment
- Network connectivity between producer and consumer nodes
