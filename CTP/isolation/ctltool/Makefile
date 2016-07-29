CC	= gcc
CXX = g++
CUBRID_DIR	= $${CUBRID}
CUBRID_INCLUDE=$(CUBRID_DIR)/include
MYSQL_INCLUDE=/usr/include/mysql/
ORACLE_INCLUDE=$${ORACLE_HOME}/rdbms/public
ORACLE_LIB=$${ORACLE_HOME}/lib

CFLAGS	= -O0 -g -W -Wall

CUBRID_LDFLAGS	= -L$(CUBRID_DIR)/lib -lcubridcs
MYSQL_LDFLAGS	= -L/usr/lib64/mysql -lmysqlclient 
ORACLE_LDFLAGS = -L$(ORACLE_LIB) -locci -lclntsh -lnnz11

all : qactl qacsql

oracle: qactlo qaoracle

qactl : common.o parse.o qamccom.o qactl.o cubrid_drv.o 
	$(CC) -o $@ $^ $(CUBRID_LDFLAGS)

qactlm : common.o parse.o qamccom.o qactlm.o mysql_drv.o
	$(CC) -o $@ $^ $(MYSQL_LDFLAGS)

qactlo : common.o parse.o qamccom.o qactlo.o oracle_drv.o
	$(CC) -o $@ $^ $(ORACLE_LDFLAGS)

qacsql : common.o parse.o cubrid_drv.o qacsql.o
	$(CC) -o $@ $^ $(CUBRID_LDFLAGS) 
	
qamysql : common.o parse.o mysql_drv.o qacsql.o
	$(CC) -o $@ $^ $(MYSQL_LDFLAGS)

qaoracle : common.o parse.o oracle_drv.o qacsql.o 
	$(CC) -o $@ $^ $(ORACLE_LDFLAGS)

qacsql.o : qacsql.c
	$(CC) $(CFLAGS) -c -o $@ $^

qamccom.o : qamccom.c
	$(CC) $(CFLAGS) -c -o $@ $^

parse.o : parse.c
	$(CC) $(CFLAGS) -c -o $@ $^ 

common.o : common.c
	$(CC) $(CFLAGS) -c -o $@ $^ 	

qactl.o : qactl.c
	$(CC) $(CFLAGS) -I$(CUBRID_INCLUDE) -DCUBRID -c -o $@ $^

qactlm.o : qactl.c
	$(CC) $(CFLAGS) -I$(MYSQL_INCLUDE) -D_MYSQL -c -o $@ $^	
	
qactlo.o : qactl.c
	$(CC) $(CFLAGS) -I$(ORACLE_INCLUDE) -DORACLE -c -o $@ $^
	
cubrid_drv.o : cubrid_drv.c
	$(CC) $(CFLAGS) -I$(CUBRID_INCLUDE) -c -o $@ $^				
	
mysql_drv.o : mysql_drv.c
	$(CC) $(CFLAGS) -I$(MYSQL_INCLUDE) -c -o $@ $^	
		
oracle_drv.o : oracle_drv.cpp
	$(CXX) $(CFLAGS) -I$(ORACLE_INCLUDE) -c -o $@ $^	



clean :
	rm -rf *.o qactl qactlm qactlo qacsql qamysql qaoracle
