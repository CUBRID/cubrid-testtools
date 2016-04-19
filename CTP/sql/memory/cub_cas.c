#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>

#define MEM_PATH 13 //the length of "/result/memory"
char *current_date (char *strd);
char *cubrid_version (char *strv);
char *ctp_home = NULL;
char memory_main_info[128] = { 0x00 };

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
  
  strcpy(memory_main_info, valgrind_out_dir);

  const char *option1 = "--trace-children=yes";
  const char *option2 = "--leak-check=full";
  const char *option3 = "--error-limit=no";
  const char *option4 = "--xml=yes";
  const char *option5 = "--track-origins=yes";
  const char *option6 = "--num-callers=30"; 
  const char *option7 = "--error-limit=no";
  char log_file[128] = { 0x00 };
  char t[128] = { 0x00 };
  strcpy (t, "--xml-file=");

  if (access (valgrind_out_dir, F_OK) < 0)
    {
      if (mkdir (valgrind_out_dir, 0775) == -1)
	{
	  perror ("mkdir error");
	  return -1;
	}
    }

  pid=getpid();
  sprintf (filename, "%s/memory_cub_cas_%d.xml", valgrind_out_dir,pid);

  sprintf (log_file, "%s%s", t, filename);
  p = getenv ("CUBRID");
  if (p == NULL)
    return -1;

  sprintf (server_exe_path, "%s/bin/cas.exe", p);
  execl (valgrind_path, valgrind_path, log_file, default_sup, option4, option2, option6, option7, server_exe_path, argv[1], NULL);

  if (valgrind_out_dir != NULL)
    free (valgrind_out_dir);
  return -1;

}

