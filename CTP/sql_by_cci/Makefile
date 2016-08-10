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

all : clean prepare ccqt execute

prepare:
	$(shell isSupportHoldCas=`cat ${CUBRID}/include/cas_cci.h | grep "cci_set_cas_change_mode" | wc -l`; if [ $$isSupportHoldCas -ne 0 ];then echo "#define CCI_SET_CAS_CHANGE_MODE_INTERFACE  1" >interface_verify.h;else echo "" > interface_verify.h;fi)

ccqt : ccqt.o
	$(CC) -o $@ $^

execute : execute.o
	$(CC) -o $@ $^ $(CUBRID_LDFLAGS)	

execute.o : execute.c
	$(CC) $(CFLAGS) $(CUBRID_INCLUDE) -c -o $@ $^

ccqt.o : ccqt.c
	$(CC) $(CFLAGS) -c -o $@ $^

clean:
	rm -rf *.o ccqt execute interface_verify
