# Scaling Architecture for CUBRID Bisect Tool

## Overview

The enhanced bisect tool implements horizontal scaling for build generation through a distributed build farm architecture. This allows multiple commits to be built concurrently across multiple machines, significantly reducing bisect time.

## Architecture Components

### 1. Build Manager (Port 8091)
Central coordinator for all build requests:
- **Build Cache**: Stores built artifacts by commit hash
- **Request Queue**: Prioritizes build requests
- **Worker Pool**: Manages local and remote build workers
- **Predictive Building**: Pre-builds commits likely to be tested

### 2. Build Workers (Port 8092)
Dedicated build servers that can run on multiple machines:
- **Concurrent Builds**: Each worker can handle multiple builds
- **Git Repository**: Local clone for fast checkouts
- **Artifact Storage**: Temporary storage for build artifacts
- **Health Monitoring**: Reports status to build manager

### 3. Enhanced Bisect Producer
Integrates with build manager for efficient builds:
- **Smart Judge Scripts**: Request builds from manager
- **Predictive Requests**: Pre-request likely commits
- **Parallel Bisects**: Run multiple bisects concurrently

## Deployment Configurations

### Single Machine Setup
All components on one powerful machine:
```
┌─────────────────────────────────────┐
│          Single Machine             │
│                                     │
│  ┌─────────────┐  ┌──────────────┐ │
│  │   Producer  │  │ Build Manager│ │
│  └─────────────┘  └──────────────┘ │
│                                     │
│  ┌─────────────┐  ┌──────────────┐ │
│  │   Consumer  │  │ Build Worker │ │
│  └─────────────┘  └──────────────┘ │
└─────────────────────────────────────┘
```

### Distributed Build Farm
Multiple build workers across machines:
```
┌──────────────┐     ┌─────────────────┐     ┌──────────────┐
│ Control Node │     │ Build Farm Node1│     │Test Node     │
│              │     │                 │     │              │
│ - Producer   │────▶│ - Build Worker  │     │ - Consumer   │
│ - Build Mgr  │     │   (2 concurrent)│     │              │
└──────────────┘     └─────────────────┘     └──────────────┘
                              │
                     ┌─────────────────┐
                     │ Build Farm Node2│
                     │                 │
                     │ - Build Worker  │
                     │   (2 concurrent)│
                     └─────────────────┘
```

## Configuration Examples

### Build Manager Configuration
```properties
# /CTP/bisect/conf/build_manager.conf
listen_port=8091
cache_dir=/var/cache/cubrid-builds
build_workers=4                    # Local worker threads
max_cache_size_gb=200             # Cache up to 200GB
build_timeout_minutes=30
predictor_enabled=true

# Remote workers (optional)
worker_endpoints=http://worker1.example.com:8092,http://worker2.example.com:8092
```

### Build Worker Configuration
```properties
# /CTP/bisect/conf/build_worker.conf
listen_port=8092
cubrid_src_dir=/home/cubrid/cubrid
build_dir_prefix=build_x86_64
work_dir=/tmp/build_worker
max_concurrent_builds=4           # Adjust based on CPU cores
git_update_interval=300
```

### Enhanced Producer Configuration
```properties
# /CTP/bisect/conf/bisect_producer.conf
listen_port=8089
build_manager_url=http://localhost:8091
max_concurrent_bisects=4
enable_predictive_builds=true
```

## Startup Sequence

1. **Start Build Manager** (on control node):
   ```bash
   cd /path/to/CTP/bisect
   ./script/start_build_manager.sh
   ```

2. **Start Build Workers** (on each build node):
   ```bash
   cd /path/to/CTP/bisect
   ./script/start_build_worker.sh
   ```

3. **Start Producer** (on control node):
   ```bash
   cd /path/to/CTP/bisect
   # Use enhanced producer for scaling
   cp src/bisect_producer_enhanced.py src/bisect_producer.py
   ./script/start_producer.sh
   ```

4. **Start Consumers** (on test nodes):
   ```bash
   cd /path/to/CTP/bisect
   ./script/start_consumer.sh
   ```

## Performance Benefits

### Build Cache Hit Rates
- First bisect: 0% cache hit
- Subsequent bisects in same range: 50-80% cache hit
- Popular commits: Near 100% cache hit

### Parallel Build Performance
With 4 build workers (2 concurrent each):
- Sequential: 8 builds × 5 min = 40 minutes
- Parallel: 40 min / 8 workers = 5 minutes

### Predictive Building
Binary search pattern prediction:
- Reduces wait time by pre-building likely commits
- Most effective for ranges > 32 commits
- Typical improvement: 20-30% faster bisects

## Scaling Guidelines

### CPU Cores
- Build Manager: 2-4 cores
- Build Worker: 2 cores per concurrent build
- Example: 8-core machine → 3-4 concurrent builds

### Memory
- Build Manager: 4GB + cache index
- Build Worker: 4GB per concurrent build
- Example: 16GB machine → 3-4 concurrent builds

### Disk Space
- Build Manager: Size of cache (100-500GB)
- Build Worker: 20GB per concurrent build
- Fast SSD recommended for build directories

### Network
- Build artifacts: ~500MB-1GB each
- Bandwidth: 1Gbps+ recommended for build farm
- Latency: <10ms between manager and workers

## Monitoring

### Build Manager Status
```bash
# Check cache statistics
curl http://localhost:8091/cache/stats

# View build queue
curl http://localhost:8091/queue/status
```

### Build Worker Health
```bash
# Check worker status
curl http://worker:8092/health

# Response:
{
  "status": "healthy",
  "queue_size": 2,
  "active_builds": 1,
  "max_concurrent": 4
}
```

### Bisect Progress
```bash
# Check bisect status
curl http://localhost:8089/bisect/status/<bisect_id>
```

## Best Practices

1. **Cache Management**
   - Set cache size to 2-3× typical working set
   - Monitor eviction rates
   - Place cache on fast SSD

2. **Worker Distribution**
   - Spread workers across availability zones
   - Balance based on CPU/memory resources
   - Monitor network latency

3. **Predictive Tuning**
   - Enable for commit ranges > 16
   - Disable for small ranges to save resources
   - Monitor prediction accuracy

4. **Resource Allocation**
   - Reserve resources for OS/monitoring
   - Leave 20% CPU headroom
   - Monitor swap usage

## Troubleshooting

### Build Failures
1. Check worker logs: `tail -f log/build_worker.log`
2. Verify git repository is up to date
3. Check disk space on worker nodes
4. Verify build dependencies installed

### Cache Issues
1. Check cache directory permissions
2. Monitor cache eviction logs
3. Verify sufficient disk space
4. Check cache index corruption

### Network Problems
1. Test connectivity between nodes
2. Check firewall rules
3. Monitor bandwidth usage
4. Verify DNS resolution

## Example Deployment

### 3-Node Cluster
```yaml
Node 1 (Control):
  - Bisect Producer
  - Build Manager
  - 4 CPU, 8GB RAM, 200GB SSD

Node 2 (Build):
  - Build Worker
  - 8 CPU, 16GB RAM, 100GB SSD
  - max_concurrent_builds: 3

Node 3 (Test):
  - Bisect Consumer
  - 4 CPU, 8GB RAM, 50GB SSD
```

This configuration can handle:
- 3 concurrent builds
- 4 concurrent bisects
- ~200GB build cache
- Estimated 5x speedup over single-threaded
