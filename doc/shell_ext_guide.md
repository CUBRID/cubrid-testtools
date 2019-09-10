# 1. Test Objective

There are some more flexible test cases that they require more than one test server (e.g., HA test with 1:1:2 nodes), or depend on repositories like `cubrid-testcases` or `cubrid-testcases-private` or `cubrid-testcases-private-ex`, or hold test server(s) exclusively, or connect test server with `'root'` to change configurations in system level, etc. In order to make such case automation, we make SHELL_EXT suite to implement advanced test.

# 2. SHELL_EXT Test via CTP

Like SHELL test, SHELL_EXT test is executed by CTP test tool. But you have to note that SHELL_EXT use the CTP with branch version `'develop_automation'`. We hope to finally merge `'develop_automation'` into `'develop'` after codes are mature.

# 3. Regression Test Deployment
# 4. Regression Test Sustaining
# 5. SHELL_EXT Test Case Specification
