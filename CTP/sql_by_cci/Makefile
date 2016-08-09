CC = gcc
SRCS = $(wildcard *.c)
OBJS = $(SRCS:.c = .o)
CUBRID_DIR      = $${CUBRID}
CUBRID_INCLUDE = -I$(CUBRID_DIR)/include
CFLAGS  = -O0 -g -W -Wall
CUBRID_LDFLAGS  = -L$(CUBRID_DIR)/lib -lcascci

ifeq ( $(BITS), 32)
	CFLAGS += -m32
else
	CFLAGS += -m64
endif

all : ccqt execute interface_verify

ccqt : ccqt.o
	$(CC) -o $@ $^

execute : execute.o
	$(CC) -o $@ $^ $(CUBRID_LDFLAGS)	

interface_verify : interface_verify.o
	$(CC) -o $@ $^ $(CUBRID_LDFLAGS) 

interface_verify.o : interface_verify.c
	$(CC) $(CFLAGS) $(CUBRID_INCLUDE) -c -o $@ $^

execute.o : execute.c
	$(CC) $(CFLAGS) $(CUBRID_INCLUDE) -c -o $@ $^

ccqt.o : ccqt.c
	$(CC) $(CFLAGS) -c -o $@ $^


clean:
	rm -rf *.o ccqt execute interface_verify
