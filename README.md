# cubrid-testtools
This repository includes functional tools to test CUBRID. 

## CTP (CUBRID Test Program)

CTP is designed to execute all kinds of functional testings for CUBRID.

[How to install CTP](doc/ctp_install_guide.md)

## Quick Start
See [CTP/README.md](CTP/README.md) for a quick start.

## Functional Testing Guide 
* [SQL/MEDIUM/SQL_BY_CCI](doc/sql_guide.md)  
* [Shell for Linux](doc/shell_guide.md)
* [Shell for Windows](doc/shell_win_guide.md)
* [Shell_heavy](./doc/shell_heavy_guide.md) 
* [Shell_long](./doc/shell_long_guide.md)
* [Shell_ext](./doc/shell_ext_guide.md)
* [Shell_HA](./doc/ha_shell_guide.md)
* [CCI](./doc/cci_guide.md)  
* [HA_repl](./doc/ha_repl_guide.md)    
* [Isolation](./doc/isolation_guide.md)
* [RQG](./doc/rqg_guide.md)   
* [Unit test](./doc/unittest_guide.md)
* [JDBC](./doc/jdbc_guide.md)
* [JDBC Compatibility](./doc/jdbc_compatibility_guide.md)
* [CCI Compatibility](./doc/cci_compatibility_guide.md)   
* [MemoryLeak](./doc/memoryleak_guide.md)
* [Code Coverage](./doc/code_coverage_guide.md)

## Install Test

[CUBRID_install_test](CUBRID_install_test/) checks that CUBRID packages install and work on several Linux distributions.
For each OS and package combination in `matrix.conf`, it installs CUBRID in a new Docker container, runs the checks, and removes it.
Results go to `results/<build version>/` as `report.csv`, `report.md` and `report.xml` (JUnit). It runs on its own, without CTP.

```bash
cd CUBRID_install_test
./run_install_test.sh <build URL | package directory>
```

See [CUBRID_install_test/README.md](CUBRID_install_test/README.md) (in Korean) for requirements, options, and pass criteria.
