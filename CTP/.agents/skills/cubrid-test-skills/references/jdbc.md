# JDBC Test Cases

Complete reference for JDBC test cases in CUBRID CTP.

## CTP Implementation Details

JDBC test execution is handled by the JDBC runner framework:
- **Runner script**: `CTP/jdbc/bin/run.sh` - Invokes the JDBC test suite
- **CTP runner only**: `CTP/jdbc/` is a runner module; Java test sources live in testcase repo
- **Test location**: `cubrid-testcases-private/interface/JDBC/test_jdbc/`
- **JUnit framework**: Uses JUnit 4.8.2 for test execution
- **Build system**: Uses `build.xml` (Ant) for compilation

## File Structure

```
test_jdbc/
├── src/
│   └── com/
│       └── cubrid/
│           └── jdbc/
│               └── test/
│                   └── spec/
│                       └── [category]/
│                           └── Test[Name].java
├── lib/
│   └── cubrid_jdbc.jar      # JDBC driver
└── jdbc.properties           # Connection configuration
```

## JDBC Test Case Template

```java
package com.cubrid.jdbc.test.spec.connection;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import com.cubrid.jdbc.test.spec.GeneralTestCase;

/**
 * Test Case: [Brief description]
 * Test Objective: [What JDBC API is being tested]
 * Author: [name]
 */
public class TestConnectionReadOnly extends GeneralTestCase {

    @Test
    public void test1() throws SQLException {
        // Test case logic
        Connection conn = getConnection();
        conn.setReadOnly(false);
        Assert.assertEquals(false, conn.isReadOnly());
    }

    @Test
    public void test2() throws SQLException {
        // Another test scenario
        DatabaseMetaData dmd = conn().getMetaData();
        Assert.assertNotNull(dmd);
    }

    @Ignore
    @Test
    public void testIgnored() throws SQLException {
        // This test is ignored during runtime
        Assert.assertTrue(true);
    }
}
```

## JDBC Conventions

1. **Extend `GeneralTestCase`** for standard test cases
2. **Use `@Test` annotation** to mark test methods
3. **Use `@Ignore` annotation** to skip tests
4. **Test method names** should start with "test"
5. **Use `Assert.*` methods** for assertions
6. **Use `conn()` method** to get database connection

## Test Location

JDBC test cases are in the private testcases repository:
- **Location**: `cubrid-testcases-private/interface/JDBC/test_jdbc/`
- **JUnit version**: JUnit 4.8.2
- **External runner**: `CTP/jdbc/bin/run.sh`

## Execution Commands

| Action | Command |
|--------|---------|
| Run JDBC tests | `ctp.sh jdbc -c conf/jdbc.conf` |
| Compile tests | `cd test_jdbc && ant compile` |

## Result Analysis

### Test Results Location
- **Results**: `CTP/result/jdbc/current_runtime_logs/`
- **Log**: `run_case_details.log` - Detailed test case results
- **Summary**: Console output shows total/success/fail counts

### Checking Results
```bash
# View detailed results
cat CTP/result/jdbc/current_runtime_logs/run_case_details.log

# Sample output:
# [TESTCASE]: /home/jdbc/.../TestReadOnly.java => test1() => OK
# [ELAPSE TIME(ms)]: 57
```

### Understanding Output
- **OK**: Test passed
- **FAIL**: Test failed (check stack trace)
- **ELAPSE TIME**: Execution time in milliseconds

## Configuration

- **Default config**: `CTP/conf/jdbc.conf`
- **Test location**: `scenario = ${HOME}/cubrid-testcases-private/interface/JDBC/test_jdbc`
- **CUBRID settings**: `[jdbc/cubrid.conf]`, `[jdbc/cubrid_broker.conf/%BROKER1]` sections

### Required Environment Caveat

```bash
export LD_LIBRARY_PATH=$CUBRID/lib:$LD_LIBRARY_PATH
```

Set this before running JDBC tests to avoid native library loading issues.

### Common Config Parameters
```ini
[common]
scenario = ${HOME}/cubrid-testcases-private/interface/JDBC/test_jdbc

[jdbc/cubrid.conf]
cubrid_port_id = 1822

[jdbc/cubrid_broker.conf/%BROKER1]
BROKER_PORT = 33120
APPL_SERVER_SHM_ID = 33120
```

### jdbc.properties (in test_jdbc/)
```properties
jdbc.driverClassName=cubrid.jdbc.driver.CUBRIDDriver
jdbc.url=jdbc:cubrid:localhost:33000:testdb:::
jdbc.username=dba
jdbc.password=
jdbc.ip=localhost
jdbc.port=33000
jdbc.dbname=testdb
```

## Debugging Failures

1. **Check connection**: Verify CUBRID server and broker are running
2. **Update JDBC driver**: Replace `test_jdbc/lib/cubrid_jdbc.jar` with tested build's JDBC driver
3. **Run in Eclipse**: Import as Java project and run with JUnit for debugging
4. **Check logs**: Review `run_case_details.log` for stack traces

## CTP-Specific Notes

- **External test cases**: Tests are in `cubrid-testcases-private` repository
- **JUnit 4.8.2**: Standard JUnit version used
- **JDBC driver**: Use `cubrid_jdbc.jar` from test build
- **Configuration**: `jdbc.properties` sets connection parameters
