# HA Shell Test Guide
# 1 Test Introduction
HA Shell test suit is used to test CUBRID HA features.  
To run a HA shell case, we usually need at least two machines. But sometimes, we need more than two machines.  
So HA shell test is divided to two categories:    
1. Test the HA shell cases which can be run on two machines. The cases are put in 'HA' case path. And these cases are executed for each CI build.  
2. Test the other HA shell cases. The cases are put in 'shell_ext' path. And these cases are usually run before a release.  

In this document, I will mainly introduce the first kind of test. This kind of test is almost the same as shell test.  
For the second kind of test , I will introduce it an the end of this document.  

# 2 Tools Introduction
CTP is the only test tool which is used in HA shell test.   
Source URL: [https://github.com/CUBRID/cubrid-testtools](https://github.com/CUBRID/cubrid-testtools)

# 3 Test Deployments
## 3.1 create and set users  
### controller node
We need create a new user: controller.
Login root user and execute:  
```
sudo useradd controller
```
Set password as our common password for user controller.  
```
sudo passwd  controller
```
 Set the user's password to never expire.  
 ```
 sudo chage -E 2999-1-1 -m 0 -M 99999 controller
 ```
 
 ### worker nodes
We need create two new users: ha, dev.
Login root user and execute:  
```
sudo useradd ha
sudo useradd dev
```
Set password as our common password for user shell and user dev.  
```
sudo passwd  ha
sudo passwd  dev
```
 Set these users' password to never expire.  
 ```
 sudo chage -E 2999-1-1 -m 0 -M 99999 ha
 sudo chage -E 2999-1-1 -m 0 -M 99999 dev
 ```
 
