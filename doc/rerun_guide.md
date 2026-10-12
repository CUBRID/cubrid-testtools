# Failed-Case Rerun Guide

# 1. Objective
QAHome's per-run fail table has a **Rerun** column. A QA engineer can rerun one failed case, or every
failed case of a run, on the same build with the latest testcase and answer of that build's testcase
branch. The verdict (pass / fail / error) is shown next to the case, and a pass verifies the case as
`[unknown] Passed on rerun`.

Supported categories (Linux): sql, sql_debug, medium, medium_debug, sql_ext, sql_by_cci, shell,
shell_debug, shell_heavy, shell_long, ha_shell, isolation, isolation_debug, cci, cci_debug, jdbc,
ha_repl, ha_repl_debug, cdc_repl.

# 2. How It Works
```
QAHome (rerun.nhn) --request row--> qaresu.verify_rerun_batch
RerunProducer (producer node) --polls--> clones the original run's message (msg_sended)
                              --sends--> RERUN_<original queue>, e.g. RERUN_QUEUE_CUBRID_QA_SHELL_LINUX
start_consumer.sh (every consumer of the original queue)
    polls RERUN_<queue> before <queue> --> common/ext/run_rerun.sh <exec>
        rerun.nhn?m=rerunCases   (case list)
        common/ext/rerun/rerun_<family>.sh   (install build, update testcases, run only those cases)
        upload_to_dailysrv logs  --> <qahome>/qaresult_en/web/test_error/rerun/<batch id>/
        rerun.nhn?m=rerunReport  (one verdict per case), rerun.nhn?m=rerunFinish
```
* The rerun message is a copy of the message that started the original run: same build package,
  build type, `MKEY_CONFIG` and testcase branch. Its `BUILD_GENERATE_MSG_WAY` is `RERUN` and it carries
  `MKEY_RERUN_BATCH_ID`, `MKEY_RERUN_TOKEN`, `MKEY_RERUN_FEEDBACK_URL`, `MKEY_RERUN_LOG_DIR`,
  `MKEY_RERUN_CASE_COUNT`.
* A rerun never feeds the normal result paths (no DB feedback, no `*ImportAction.nhn` call, no upload
  to `qa_repository/function`): importing a partial run would hide the original run's fail list.
* A rerun can be cancelled from QAHome (the `in progress` cell opens its details). While the harness
  runs, `run_rerun.sh` asks `rerun.nhn?m=rerunPoll` every `RERUN_POLL_SECONDS` (60); on `CANCEL` it
  kills the runner's process group and the process groups of the steps it runs under `timeout` (TERM, then
  KILL after 30 s) and exits without reporting. CUBRID servers the harness started, and processes already
  started on remote worker nodes, are left to the harness's next cleanup, as after a daily run.
* A consumer takes at most one rerun message of a queue before that queue's regular poll, so a
  rerun waits only for the run already in progress on the node. An interrupted rerun is not resumed
  (no continue mode); QAHome turns it into `error` after 24 hours.

# 3. Deploy
## 3.1 Producer node
The producer node (the one that runs `start_producer.sh` / `sender.sh`) needs nothing new beyond its
existing `conf/common.conf` / `conf/producer.conf` (`qahome_db_*`, `activemq_*`,
`build.repository.root`). Optional keys, in `conf/rerun.conf` or `conf/producer.conf`:

| key | default | meaning |
|---|---|---|
| `rerun.poll.interval.seconds` | `60` | how often requests are read |
| `rerun.queue.prefix` | `RERUN_` | rerun queue = prefix + original queue |
| `rerun.batch.limit` | `20` | requests handled per pass |
| `rerun.queue.exclude` | `MEMORY_LEAK,CODE_COVERAGE` | queues never used as the original run (they reuse category names) |

Start it in the background (PID in `conf/rerun_producer_PID`):
```
$ cd ~/CTP/common/script
$ nohup sh start_rerun_producer.sh > ~/rerun_producer.log 2>&1 &
```
Off the producer node (e.g. a staging producer), the build-store check falls back to an HTTP HEAD
request on `BUILD_URLS`.

Check what it would send without changing anything:
```
$ sh start_rerun_producer.sh --dry-run --once
```

## 3.2 Consumers
Nothing to configure: `start_consumer.sh` polls `RERUN_<queue>` for every queue it serves. To keep a
node out of reruns, start it with `--without-rerun`:
```
$ nohup sh start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_LINUX -exec run_shell --without-rerun &
```
A node can also serve reruns only — it never takes a regular message nor resumes a regular run — and
use another queue prefix, which is how a staging test borrows a node without touching daily QA:
```
$ nohup sh start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_LINUX -exec run_shell --rerun-only --rerun-prefix RERUNTEST_ --without-sync &
```
The consumer must be able to reach QAHome's `rerun.nhn` over HTTP (`MKEY_RERUN_FEEDBACK_URL`, set by
QAHome's `rerun.feedback.url`).

# 4. Check a Rerun
* Queue: the `RERUN_*` queues in the ActiveMQ console.
* Consumer: `nohup.out` shows `Action: RERUN_<queue>, run_rerun.sh <exec>, RERUN`, and
  `${CTP_HOME}/result/rerun/batch_<id>/` holds the case list, verdicts and logs.
* QAHome: `verify_rerun_batch` / `verify_rerun` in qaresu; the Rerun column of the run's fail table.
