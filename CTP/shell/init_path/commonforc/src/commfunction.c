/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.

 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
 
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

