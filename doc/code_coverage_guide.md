# 1. Test Objective

Code coverage is a measurement of how many lines/blocks/arcs of CUBRID code are executed while the automated tests are running. We use valgrind tool to instrument the binaries and run a full set of automated tests. Near all of existing test cases (SQL, MEDIUM, SQL_BY_CCI, HA_REPL, CCI, ISOLATION, SHELL, HA_SHELL, SHELL_HEAVY, SHELL_LONG, YCSB, SYSBENCH, TPC-W, TPC-C, DOTS) are used for code coverage test and scheduled by manual.

# 2. Regression Test Deployment

## 2.1 Deployment overview

<table>
<tr>
<th>Description</th>
<th>User Name</th>
<th>IP</th>
<th>Hostname</th>
<th>Tools to deploy</th>
</tr>
<tr class="even">
<td>Controller</td>
<td>codecov</td>
<td>192.168.1.98</td>
<td>func23</td>
<td> CTP<br>
cc4c <br>
CUBRID source <br>  
 </td>
</tr>
</table>


## 2.2 Installation

# 3. Regression Test Sustaining



