<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-07 | Updated: 2026-04-07 -->

# common/sched

## Purpose
Message-queue-based build job scheduler using ActiveMQ and Quartz. Producer creates test jobs from build triggers on a cron schedule; consumer processes jobs from the queue and invokes test suite runners.

## Key Files
| File | Description |
|------|-------------|
| `init.sh` | Scheduler initialization: parses BUILD_URLS/BUILD_SCENARIOS, exports env vars, provides helper functions |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `src/` | Java source for producer, consumer, and shared scheduler utilities |
| `lib/` | `cubridqa-scheduler.jar` + ActiveMQ 5.8.0, Quartz 2.2.1, JMS dependencies |

## For AI Agents

### Working In This Directory
- **Producer** (`src/.../producer/`): `SchedularMain` uses Quartz cron to trigger `Producer` which pushes test jobs to ActiveMQ
- **Consumer** (`src/.../consumer/`): `Consumer` polls ActiveMQ, invokes suite runners from `common/ext/`
- **Common** (`src/.../common/`): `ActiveMQFactory`, `MQPoolUtil`, `Message`, logging
- `init.sh` exports: `DAILYQA_DAILYSRV_HOST`, `DAILYQA_GIT_USER`, `DAILYQA_SSH_PWD_DEFAULT`
- Helper functions: `runAction()`, `upload_to_dailysrv()`, `check_local_disk_space()`

### Testing Requirements
- Producer: verify cron schedule and message creation
- Consumer: verify message consumption and correct suite runner invocation
- Requires ActiveMQ broker connectivity

### Key Classes
| Class | Purpose |
|-------|---------|
| `producer/Main.java` | Entry point for producer |
| `producer/Producer.java` | Pushes test jobs to ActiveMQ |
| `producer/crontab/SchedularMain.java` | Quartz-based cron scheduling |
| `consumer/Consumer.java` | Main consumer — polls queue |
| `consumer/ConsumerAgent.java` | Agent implementation |
| `common/ActiveMQFactory.java` | ActiveMQ connection factory |
| `common/MQPoolUtil.java` | Message queue pool management |

## Dependencies

### External
- ActiveMQ 5.8.0 (broker, client, pool)
- Quartz 2.2.1 (cron job scheduling)
- Geronimo JMS (JMS spec implementation)

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
