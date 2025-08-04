# CUBRID Test Tools - Bisect Workflow Implementation Guide

## Overview

This implementation adds a JSON-based callback workflow that uses `git bisect` to efficiently find the first failing commit for shell tests. Instead of building every commit sequentially, it uses binary search to minimize the number of builds and tests required.

The workflow operates as follows:

1. **QAHome** sends a JSON request with a commit range (former/latter) and list of failing tests
2. **JsonRequestHandler** receives the request and forwards it to the `bisect.request` queue
3. **BisectWorker** runs git bisect for each test to find the first bad commit
4. **BisectResultAggregator** collects results and POSTs them back to the callback URL

## Architecture

### Components

#### 1. JsonRequestHandler.java
- HTTP server listening on port 8089 (configurable)
- Parses incoming JSON requests from QAHome
- Creates and sends messages to the bisect.request queue

#### 2. BisectWorker.java
- Listens to the bisect.request queue
- For each failing test, runs git bisect to find the first bad commit
- Uses a dynamically generated judge script to test each commit
- Collects results including commit hash and author information

#### 3. BisectResultAggregator.java
- Aggregates bisect results into JSON format
- POSTs results back to the callback URL

### Message Flow

```
1. QAHome sends JSON request with commit range and failing tests
2. JsonRequestHandler parses request and sends to bisect.request queue
3. BisectWorker picks up message and for each test:
   - Creates a judge script
   - Runs git bisect to find first bad commit
   - Collects commit hash and author info
4. BisectResultAggregator formats results as JSON and POSTs to callback URL
```

```
QAHome → JsonRequestHandler → bisect.request → BisectWorker
                                                     ↓
QAHome ← HTTP POST ← BisectResultAggregator ←───────┘
```

## JSON Contracts

### Request (QAHome → CTP)
```json
{
  "commitFormer": "19a9f15",      // known good commit
  "commitLatter": "e4c8127",      // known bad commit
  "buildType": "debug",           // debug or release
  "workerIp": "10.0.0.42",       // specific worker node IP to use
  "tests": [                      // list of failing shell tests
    "shell/_06_issues/_12_2h/bug_bts_7583/cases/bug_bts_7583.sh",
    "shell/_06_issues/_14_1h/bug_bts_13331/cases/bug_bts_13331.sh"
  ],
  "callbackUrl": "http://qahome:8080/bisect/result",
  "originIp": "10.0.0.17"
}
```

### Response (CTP → QAHome)
```json
{
  "commitFormer": "19a9f15",
  "commitLatter": "e4c8127",
  "workerIp": "10.0.0.42",
  "generatedAt": "2025-08-04T09:13:14Z",
  "tests": [
    {
      "name": "shell/_06_issues/_12_2h/bug_bts_7583/cases/bug_bts_7583.sh",
      "status": "found",
      "firstBadCommit": "437cc038cbfb9d98ca9777ffca22cb363dce9ce8",
      "author": "정소희 <94791489+sohee-dgist@users.noreply.github.com>",
      "runtimeMs": 185000
    },
    {
      "name": "shell/_06_issues/_14_1h/bug_bts_13331/cases/bug_bts_13331.sh", 
      "status": "found",
      "firstBadCommit": "07afc09e091c6943e51866ef08f2d4b6cf13f16d",
      "author": "Na, Hyunik <67700070+hyunikn@users.noreply.github.com>",
      "runtimeMs": 192000
    }
  ]
}
```

## Setup Instructions

### 1. Configure the System

Edit `conf/bisect.conf` with your environment settings:

```properties
# Path to CUBRID source repository
cubrid.src.dir=/home/cubrid/cubrid

# Path to shell test cases
shell.tc.dir=/home/cubrid/cubrid-testcases-private-ex

# Build configuration  
cubrid.build.arg=-g ninja -m debug build
cubrid.build.dir.debug=build_x86_64_debug
cubrid.build.dir.release=build_x86_64_release

# JSON handler port
json.handler.port=8089

# Feedback configuration
feedback_type=file
feedback_notice_qahome_url=http://192.168.1.86:8080/qaresult/bisectImportAction.nhn?main_id=<MAINID>
result_file_dir=/tmp/cubrid-bisect-results
```

### 2. Start the Services

#### Start ActiveMQ
```bash
cd $ACTIVEMQ_HOME
./bin/activemq start
```

#### Start the Producer with JSON Handler
```bash
cd $CTP_HOME/common/sched
java -cp "lib/*:build/*" com.navercorp.cubridqa.scheduler.producer.Main bisect.conf
```

#### Start the Bisect Worker
```bash
cd $CTP_HOME/common/sched
java -cp "lib/*:build/*" com.navercorp.cubridqa.scheduler.bisect.BisectWorkerMain
```

### 3. Send a Test Request

Use the provided test script:
```bash
./test_bisect_workflow.sh
```

Or send a JSON request directly:
```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{
    "commitFormer": "19a9f15",
    "commitLatter": "e4c8127",
    "buildType": "debug",
    "workerIp": "10.0.0.42",
    "tests": ["shell/path/to/test.sh"],
    "callbackUrl": "http://qahome:8080/bisect/result",
    "originIp": "10.0.0.1"
  }' \
  http://localhost:8089/bisect
```

## How It Works

### Judge Script Generation

For each test, the system generates a judge script that:

1. Resets git submodules
2. Cleans the build directory
3. Builds CUBRID with specified arguments
4. Runs the shell test
5. Checks for "NOK" in the .result file
6. Returns exit code 0 (pass) or 1 (fail)

### Git Bisect Process

```bash
# Start bisect between known good and bad commits
git bisect start <bad_commit> <good_commit>

# Run the judge script for each commit
git bisect run ./judge_script.sh

# Parse output to find first bad commit
```

For each failing test, the system:

1. Creates a judge script that:
   - Builds CUBRID at the current commit
   - Runs the specific shell test
   - Checks for NOK in the .result file
   - Returns 0 (good) or 1 (bad) to git bisect

2. Runs `git bisect start <latter> <former>`
3. Runs `git bisect run ./judge.sh`
4. Parses the output to extract the first bad commit

This binary search approach is much more efficient than testing every commit sequentially.

### Result Format

The system returns results in this format:

```json
{
  "commitFormer": "19a9f15",
  "commitLatter": "e4c8127",
  "workerIp": "10.0.0.42",
  "generatedAt": "2025-08-04T09:13:14Z",
  "tests": [
    {
      "name": "shell/_06_issues/_12_2h/bug_bts_7583/cases/bug_bts_7583.sh",
      "status": "found",
      "firstBadCommit": "437cc038cbfb9d98ca9777ffca22cb363dce9ce8",
      "author": "정소희 <94791489+sohee-dgist@users.noreply.github.com>",
      "runtimeMs": 185000
    }
  ]
}
```

## File-Based Feedback

The system supports two feedback mechanisms:

### 1. File-Based Feedback (Recommended)
When `feedback_type=file`, the system:

1. **Creates result files** in the configured directory:
   - `bisect_main_<MAINID>.dat` - Contains bisect session metadata
   - `bisect_result_<MAINID>_<N>.dat` - Individual test results

2. **File Format**:
   ```
   # Main file
   MAIN_ID=1672851200000
   COMMIT_FORMER=19a9f15
   COMMIT_LATTER=e4c8127
   BUILD_TYPE=debug
   WORKER_IP=10.0.0.42
   ORIGIN_IP=10.0.0.17
   START_TIME=2023-01-04 15:30:00
   END_TIME=2023-01-04 15:32:15
   CATEGORY=bisect
   TEST_TYPE=shell
   
   # Test result files
   MAIN_ID=1672851200000
   TEST_NAME=shell/_06_issues/_12_2h/bug_bts_7583/cases/bug_bts_7583.sh
   STATUS=found
   RUNTIME_MS=185000
   FIRST_BAD_COMMIT=437cc038cbfb9d98ca9777ffca22cb363dce9ce8
   COMMIT_AUTHOR=정소희 <94791489+sohee-dgist@users.noreply.github.com>
   ```

3. **Notifies QAHome** via HTTP GET to the configured URL with the main ID

### 2. HTTP Callback (Legacy)
When `feedback_type=database` (or not set), uses the original JSON POST mechanism.

## Performance Considerations

### Binary Search Efficiency

For a range of N commits:
- Linear search: O(N) builds required
- Binary search: O(log N) builds required

Example: For 128 commits between known good and bad:
- Linear: up to 128 builds
- Binary: maximum 7 builds (log₂ 128)

### Worker Node Selection

The `workerIp` parameter allows QAHome to specify which worker node should handle the request:
- Useful when QAHome knows which node had the failing tests
- Ensures bisect runs on the same environment where failures occurred
- If specified, only the matching worker will process the request

## Benefits

- **Efficiency**: Uses binary search to minimize the number of builds/tests
- **Accuracy**: Finds the exact commit that introduced each failure
- **Parallelism**: Can process multiple tests concurrently
- **No Database**: Results are returned directly via HTTP callback

## Error Handling

- If bisect fails to find a bad commit, status is set to "error"
- Build failures are handled gracefully
- Timeout protection for long-running bisects
