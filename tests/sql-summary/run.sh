#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d "${TMPDIR:-/tmp}/ctp-sql-summary.XXXXXXXX")
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/classes" "$tmp/bin" "$tmp/ctp/sql/configuration"
printf "scenariontypes=sql(.sql)\n" > "$tmp/ctp/sql/configuration/local.properties"
cp "$repo/CTP/sql/configuration/System.xml" "$tmp/ctp/sql/configuration/System.xml"
printf '#!/bin/sh\nprintf "release\\n"\n' > "$tmp/bin/cubrid_rel"
chmod +x "$tmp/bin/cubrid_rel"
export CTP_HOME="$tmp/ctp" PATH="$tmp/bin:$PATH"
classpath="$repo/CTP/sql/lib/*:$repo/CTP/common/lib/*"
src="$repo/CTP/sql/src/com/navercorp/cubridqa/cqt/console"
if [[ -n ${CTP_SQL_RUNTIME_JAR:-} ]]; then
    runtime=$(cd "$(dirname "$CTP_SQL_RUNTIME_JAR")" && pwd)/$(basename "$CTP_SQL_RUNTIME_JAR")
    classpath="$runtime:$classpath"
    javac -cp "$classpath" -d "$tmp/classes" "$repo/tests/sql-summary/SqlSummaryTest.java"
else
    runtime="$tmp/classes"
    javac -cp "$classpath" -sourcepath "$repo/CTP/sql/src" -d "$tmp/classes" \
        "$src/bo/ConsoleBO.java" "$src/util/TestUtil.java" "$src/util/JunitXmlWriter.java" \
        "$src/bean/Summary.java" "$src/bean/SummaryInfo.java" "$src/bean/CaseResult.java" \
        "$src/bean/Test.java" "$repo/tests/sql-summary/SqlSummaryTest.java"
fi
cd "$tmp"
java -cp "$tmp/classes:$classpath" SqlSummaryTest "$tmp" "$runtime"
