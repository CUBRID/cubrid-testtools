# JSON/Callback Workflow Implementation - Git Bisect Based

This implementation adds support for a JSON-based callback workflow to the CUBRID test tools, using git bisect to efficiently find the first failing commit for shell tests.

## Overview

The workflow operates as follows:

1. **QAHome** sends a JSON request with a commit range (former/latter) and list of failing tests
2. **Producer** receives the request and forwards it to the `bisect.request` queue
3. **Bisect Worker** runs git bisect for each test to find the first bad commit
4. **Result Aggregator** collects results and POSTs them back to the callback URL

## New Components

### 1. JsonRequestHandler.java
- HTTP server listening on port 8089 (configurable)
- Parses incoming JSON requests from QAHome
- Creates and sends messages to the bisect.request queue

### 2. BisectWorker.java
- Listens to the bisect.request queue
- For each failing test, runs git bisect to find the first bad commit
- Uses a dynamically generated judge script to test each commit
- Collects results including commit hash and author information

### 3. ResultAggregator.java
- Aggregates bisect results into JSON format
- POSTs results back to the callback URL

## Message Flow

```
QAHome → JsonRequestHandler → bisect.request → BisectWorker
                                                     ↓
QAHome ← HTTP POST ← ResultAggregator ←─────────────┘
```

## JSON Contracts

### Request (QAHome → CTP)
```json
{
  "commitFormer": "19a9f15",      // known good commit
  "commitLatter": "e4c8127",      // known bad commit
  "buildType": "debug",           // debug or release
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

## Configuration

Add these properties to job.conf:

```properties
# JSON Request Handler
json.handler.port=8089

# Git bisect configuration
cubrid.src.dir=/home/cubrid/cubrid
shell.tc.dir=/home/cubrid/cubrid-testcases-private-ex
cubrid.build.arg=-g ninja -m debug build
cubrid.build.dir=build_x86_64_debug
```

## Running the Workers

1. Start the Producer with JSON handler:
   ```bash
   java -cp ... com.navercorp.cubridqa.scheduler.producer.Main
   ```

2. Start the Bisect Worker:
   ```bash
   java -cp ... com.navercorp.cubridqa.scheduler.bisect.BisectWorkerMain
   ```

## How Git Bisect Works

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

## Benefits

- **Efficiency**: Uses binary search to minimize the number of builds/tests
- **Accuracy**: Finds the exact commit that introduced each failure
- **Parallelism**: Can process multiple tests concurrently
- **No Database**: Results are returned directly via HTTP callback

## Error Handling

- If bisect fails to find a bad commit, status is set to "error"
- Build failures are handled gracefully
- Timeout protection for long-running bisects
