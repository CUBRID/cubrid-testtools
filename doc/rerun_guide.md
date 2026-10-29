# Failed-Case Rerun Guide

# 1. Objective
QAHome's per-run fail table has a **Rerun** column. A QA engineer can rerun one failed case, or every
failed case of a run, on the same build with the testcases the original run took. The verdict (pass /
fail / error) is shown next to the case, and a pass verifies the case as `[unknown] Passed on rerun`.
The AI fix page can also rerun the cases of an AI revise / stabilize pull request with the pull request's
testcases, on the newest build of its branch or on the failed build (no auto-verify).

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
  build type, `MKEY_CONFIG` and testcase branch. The original is the run's own message when the run
  recorded it (`resultstat.qacomment`, sql family), else the message consumed last before the run ended,
  so a later manual resend is not taken for it. Its `BUILD_GENERATE_MSG_WAY` is `RERUN` and it carries
  `MKEY_RERUN_BATCH_ID`, `MKEY_RERUN_TOKEN`, `MKEY_RERUN_FEEDBACK_URL`, `MKEY_RERUN_LOG_DIR`,
  `MKEY_RERUN_CASE_COUNT`. The producer makes the token when it claims the request; qaresu keeps only
  its SHA-256 and the `msg_sended` copy has it masked. The consumer passes it to QAHome on curl's stdin,
  never on a command line.
* Testcases: `run_git_update` takes what the original run took (CUBRIDQA-1667): a develop build's testcases
  as of its engine commit time (`BUILD_COMMIT_TIME`, also passed to the shell / isolation test nodes),
  other builds the branch head. A develop run from before that change (2026-10-23) took the branch head of its
  day; its rerun is pinned all the same. When `init.sh` cannot read that time on GitHub, the rerun ends at once
  with an error. The case log names the source (`Testcases from`), and a missing case reads
  `case not found at develop as of the build (<commit>)`.
* A pull request rerun (`MKEY_RERUN_TC_REF=refs/pull/<n>/head`, `MKEY_RERUN_TC_SHA`) updates the checkout
  as above, then fetches the pull request's head from the testcase repository through a temporary remote made
  the way `run_git_update` makes `auto_update_url` (the checkout's origin with `conf/common.conf`'s
  `git_user` / `git_pwd`, so a fork's branch needs no other access; removed right after, the URL never
  printed) and checks it out on the local branch `rerun_tc_pr`; the next `run_git_update` moves the checkout
  back. The commit QAHome knows is required, and a head that no longer contains it is refused. The build pin
  does not apply.
* Log capture (shell family, for QAHome's AI analysis): each requested case script gets one more command in
  front of its first line in the rerun's checkout (the line count does not change). It sources
  `~/ctp_rerun_work/<batch>/capture.sh` on the test node, which shadows `rm` so that every text file the
  case or `finish` removes is copied first, then removed by the real `rm`, and which copies `$CUBRID/log`
  and the files the case left when the script exits (2 MB per file, 30 MB per case; tunables
  `RERUN_CAPTURE_FILE_MAX`, `RERUN_CAPTURE_CASE_MAX`). The scripts are put back after the harness; the
  copies are published under `<rerunId>/capture/` with `INDEX.tsv`. `init.sh` and daily runs are unchanged.
* A rerun never feeds the normal result paths (no DB feedback, no `*ImportAction.nhn` call, no upload
  to `qa_repository/function`): importing a partial run would hide the original run's fail list.
* A rerun can be cancelled from QAHome (the `in progress` cell opens its details). While the harness
  runs, `run_rerun.sh` asks `rerun.nhn?m=rerunPoll` every `RERUN_POLL_SECONDS` (60); on `CANCEL` it
  kills the runner's process group and the process groups of the steps it runs under `timeout` (TERM, then
  KILL after 30 s) and exits without reporting. CUBRID servers the harness started, and processes already
  started on remote worker nodes, are left to the harness's next cleanup, as after a daily run.
* Killing `run_rerun.sh` itself (TERM, INT or HUP) stops the harness the same way and closes the request
  in QAHome with its unreported cases as `error`.
* A consumer takes at most one rerun message of a queue before that queue's regular poll, so a
  rerun waits only for the run already in progress on the node. An interrupted rerun is not resumed
  (no continue mode). A request not started within QAHome's `rerun.timeout.hours` (24), or running longer
  than that, becomes `error` / `timed out`; its message expires from the queue, a consumer that takes it
  anyway is refused, and a running one is told to stop on its next `rerunPoll`.
* Calls to QAHome are retried with a backoff (about 5 minutes in all) when it cannot be reached, so a
  QAHome restart does not lose a rerun. `${CTP_HOME}/result/rerun/` keeps the newest 20 batches
  (`RERUN_KEEP_BATCHES`); the raw harness logs and conf copies of a batch are removed once it is reported.

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
| `rerun.message.ttl.hours` | `24` | a rerun message nobody took leaves the queue; keep it at QAHome's `rerun.timeout.hours` |

A `failover:` broker URL is given a 60 s send timeout and 3 startup reconnect attempts unless it sets a
`timeout` itself, and the producer connects before it claims any request, so a broker outage leaves the
requests waiting instead of half sent.

Start it in the background (PID in `conf/rerun_producer_PID`; a second start is refused) and stop it:
```
$ cd ~/CTP/common/script
$ nohup sh start_rerun_producer.sh > ~/rerun_producer.log 2>&1 &
$ sh start_rerun_producer.sh --stop
```
The scheduler jar must contain `RerunProducer`: after the merge, wait for the CI commit that deploys the
new build artifacts.
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
The prefix must end with `_` (an empty one would make the rerun queue the regular queue), and
`--rerun-only` cannot be combined with `--without-rerun` or `--debug`; the consumer refuses to start
otherwise. A message on a rerun queue that is not `BUILD_GENERATE_MSG_WAY=RERUN` is ignored.
The consumer must be able to reach QAHome's `rerun.nhn` over HTTP (`MKEY_RERUN_FEEDBACK_URL`, set by
QAHome's `rerun.feedback.url`).

# 4. Check a Rerun
* Queue: the `RERUN_*` queues in the ActiveMQ console.
* Consumer: `nohup.out` shows `Action: RERUN_<queue>, run_rerun.sh <exec>, RERUN`, and
  `${CTP_HOME}/result/rerun/batch_<id>/` holds the case list, verdicts and logs.
* QAHome: `verify_rerun_batch` / `verify_rerun` in qaresu; the Rerun column of the run's fail table.
