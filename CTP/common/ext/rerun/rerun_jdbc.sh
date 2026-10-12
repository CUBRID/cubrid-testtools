#!/bin/bash
# 
# Copyright (c) 2016, Search Solution Corporation. All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without 
# modification, are permitted provided that the following conditions are met:
# 
#   * Redistributions of source code must retain the above copyright notice, 
#     this list of conditions and the following disclaimer.
# 
#   * Redistributions in binary form must reproduce the above copyright 
#     notice, this list of conditions and the following disclaimer in 
#     the documentation and/or other materials provided with the distribution.
# 
#   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
#     derived from this software without specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
# USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
#
# Rerun runner of the CTP JDBC harness: jdbc (and jdbc_debug) (CUBRIDQA-1632).
#
# A JDBC case is one JUnit method ("<scenario>/src/<pkg>/<Class>.java => <method>()"). JdbcLocalTest has no
# filter: it compiles every *.java of the scenario and runs every test method of every class it finds. Without a
# harness change the smallest unit that can be selected is the class, and this runner selects it so:
#   1. runAction jdbc_unit_git.act, run_cubrid_install $url (no role: run_jdbc.sh refuses other BUILD_TYPEs), check
#      that cubrid_rel shows BUILD_ID; the template (jdbc_template.conf, else jdbc.conf) is copied into RERUN_WORK
#      (run_jdbc.sh edits the template itself; the rerun never does);
#   2. the repo holding the template's [common] scenario (../cubrid-testcases-private for run_jdbc.sh) is updated
#      with run_git_update under a timeout, the branch is verified and the commit recorded;
#   3. the whole suite at HEAD is compiled once into a jar (same sources and jars as jdbc/bin/run.sh, with this
#      build's cubrid_jdbc.jar), then a stage copy of the scenario is made with every non-source file (lib/,
#      jdbc.properties, ...), no *.java / *.class except the requested classes' sources, and that jar in lib/:
#      jdbc/bin/run.sh compiles only the requested classes (dependencies come from the jar) and JdbcLocalTest
#      discovers and runs only them; the checkout's jdbc.properties and lib/ are left untouched;
#   4. [common] scenario points at the stage, feedback_type=file, no notice URL, no feedback DB keys; one ctp.sh jdbc
#      run under a timeout;
#   5. verdict per requested method from result/jdbc/current_runtime_logs/test-<cat>.xml (<testcase file="<stage>/src/
#      ...java => m()">: no child = PASS, <failure> = FAIL), else the console line "[TESTCASE]: <file> => m() =>
#      OK|NOK"; neither = ERROR. A class or method absent at HEAD is ERROR "case not found at <branch> HEAD".
#
# Known limits:
#   - every test method of a requested class runs (only the requested ones are reported), in one JVM and on one fresh
#     jdbcdb, without the classes that ran before it in the daily run;
#   - a suite that does not compile at HEAD makes every case ERROR, as it breaks the daily run too.

function rerun_jdbc() {
    local template conf commit scenario repo_dir scen_rel map id sce file meth key java stage classes srclist cp_jars jar
    local log xml vfile rc ident_re='^[A-Za-z_$][A-Za-z0-9_$]*$'

    rerun_init jdbc
    rerun_precheck_message || return 0
    if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]; then
        rerun_error_all "the CTP jdbc harness does not support BUILD_TYPE ${BUILD_TYPE} (run_jdbc.sh refuses it too)"
        return 0
    fi
    case "$BUILD_SCENARIOS" in
        jdbc|jdbc_debug) ;;
        *)
            rerun_error_all "category ${BUILD_SCENARIOS} is not handled by the jdbc rerun runner"
            return 0
            ;;
    esac
    if [ -f "${CTP_HOME}/conf/jdbc_template.conf" ]; then
        template=${CTP_HOME}/conf/jdbc_template.conf
    else
        template=${CTP_HOME}/conf/jdbc.conf
    fi
    conf=$RERUN_FAM_WORK/jdbc_rerun.conf
    cp -f "$template" "$conf"
    rerun_on_exit "rm -f '$conf'"
    rerun_secrets_from_conf "$conf"

    # STEP 1-2 of run_jdbc.sh: clean, install
    rerun_run_action jdbc_unit_git.act
    rerun_clean_runtime_logs jdbc
    rerun_step install "$RERUN_INSTALL_TIMEOUT" run_cubrid_install $url
    rc=$?
    if [ $rc -eq 124 -o $rc -eq 137 ]; then
        rerun_error_all "build install timed out after ${RERUN_INSTALL_TIMEOUT}s"
        return 0
    fi
    if ! rerun_check_installed; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi

    # STEP 3: testcases at the branch head
    scenario=$(ini.sh -s common "$conf" scenario)
    scenario=${scenario%/}
    if ! rerun_split_scenario "$scenario" || [ ! -d "$scenario" ]; then
        rerun_error_all "the template [common] scenario '${scenario}' is not a cubrid-testcases* directory on ${USER}@$(hostname)"
        return 0
    fi
    repo_dir=$RERUN_REPO_RAW
    scen_rel=$RERUN_SCEN_REL
    if ! commit=$(rerun_git_update_local "$repo_dir"); then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    rerun_set_tc_commit "$commit"

    # sce_id "<scen_rel>/src/<pkg>/<Class>.java => <method>()" -> key "src/<pkg>/<Class>.java => <method>()"
    map=$RERUN_FAM_WORK/cases.map
    classes=$RERUN_FAM_WORK/classes.txt
    : > "$map"
    : > "$classes"
    while IFS=$'\t' read -r id sce <&3; do
        sce=${sce%$'\r'}
        file=${sce%% => *}
        meth=${sce#* => }
        meth=${meth%"()"}
        if [ "$file" == "$sce" ] || [[ "$sce" != *"()" ]] || rerun_bad_path "$file" || [[ "$file" != "$scen_rel"/src/*.java ]] ||
            ! [[ "$meth" =~ $ident_re ]]; then
            rerun_verdict "$id" ERROR "" "'${sce}' is not a JDBC case id (${scen_rel}/src/<pkg>/<Class>.java => <method>())"
            continue
        fi
        java=$repo_dir/$file
        if [ ! -f "$java" ]; then
            rerun_verdict "$id" ERROR "" "case not found at ${RERUN_BRANCH} HEAD (${commit:0:10}): no ${file##*/}"
            continue
        fi
        if ! grep -q "[^A-Za-z0-9_\$]${meth}[[:space:]]*(" "$java"; then
            rerun_verdict "$id" ERROR "" "case not found at ${RERUN_BRANCH} HEAD (${commit:0:10}): no method ${meth}() in ${file##*/}"
            continue
        fi
        key=${file#"$scen_rel"/}" => ${meth}()"
        printf '%s\t%s\t%s\n' "$id" "$sce" "$key" >> "$map"
        echo "${file#"$scen_rel"/}" >> "$classes"
    done 3< "$RERUN_CASES_FILE"
    if [ ! -s "$map" ]; then
        return 0
    fi
    sort -u "$classes" -o "$classes"

    # compile the whole suite at HEAD into a jar (the dependencies of the requested classes)
    rerun_on_exit "rm -rf '$RERUN_FAM_WORK/stage' '$RERUN_FAM_WORK/classes'"
    mkdir -p "$RERUN_FAM_WORK/classes"
    srclist=$RERUN_FAM_WORK/sources.txt
    (cd "$scenario" && find . -type f -name "*.java") > "$srclist"
    cp_jars=$CUBRID/jdbc/cubrid_jdbc.jar
    for jar in "$scenario"/lib/*.jar; do
        [ -f "$jar" ] || continue
        [ "${jar##*/}" == "cubrid_jdbc.jar" ] && continue
        cp_jars=$cp_jars:$jar
    done
    rerun_log "compiling $(wc -l < "$srclist") JDBC source(s) of ${scen_rel}"
    (cd "$scenario" && timeout 1800 javac -nowarn -d "$RERUN_FAM_WORK/classes" -cp ".:$cp_jars" @"$srclist") \
        > "$RERUN_FAM_WORK/jdbc_compile.log" 2>&1 < /dev/null
    rc=$?
    rerun_save_log "$RERUN_FAM_WORK/jdbc_compile.log" setup/jdbc_compile.log
    if [ $rc -ne 0 ]; then
        rerun_error_all "the JDBC sources at ${RERUN_BRANCH} HEAD (${commit:0:10}) do not compile (see setup/jdbc_compile.log)"
        return 0
    fi

    # stage: everything but sources and classes, then the requested sources and the suite jar
    stage=$RERUN_FAM_WORK/stage/${scenario##*/}
    mkdir -p "$stage"
    stage=$(cd "$stage" && pwd)
    if ! (cd "$scenario" && tar cf - --exclude='*.java' --exclude='*.class' .) | (cd "$stage" && tar xf -); then
        rerun_error_all "cannot stage ${scenario} into ${stage}"
        return 0
    fi
    while IFS= read -r file <&3; do
        mkdir -p "$stage/$(dirname "$file")"
        cp -p "$scenario/$file" "$stage/$file"
    done 3< "$classes"
    mkdir -p "$stage/lib"
    if ! (cd "$RERUN_FAM_WORK/classes" && "${JAVA_HOME:+$JAVA_HOME/bin/}jar" cf "$stage/lib/zz_rerun_suite_classes.jar" .) \
        > "$RERUN_FAM_WORK/jar.log" 2>&1; then
        rerun_save_log "$RERUN_FAM_WORK/jar.log" setup/jdbc_jar.log
        rerun_error_all "cannot build the suite jar (see setup/jdbc_jar.log)"
        return 0
    fi
    rerun_log "$(wc -l < "$classes") class(es) staged in ${stage}"

    # STEP 4 of run_jdbc.sh, plus the rerun overrides
    ini.sh -s common "$conf" scenario "$stage"
    ini.sh -s common "$conf" test_category "$BUILD_SCENARIOS"
    if ! rerun_conf_silence "$conf" common || [ "$(ini.sh -s common "$conf" scenario)" != "$stage" ] ||
        ! rerun_conf_assert "$conf" scenario "$stage"; then
        rerun_error_all "${RERUN_LAST_ERROR:-rerun conf guard failed: [common] scenario is not the stage}"
        return 0
    fi

    # STEP 5: run once
    log=$RERUN_FAM_WORK/harness.log
    rerun_run_harness jdbc "$conf" "$log" "${RERUN_HARNESS_TIMEOUT:-14400}"
    rerun_save_log "$log" harness/harness.log
    rerun_save_runtime_logs jdbc

    # verdicts: test-<cat>.xml, console line as fallback
    xml=${CTP_HOME}/result/jdbc/current_runtime_logs/test-${BUILD_SCENARIOS}.xml
    vfile=$RERUN_FAM_WORK/verdicts.tsv
    {
        grep '^\[TESTCASE\]: ' "$log" | awk -v pre="$stage/" '{
            line = substr($0, 13); p = index(line, pre); if (p != 1) next
            line = substr(line, length(pre) + 1)
            n = split(line, a, " => "); if (n < 3) next
            st = (a[n] == "OK") ? "PASS" : ((a[n] == "NOK") ? "FAIL" : "ERROR")
            print a[1] " => " a[2] "\t" st "\t" (st == "FAIL" ? "JUnit failure; " : "") "verdict from the harness console (test-xml has none)"
        }'
        rerun_junit_verdicts "$xml" | awk -F'\t' -v pre="$stage/" '{
            if (index($1, pre) != 1) next
            st = $2; note = ""
            if (st == "SKIP") { st = "ERROR"; note = "skipped by the harness" }
            else if (st == "ERROR") note = "the harness reported an unknown status"
            else if (st == "FAIL") note = "JUnit failure (see the case log)"
            print substr($1, length(pre) + 1) "\t" st "\t" note
        }'
    } > "$vfile"
    if grep -q 'JDBC source code compile fail' "$log"; then
        RERUN_HARNESS_NOTE="${RERUN_HARNESS_NOTE:+$RERUN_HARNESS_NOTE; }jdbc/bin/run.sh could not compile the staged classes"
    fi

    RERUN_JDBC_STAGE=$stage
    RERUN_JDBC_XML=$xml
    rerun_report_cases "$map" "$vfile" rerun_jdbc_case_log "no verdict: neither ${xml##*/} nor the harness console has this method"
}

# rerun_jdbc_case_log <rerunId> <sce> <key> <status> <note>
function rerun_jdbc_case_log() {
    local key=$3 d=${CTP_HOME}/result/jdbc/current_runtime_logs
    rerun_junit_block "$RERUN_JDBC_XML" "$RERUN_JDBC_STAGE/$key" | rerun_case_log_section "${RERUN_JDBC_XML##*/}"
    rerun_extract_block "$d/run_case_details.log" "[TESTCASE]: $RERUN_JDBC_STAGE/$key => " '^=====' |
        rerun_case_log_section "run_case_details.log (failure message and runtime log)"
}
