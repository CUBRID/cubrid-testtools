# 1. Test Objective

Unittest cases are provided by developers for the purpose of unit test. In order to enhance quality of CUBRID, these unittest cases were decided to add to daily regression test and execute on both release and debug build. In this guide, the detail usage will be introduced.

# 2. Unittest Test Usage

## 2.1 Quick Start

Let's show an example that how to execute unittest test via CTP.

### Step 1: Prepare test cases

Test cases are written in C or C++ programming language by developers. We need compile them to make executable test cases.

Download cubrid source codes from [git repository of CUBRID](https://github.com/CUBRID/cubrid.git) or package (cubrid-<build id>.tar.gz) in CI build:
  
    wget http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8414-294c026/drop/cubrid-10.2.0.8414-294c026.tar.gz
    tar xzvf cubrid-10.2.0.8414-294c026.tar.gz
    cd cubrid-10.2.0.8414-294c026
    
    # generate test cases of unittest based on release build
    sh build.sh  -t 64 -m release -b build_release 
    
    # generate test cases of unittest_debug on debug build
    # sh build.sh -t 64 -m debug -b build_debug
    
After compile, we may get unittest test cases as below:

    ./build_release/bin/unittests_area
    ./build_release/bin/unittests_bit
    ./build_release/bin/unittests_lf
    ./build_release/bin/unittests_snapshot
  
Then, export `CUBRID` environment variable in order that CTP may find cases by it.

Add to `~/.bash_profile`:
    
    export CUBRID=$HOME/cubrid-10.2.0.8414-294c026

### Step 2: Prepare test cases


# 3. Unittest Test Case Specification

# 4. Regression Test Deployment

# 5. Regression Test Sustaining
