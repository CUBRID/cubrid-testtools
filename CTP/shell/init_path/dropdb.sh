#!/bin/bash
#Drop database

cubrid server stop $1
cubrid deletedb $1
