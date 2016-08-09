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
char *path   = NULL;
char *sql_by_cci_home     = NULL;
char *result = NULL;
char *test_tp = NULL;
int count = 0;
char strv[16] = { 0x00 };
char command[1024] = "";
char strd[32] = { 0x00 };
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
        ret = execl (exe_p, "execute", port, dbname, test_tp, filename, resultfile, NULL);
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

char *current_date (char *strt)
{
    time_t curtime;
    time_t t;
    int tmp = 0;
    struct tm *loctime;

    /* Get the current time.  */
    curtime = time (NULL);
    t = time (0);
    /* Convert it to local time representation.  */
    loctime = localtime (&curtime);
    /* Format local time */
    strftime (strt, 8, "%Y%m%d", loctime);
    tmp = strlen (strt);
    strt = strt + tmp;
    sprintf (strt, "%d", t);
    return strt;
}

char *cubrid_version (char *strv)
{
    char str[100] = { 0x00 };
    FILE *fp = NULL;

    sprintf (str, "cubrid_rel|grep -Po '\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}.\\d{1,6}' > v.txt");
    system (str);

    if ((fp = fopen ("v.txt", "r")) != NULL)
    {
        fgets (strv, 16, fp);
        strv[strlen (strv) - 1] = 0x00;
        fclose (fp);
    }

    return strv;
}


int main (int argc, char **argv)
{
    if (argc < 6)
    {
        printf ("please confirm your parameters!");
        exit (-1);
    }

    char *res_folder_name = NULL;
    int res_folder_len = 0;
    int t = 0;
    char *bit = NULL;
    char *hm = NULL;
    
    port    = argv[1];
    dbname  = argv[2];
    test_tp = argv[3];
    bit     = argv[4];
    path    = argv[5];		//loop directory 
    hm      = argv[6];			//the home directory of ccqt 
    memcpy(strd, argv[7], sizeof(strd));             //get the timestamp from ccqt.sh  
    
    cubrid_version (strv);

    res_folder_len = strlen ("Schedule_cdriver_linux_") + strlen (strd) + strlen ("_") * 3 + strlen (strv) + strlen (bit) + strlen (test_tp);
    res_folder_name = malloc (sizeof (char) * (res_folder_len + 1));
    memset (res_folder_name, 0, res_folder_len + 1);
    sprintf (res_folder_name, "schedule_cdriver_linux_%s_%s_%s_%s", test_tp, strd, strv, bit);
    printf("RESULT_DIR: %s\n\n", res_folder_name);
    t = strlen (res_folder_name) + strlen (hm) + strlen ("/result/sql_by_cci/");
    result = malloc (sizeof (char) * (t + 1));
    memset (result, 0, (t + 1));
    sprintf (result, "%s%s%s", hm, "/result/sql_by_cci/", res_folder_name);
    sql_by_cci_home=strcat(hm, "/sql_by_cci/");
    if (res_folder_name != NULL)
        free (res_folder_name);

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
    printf("TOTAL_COUNT: %d\n", count);
}
