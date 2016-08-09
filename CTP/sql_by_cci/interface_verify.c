#include <stdio.h>
#include <string.h>
#include <cas_cci.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <dirent.h>

void
test()
{
    int res = 0;
    int conn = 0;     
    T_CCI_ERROR error;
    res = cci_set_cas_change_mode (conn,CCI_CAS_CHANGE_MODE_KEEP,&error);
    res = cci_set_cas_change_mode (conn,CCI_CAS_CHANGE_MODE_AUTO,&error);
}

int
main(int argc,char* argv[])
{
    printf("The interface can be compiled!\n");
}
