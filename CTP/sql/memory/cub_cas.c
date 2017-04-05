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
#include <time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>

#define MEM_PATH 14 //the length of "/result/memory"
char *current_date (char *strd);
char *cubrid_version (char *strv);
char *ctp_home = NULL;
//char memory_main_info[128] = { 0x00 };

int
main (int argc, char *argv[])
{
  char server_exe_path[128];
  char valgrind_path[128];
  char default_sup[100];
  char *p = NULL;
  char *valgrind_out_dir = NULL;
  pid_t pid;
  char filename[100];
  ctp_home = getenv ("CTP_HOME");
  p = getenv ("VALGRIND_HOME");
  strcpy (valgrind_path, p);
  strcat (valgrind_path, "/bin/valgrind");

  strcpy (default_sup, "--suppressions=");
  strcat (default_sup, ctp_home);
  strcat (default_sup, "/sql/memory/default.supp");

  int LEN = MEM_PATH + strlen (ctp_home) + 1;
  valgrind_out_dir = malloc (sizeof (char) * LEN);
  memset (valgrind_out_dir, 0, LEN);
  sprintf (valgrind_out_dir, "%s%s", ctp_home, "/result/memory");
  
  //strcpy(memory_main_info, valgrind_out_dir);

  const char *option1 = "--trace-children=yes";
  const char *option2 = "--leak-check=full";
  const char *option3 = "--error-limit=no";
  //const char *option4 = "--xml=yes";
  const char *option5 = "--track-origins=yes";
  const char *option6 = "--num-callers=30"; 
  const char *option7 = "--error-limit=no";
  char log_file[128] = { 0x00 };
  char t[128] = { 0x00 };
  strcpy (t, "--log-file=");

  if (access (valgrind_out_dir, F_OK) < 0)
    {
      if (mkdir (valgrind_out_dir, 0775) == -1)
	{
	  perror ("mkdir error");
	  return -1;
	}
    }

  //pid=getpid();
  sprintf (filename, "%s/memory_cub_cas", valgrind_out_dir);

  sprintf (log_file, "%s%s%s", t, filename, "_%p.log");
  p = getenv ("CUBRID");
  if (p == NULL)
    return -1;

  sprintf (server_exe_path, "%s/bin/cas.exe", p);
  execl (valgrind_path, valgrind_path, log_file, default_sup, option2, option5, option6, option7, server_exe_path, argv[1], NULL);

  if (valgrind_out_dir != NULL)
    free (valgrind_out_dir);
  return -1;

}

