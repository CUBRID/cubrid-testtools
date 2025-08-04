#!/bin/bash

# Sample script to test the bisect workflow
# This sends a JSON request to CTP to find the first bad commits for failing tests

# Configuration
CTP_HOST="localhost"
CTP_PORT="8089"
CALLBACK_URL="http://localhost:8080/bisect/result"

# Test data - based on the example from the script
COMMIT_FORMER="19a9f15"  # Known good commit
COMMIT_LATTER="e4c8127"  # Known bad commit

# JSON request payload
read -r -d '' JSON_PAYLOAD << EOF
{
  "commitFormer": "$COMMIT_FORMER",
  "commitLatter": "$COMMIT_LATTER",
  "buildType": "debug",
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
  "callbackUrl": "$CALLBACK_URL",
  "originIp": "$(hostname -I | awk '{print $1}')"
}
EOF

echo "Sending bisect request to CTP..."
echo "From: $COMMIT_FORMER"
echo "To: $COMMIT_LATTER"
echo "Tests: 10 failing shell tests"
echo

# Send the request
curl -X POST \
  -H "Content-Type: application/json" \
  -d "$JSON_PAYLOAD" \
  "http://$CTP_HOST:$CTP_PORT/bisect"

echo
echo "Request sent. Results will be posted to: $CALLBACK_URL"
