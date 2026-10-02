#!/bin/bash
# Run a single SQL file using CTP's ConsoleAgent (same output format as ctp.sh sql)
# Usage: run_sql.sh <sql_file> [db_name]
# Note: A matching <db_name>_qa.xml must exist in $CTP_HOME/sql/configuration/Function_Db/
#       Pre-configured: basic (default), testdb, kcc, mdb, neis05, neis08, shell
#       See CTP/README.md for how to create a custom db config.

[ -z "$1" ] && echo "Usage: $0 <sql_file> [db_name]" && exit 1

SQL_FILE="$(readlink -f "$1")"
DB_NAME="${2:-basic}"

[ ! -f "$SQL_FILE" ] && echo "Error: SQL file not found: $1" && exit 1
[ -z "$JAVA_HOME" ] && echo "Error: JAVA_HOME is not set" && exit 1
[ -z "$CUBRID" ] && echo "Error: CUBRID is not set" && exit 1

[ -z "$CTP_HOME" ] && echo "Error: CTP_HOME is not set" && exit 1
export CTP_HOME
[ ! -d "$CTP_HOME/sql/lib" ] && echo "Error: CTP_HOME is invalid: $CTP_HOME" && exit 1

shopt -s nullglob
CPCLASSES=""
for jar in "$CTP_HOME/sql/lib/"*.jar; do
    CPCLASSES="${CPCLASSES:+$CPCLASSES:}$jar"
done
shopt -u nullglob

[ -z "$CPCLASSES" ] && echo "Error: No jar files found in $CTP_HOME/sql/lib/" && exit 1

"$JAVA_HOME/bin/java" \
    -classpath "$CPCLASSES" \
    com.navercorp.cubridqa.cqt.console.ConsoleAgent \
    runCQT sql sql 64 test_default.xml \
    "${SQL_FILE}?db=${DB_NAME}_qa"
exit $?
