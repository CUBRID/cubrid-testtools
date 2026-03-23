#!/bin/bash
# Run a single SQL file using CTP's ConsoleAgent (same output format as ctp.sh sql)
# Usage: run_sql.sh <sql_file> [db_name]

SQL_FILE=$(readlink -f "$1")
DB_NAME="${2:-basic}"

[ -z "$1" ] && echo "Usage: $0 <sql_file> [db_name]" && exit 1
[ ! -f "$SQL_FILE" ] && echo "Error: SQL file not found: $1" && exit 1
[ -z "$JAVA_HOME" ] && echo "Error: JAVA_HOME is not set" && exit 1
[ -z "$CUBRID" ] && echo "Error: CUBRID is not set" && exit 1

CTP_HOME=$(cd $(dirname $(readlink -f $0))/..; pwd)

CPCLASSES=""
for jar in "$CTP_HOME/sql/lib/"*.jar; do
    CPCLASSES="$CPCLASSES:$jar"
done

"$JAVA_HOME/bin/java" \
    -classpath "$CPCLASSES" \
    com.navercorp.cubridqa.cqt.console.ConsoleAgent \
    runCQT sql sql 64 test_default.xml \
    "${SQL_FILE}?db=${DB_NAME}_qa"
