# Bisect Tool Architecture

## System Components

```
┌─────────────────┐         ┌─────────────────────┐         ┌──────────────────┐
│                 │         │                     │         │                  │
│  QAHome/Client  │────────▶│   Producer Node     │────────▶│  Consumer Node   │
│                 │  HTTP   │                     │  HTTP   │                  │
│                 │         │ - Git Repository    │         │ - Test Executor  │
│                 │         │ - Build Environment │         │ - CUBRID Runtime │
│                 │         │ - Bisect Logic      │         │                  │
│                 │         │                     │         │                  │
└─────────────────┘         └─────────────────────┘         └──────────────────┘
        ▲                             │
        │                             │
        └─────────────────────────────┘
              HTTP Callback
```

## Request Flow

1. **Client → Producer**: POST /bisect
   ```json
   {
     "commitFormer": "abc123",
     "commitLatter": "def456",
     "tests": ["test1.sh", "test2.sh"],
     "workerIp": "10.0.0.2",
     "callbackUrl": "http://client/result"
   }
   ```

2. **Producer**: For each test
   - Start git bisect
   - Build CUBRID at current commit
   - Package build

3. **Producer → Consumer**: POST /test
   ```json
   {
     "buildPackage": "/path/to/build.tar.gz",
     "testPath": "shell/test1.sh",
     "testDir": "/path/to/tests",
     "testScript": "test1.sh",
     "testName": "test1"
   }
   ```

4. **Consumer → Producer**: Test result
   ```json
   {
     "status": "pass|fail",
     "test": "test1"
   }
   ```

5. **Producer**: Continue bisect based on result
   - If fail: git bisect bad
   - If pass: git bisect good
   - Repeat until first bad commit found

6. **Producer → Client**: POST callback
   ```json
   {
     "tests": [{
       "name": "test1.sh",
       "status": "found",
       "firstBadCommit": "xyz789",
       "author": "developer@cubrid.com"
     }]
   }
   ```

## Key Design Decisions

1. **Direct HTTP Communication**: No message queue dependencies
2. **Stateless Consumer**: Can scale horizontally
3. **Producer Coordinates**: Manages git bisect state
4. **Async Processing**: Client gets immediate acknowledgment
5. **Build Once Per Commit**: Producer builds and distributes
