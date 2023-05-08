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
 
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <dirent.h>
#include <time.h>

#define TRUE  1
#define FALSE 0

char *dbname = NULL;
char *port   = NULL;
char *urlproperty = NULL;
char *path   = NULL;
char *sql_by_cci_home     = NULL;
char *result = NULL;
char *test_tp = NULL;
int count = 0;
char command[1024] = "";
FILE *result_recorder = NULL;
char *summarylog = NULL;

int testdir (char *path)
{
    struct stat buf;
    if (lstat (path, &buf) < 0)
    {
        return 0;
    }
    if (S_ISDIR (buf.st_mode))
    {
        return 1;			//directory  
    }
    return 0;
}

int checkext (char *path)
{
    const char *ext = strrchr (path, '.');
    if (!strcmp (ext, ".sql"))
    {
        return 1;
    }
    else
    {
        return 0;
    }
}

int execute (const char *filename, char *resultfile)
{
    char *exe_p = NULL;
    int len = strlen (sql_by_cci_home) + strlen ("execute");
    int ret;
    int child_ret;

    exe_p = (char *) malloc (len + 1);
    memset (exe_p, 0, len + 1);
    strcpy (exe_p, sql_by_cci_home);
    strcat (exe_p, "execute");

    if (fork () == 0)
    {
        ret = execl (exe_p, "execute", port, dbname, test_tp, filename, resultfile, urlproperty, NULL);
        if (ret < 0)
            perror ("Error");
        if (exe_p != NULL)
            free (exe_p);
        exit (0);
    }
    else
    {
        wait (&child_ret);
    }
    if (exe_p != NULL)
        free (exe_p);

    return ret;
}

void init_log (const char *log_path)
{
    char *log_name = "/summary.info";
    int len = strlen (log_path) + strlen (log_name);
    summarylog = malloc (sizeof (char) * (len + 1)); 
    memset (summarylog, 0, (len + 1)); 
    sprintf (summarylog, "%s%s", log_path, log_name);

    if (NULL == (result_recorder = fopen (summarylog, "a+")))
    {    
        perror ("Open log error");
        exit (-1);
    }    
}

void close_log ()
{
    if (fclose (result_recorder) != 0)
    {    
        perror ("Close log error");
        exit (-1);
    }    
}

int mkdir_r (const char *path)
{
    if (path == NULL)
    {
        return -1;
    }

    char *temp = strdup (path);
    char *pos = temp;
    char *str = NULL;
    char *tmp = NULL;

    if (strncmp (temp, "/", 1) == 0)
    {
        pos += 1;
    }
    else if (strncmp (temp, "./", 2) == 0)
    {
        pos += 2;
    }

    for (; *pos != '\0'; ++pos)
    {
        if (*pos == '/')
        {
            int t = pos - temp;
            tmp = malloc (t + 1);
            memset (tmp, 0, t + 1);
            strncpy (tmp, temp, t);
            *pos = '\0';
            if (access (tmp, F_OK) != 0)
            {
                mkdir (tmp, 0755);
            }
            *pos = '/';
            if (tmp != NULL)
                free (tmp);
        }
    }

  free (temp);

  return 0;
}

char *getinfoname (char *casename)
{
    char *res = NULL;
    if (casename == NULL)
        return NULL;

    int len = strlen (casename) + strlen ("info") - strlen ("sql") + 1; 
    res = (char *) malloc (len);
    memset (res, 0, len);
    res = strncpy (res, casename, strlen (casename) - 4);
    res = strcat (res, ".info");

    return res; 
}

int search_infofile( char *path, char *name)
{
    FILE *fp = NULL;
    char tmp_case[512] = "";

    if ( (NULL == path) || (NULL == name))
    {
        printf("error occur in search_infofile function\n");
        return -1;
    }
    else
    {
        sprintf(tmp_case, "%s/%s", path, getinfoname(name));
    }

    if ((fp = fopen( tmp_case, "r")) == NULL)
    {
        return FALSE;
    }
    else
    {
        return TRUE;
    }
}

void write_core_fatal_make( char *path, char *name, char *resultfile)
{
     init_log(resultfile);

     if ( TRUE == search_infofile(path, name))
     {
         fprintf (result_recorder, "%s\n", ":YES");
     }
     else
     {
         fprintf (result_recorder, "%s\n", ":NO");
     }

     close_log ();
}


void get_case_in_directory (char *path)
{
    DIR *db = NULL;
    int LEN = 1024;
    char filename[LEN];
    struct dirent *p = NULL;

    db = opendir (path);
    if (db == NULL)
        printf ("Case directory is wrong, please confirm it.");

    memset (filename, 0, LEN);
    while ((p = readdir (db)))
    {
        if ((strcmp (p->d_name, ".") == 0) || (strcmp (p->d_name, "..") == 0) || (p->d_name[0] == '.') || (strcmp (p->d_name, "answers") == 0))
        {
            continue;
        }
        else
        {
            sprintf (filename, "%s/%s", path, p->d_name);
            if (testdir (filename))
            {
                get_case_in_directory (filename);
            }
            else
            {
                if (checkext (filename))
                {
                    //printf ("%s\n", filename);
                    int ret = execute (filename, result);
                    count = count + 1;
                }
            }
        }
        memset (filename, 0, LEN);
    }
    closedir (db);
}

long getCurrentTime()
{
    struct timeval curTime;
    gettimeofday(&curTime, NULL);
    return ((long)curTime.tv_sec)*1000+(long)curTime.tv_usec/1000;
}

char *time_stamp()
{
    char *timestamp = (char *)malloc(sizeof(char) * 16);
    time_t ltime;
    ltime=time(NULL);
    struct tm *tm;
    tm=localtime(&ltime);
    sprintf(timestamp,"%04d%02d%02d%02d%02d%02d", tm->tm_year+1900, tm->tm_mon, 
    tm->tm_mday, tm->tm_hour, tm->tm_min, tm->tm_sec);
    return timestamp;
}

int main (int argc, char **argv)
{
    if (argc < 6)
    {
        printf ("please confirm your parameters!");
        exit (-1);
    }

    char *res_folder_name = NULL;
    int t = 0;
    char *hm = NULL;
    char *str_date = NULL;
    long start_time, end_time, elapse_time;
    
    port    = argv[1];
    dbname  = argv[2];
    test_tp = argv[3];
    res_folder_name = argv[4];
    path    = argv[5];		//loop directory for cases
    hm      = argv[6];			//the home directory of ctp
    urlproperty = argv[7];
    
    str_date = time_stamp(); 

    t = strlen (res_folder_name) + strlen (hm) + strlen ("/result/sql_by_cci/");
    result = malloc (sizeof (char) * (t + 1));
    memset (result, 0, (t + 1));
    sprintf (result, "%s%s%s", hm, "/result/sql_by_cci/", res_folder_name);
    printf("Result Root Dir: %s\n\n", result);
    sql_by_cci_home = malloc(strlen (hm) + strlen ("/sql_by_cci/") + 1);
    memset(sql_by_cci_home, 0x0, strlen (hm) + strlen ("/sql_by_cci/") + 1);
    sprintf(sql_by_cci_home, "%s%s", hm, "/sql_by_cci/");
    start_time = getCurrentTime();

    if (access (result, 0) != 0)
    {
        if (mkdir (result, 0755) == -1)
        {
            mkdir_r(result);
        }
    }

    if (access (path, F_OK) == 0 && testdir (path))
    {
        get_case_in_directory (path);
    }
    else
    {
        if (checkext (path))
        {
            int ret = execute (path, result);
            count = count + 1;
            printf ("%s is test\n", path);
        }
    }
    end_time = getCurrentTime();
    elapse_time = end_time - start_time;
    printf("TOTAL_COUNT: %d\n", count);
    printf("TOTAL_ELAPSE_TIME: %lu\n", elapse_time);
    
    if(result != NULL) 
        free(result);
    if(sql_by_cci_home != NULL)
        free(sql_by_cci_home);
}
