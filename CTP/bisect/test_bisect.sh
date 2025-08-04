#!/bin/bash
#
# Test script for the bisect workflow
#

# Configuration
PRODUCER_HOST="${PRODUCER_HOST:-localhost}"
PRODUCER_PORT="${PRODUCER_PORT:-8089}"
WORKER_IP="${WORKER_IP:-localhost}"
CALLBACK_URL="${CALLBACK_URL:-http://localhost:8080/bisect/result}"

# Test data - based on the example from the original script
COMMIT_FORMER="19a9f15"  # Known good commit
COMMIT_LATTER="e4c8127"  # Known bad commit

# Display configuration
echo "Bisect Test Script"
echo "=================="
echo "Producer: http://${PRODUCER_HOST}:${PRODUCER_PORT}/bisect"
echo "Worker IP: ${WORKER_IP}"
echo "Callback URL: ${CALLBACK_URL}"
echo "Commit range: ${COMMIT_FORMER} -> ${COMMIT_LATTER}"
echo

# JSON request payload
read -r -d '' JSON_PAYLOAD << EOF
{
  "commitFormer": "${COMMIT_FORMER}",
  "commitLatter": "${COMMIT_LATTER}",
  "buildType": "debug",
  "workerIp": "${WORKER_IP}",
  "tests": [
    "shell/_06_issues/_12_2h/bug_bts_7583/cases/bug_bts_7583.sh",
    "shell/_06_issues/_14_1h/bug_bts_13331/cases/bug_bts_13331.sh",
    "shell/_06_issues/_17_1h/cbrd_20759/hide_utls_cub_admin_unloaddb_password/cases/hide_utls_cub_admin_unloaddb_password.sh",
    "shell/_28_features_844/issue_10709_statistic/issue_10709_statistic_3/cases/issue_10709_statistic_3.sh",
    "shell/_37_elderberry/cbrd_23839/cases/cbrd_23839.sh",
    "shell/_10_plcsql/cbrd_25619/cases/cbrd_25619.sh",
    "shell/_39_fig_cake/cbrd_24046/cases/cbrd_24046.sh",
    "shell/_39_fig_cake/cbrd_25035/cases/cbrd_25035.sh",
    "shell/_39_fig_cake/cbrd_25230/cases/cbrd_25230.sh",
    "shell/_39_fig_cake/cbrd_25395/cte/cases/cte.sh"
  ],
  "callbackUrl": "${CALLBACK_URL}",
  "originIp": "$(hostname -I | awk '{print $1}')"
}
EOF

echo "Sending bisect request..."
echo

# Send the request
RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "$JSON_PAYLOAD" \
  "http://${PRODUCER_HOST}:${PRODUCER_PORT}/bisect")

# Extract HTTP status code
HTTP_STATUS=$(echo "$RESPONSE" | tail -n1 | cut -d: -f2)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "Response: $BODY"
echo "HTTP Status: $HTTP_STATUS"

if [ "$HTTP_STATUS" = "202" ]; then
    echo
    echo "Request accepted. Results will be posted to: ${CALLBACK_URL}"
    echo "Monitor producer logs for progress."
else
    echo
    echo "Request failed!"
fi
