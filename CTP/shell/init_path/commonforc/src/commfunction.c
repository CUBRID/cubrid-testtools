#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int get_ip_port(char *host, int *port)
{
      
      char strBuff[100]={0};
      char *pBegin,*pEnd;
      char portBuff[20]={0};
      char *initPath;

      initPath=getenv("init_path");     
   
      if ((NULL == initPath)||(0 == strlen(initPath)))
      {
            printf("environment varible init_path is not set \n");
            return -1;
      }

      char *filename;
      filename=strcat(initPath,"/shell_config.xml");
 
      FILE *fp;
      
      fp=fopen(filename,"r");

      if (NULL == fp)
      {
            printf("can not open file: \n" ,filename);
            return -2;
      }
      
      while(fgets(strBuff,99,fp))
      {
            if((pBegin=strstr(strBuff,"<ip>"))!=NULL)
            {
                   pEnd = strstr(pBegin + 1, "</ip>");
                   memcpy(host, pBegin + 4, pEnd - pBegin - 4);
            }
            if((pBegin=strstr(strBuff,"<port>"))!=NULL)
            {
                   pEnd = strstr(pBegin + 1, "</port>");
                   memcpy(portBuff, pBegin + 6, pEnd - pBegin - 6);
                   *port = atoi(portBuff);
            }
      }
      
     // printf("%s\n",host);
     // printf("%d\n",*port);

      fclose(fp);
      return 0;
}

