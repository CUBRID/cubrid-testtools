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

#define _GNU_SOURCE
#include <stdio.h>
#include <string.h>
#include <cas_cci.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <dirent.h>
#include <time.h>
#include <string.h>
#include <assert.h>
#include <sys/time.h>
#if ADD_CAS_ERROR_HEADER == 1
#include <broker_cas_error.h>
#endif
#include "interface_verify.h"
#undef _GNU_SOURCE

#define DBMS_OUTPUT_BUFFER_SIZE (50000)
#define MAXLINELENGH 1024*1024*5
#define MAX_SQL_NUM 100*256
#define MAX_SQL_LEN 1024*200
#define MAX_LEN 1024

typedef int bool;

typedef struct SqlStateStruct
{
  char *sql;
  bool hasqp;
  bool iscallwithoutvalue;
} SqlStateStruce;

extern int is_statement_end ();
extern int is_in_plcsql_text ();
extern void clear_line_scanner_state ();
extern void scan_line (const char *line);

static char host[32] = "localhost";
static char user[32] = "dba";
static char passwd[32] = "";
static char url[256] = "";

static char *dbname = NULL;
static char *resname = NULL;
static char *answername = NULL;
static char *summarylog = NULL;

static int port = 0;
static int total_sql = 0;
static int queryplan = 0;
static int has_st = 0;		//add by charlie for format the show trace

static char *parameter[MAX_SQL_NUM];
static FILE *result_recorder = NULL;

static SqlStateStruce sqlstate[MAX_SQL_NUM];

static bool is_server_message_on = 0;

char *
get_err_msg (int err_code)
{
  static char err_msg[1024];
  err_msg[0] = 0x00;
  cci_get_err_msg (err_code, err_msg, sizeof (err_msg));
  return err_msg;
}

int
len (char a[])
{
  int temp = 0, i;

  for (i = 0; a[i] != '\0'; i++)
    {
      temp++;
    }

  return temp;
}

int
get_out_num_by_str (const char *str)
{
  int count = 0;
  char *ch = NULL;
  ch = strstr (str, "$out:");

  while (ch != NULL)
    {
      ch = strstr (ch, "$out:");
      if (ch != '\0')
	{
	  count++;
	}
      else
	{
	  break;
	}
      ch = ch + strlen ("$out:");
    }

  return count;
}

int
chrindex (char a[], char b[])
{
  int i, j, temp;

  for (i = 0; i < len (a) - len (b); i++)
    {
      temp = i;
      j = 0;
      while (j <= len (b) && a[temp] == b[j])
	{
	  temp++;
	  j++;
	}

      if (j == len (b))
	return i;
    }
  return -1;
}

int
mkdir_r (const char *path, const char *res)
{
  if (path == NULL)
    {
      return -1;
    }

  char *temp = strdup (path);
  char *pos = temp;
  char *str = NULL;
  char *tmp = NULL;
  pos = pos + strlen (res);

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

int
copyfile (char *src, char *dest)
{
  int ch;

  FILE *psrc = fopen (src, "r");
  if (psrc == NULL)
    {
      printf ("open %s error !\n", src);
      return -1;
    }

  FILE *pdest = fopen (dest, "w+");
  if (pdest == NULL)
    {
      printf ("open %s error !\n", dest);
      return -1;
    }

  while ((ch = getc (psrc)) != EOF)
    {
      fputc (ch, pdest);
    }
  fclose (psrc);
  fclose (pdest);

  return 0;
}


void
init_log (const char *log_path)
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

void
close_log ()
{
  if (fclose (result_recorder) != 0)
    {
      perror ("Close log error");
      exit (-1);
    }
}

int
isdouble (char *buffer)
{
  int len = strlen (buffer);
  int dotcount = 0, i = 1;
  if (!(buffer[0] == '-' || (buffer[0] >= '0' && buffer[0] <= '9')))
    return 0;

  for (i = 1; i < len; i++)
    {
      if (!(buffer[i] == '.' || (buffer[i] >= '0' && buffer[i] <= '9')))
	return 0;
      if (buffer[i] == '.')
	dotcount++;
      if (dotcount > 1)
	return 0;
    }
  return 1;
}

void
formatdatetime (FILE * fp, char *buffer)
{
  int _position = chrindex (buffer, "-");
  int dot_position = chrindex (buffer, ".");
  int length = strlen (buffer);
  int allzero = 1;
  char *p;
  int i;

  for (i = dot_position + 1; i < length; i++)
    {
      if (buffer[i] != '0')
	{
	  allzero = 0;
	  break;
	}
    }

  if (allzero)
    {
      if (_position > -1 && dot_position > -1 && dot_position < length)
	{
	  p = (char *) malloc ((length + 1) * sizeof (char));
	  memcpy (p, buffer, (length + 1) * sizeof (char));
	  p[dot_position + 1] = '0';
	  p[dot_position + 2] = 0;
	  fprintf (fp, "%s", p);
	  free (p);
	}
    }
  else
    {
      fprintf (fp, "%s", buffer);
    }
}

void
trimnumeric (FILE * fp, char *buffer)
{
  int dot_position = chrindex (buffer, ".");
  int length = strlen (buffer);
  int allzero = 1;
  int i;
  char *p;

  p = (char *) malloc ((length + 1) * sizeof (char));
  memcpy (p, buffer, (length + 1) * sizeof (char));

  if (!isdouble (p))
    {
      printf ("wrong date %s", p);
    }

  if (dot_position < 1)
    {
      fprintf (fp, "%s", p);
      free (p);
      return;
    }

  for (i = dot_position + 1; i < length; i++)
    {
      if (buffer[i] != '0')
	{
	  allzero = 0;
	  break;
	}
    }

  if (allzero && length < 20)
    {
      p[dot_position + 1] = '0';
      p[dot_position + 2] = 0;
      fprintf (fp, "%s", p);
      free (p);
      return;
    }

  if (length > 19)
    {
      sprintf (p, "%le", atof (buffer));
    }
  else
    {
      if ((length - dot_position - 1) > 11)
	{
	  p[dot_position + 11] = 0;
	}
    }
  fprintf (fp, "%s", p);
  free (p);
}

void
trimdouble (FILE * fp, char *buffer)
{
  int dot_position = chrindex (buffer, ".");
  int length = strlen (buffer);
  int allzero = 1;
  int i;
  char *p;
  p = (char *) malloc ((length + 1) * sizeof (char));
  memcpy (p, buffer, (length + 1) * sizeof (char));

  if (!isdouble (p))
    {
      printf ("wrong date %s", p);
    }

  if (dot_position < 1)
    {
      fprintf (fp, "%s", p);
      free (p);
      return;
    }

  for (i = dot_position + 1; i < length; i++)
    {
      if (buffer[i] != '0')
	{
	  allzero = 0;
	  break;
	}
    }

  if (allzero && length < 20)
    {
      p[dot_position + 1] = '0';
      p[dot_position + 2] = 0;
      fprintf (fp, "%s", p);
      free (p);
      return;
    }

  if (length > 19)
    {
      sprintf (p, "%le", atof (buffer));
    }
  else
    {
      if ((length - dot_position - 1) > 0)
	{
	  for (i = length - 1; i > dot_position; i--)
	    {
	      if (p[i] == '0')
		continue;
	      else
		break;
	    }
	  p[i + 1] = 0;
	}
    }
  fprintf (fp, "%s", p);
  free (p);
}

void
trimfloat (FILE * fp, char *buffer)
{
  int dot_position = chrindex (buffer, ".");
  int length = strlen (buffer);
  int allzero = 1;
  int i;
  char *p;
  p = (char *) malloc ((length + 1) * sizeof (char));
  memcpy (p, buffer, (length + 1) * sizeof (char));

  if (!isdouble (p))
    {
      printf ("wrong date %s", p);
    }

  if (dot_position < 1)
    {
      fprintf (fp, "%s", p);
      free (p);
      return;
    }

  for (i = dot_position + 1; i < length; i++)
    {
      if (buffer[i] != '0')
	{
	  allzero = 0;
	  break;
	}
    }

  if (allzero && length < 20)
    {
      p[dot_position + 1] = '0';
      p[dot_position + 2] = 0;
      fprintf (fp, "%s", p);
      free (p);
      return;
    }

  if (length > 19)
    {
      sprintf (p, "%le", atof (buffer));
    }
  else
    {
      while (--length > dot_position)
	{
	  if (buffer[length] == '0')
	    {
	      continue;
	    }
	  else
	    break;
	}
    }
  if (dot_position == length)
    {
      length++;
      p[length] = '0';
    }

  if (length < strlen (buffer))
    p[length + 1] = 0x00;

  fprintf (fp, "%s", p);
  free (p);
}

int
strindex (const char *s, const char *t)
{
  const char *ps = s;
  while (*ps != '\0')
    {
      const char *pps = ps;
      const char *pt = t;
      while (*pps++ == *pt++)
	{
	  if (*pt == '\0')
	    return ps - s;
	}
      ps++;
    }
  return -1;
}

//transfer number to ?
void
trannum (char *plan)
{
  int i = 0, j = 0, k = 0;
  int length;
  char *p;
  length = strlen (plan);
  p = (char *) malloc (sizeof (char) * (length + 1));
  memcpy (p, plan, sizeof (char) * length);
  p[length] = 0;
  memset (plan, 0, sizeof (char) * length);
  while (i < length)
    {
      if (p[i] >= '0' && p[i] <= '9')
	{
	  if (p[i + 1] >= '0' && p[i + 1] <= '9')
	    {
	      i++;
	      continue;
	    }
	  else
	    {
	      plan[j] = '?';
	      j++;
	    }
	}
      else
	{
	  plan[j] = p[i];
	  j++;
	}
      i++;
    }
  free (p);
}

char *
trimline (char *str)
{
  char *p = NULL;
  char *s = NULL;

  if (str == NULL)
    return (str);

  for (s = str; *s != '\0' && (*s == ' ' || *s == '\t' || *s == '\n' || *s == '\r'); s++)
    ;

  if (*s == '\0')
    {
      *str = '\0';
      return (str);
    }

  /* *s must be a non-white char */
  for (p = s; *p != '\0'; p++)
    ;

  for (p--; *p == ' ' || *p == '\t' || *p == '\n' || *p == '\r'; p--)
    ;

  *++p = '\0';
  if (s != str)
    {
      memmove (str, s, (strlen (s) + 1) * sizeof (char));
    }
  return (str);
}

void
trimbit (char *str)
{
  int i, j;
  i = strlen (str);

  for (j = 0; j < i; j++)
    {
      if (str[j] >= 'A' && str[j] <= 'F')
	{
	  str[j] += 32;
	}
    }
}

T_CCI_U_TYPE
getutype (char *p)
{
  if (startswithCI (trimline (p) + 1, "INT"))
    {
      return CCI_U_TYPE_INT;
    }
  if (startswithCI (trimline (p) + 1, "CHAR"))
    {
      return CCI_U_TYPE_CHAR;
    }
  if (startswithCI (trimline (p) + 1, "STRING"))
    {
      return CCI_U_TYPE_STRING;
    }
  if (startswithCI (trimline (p) + 1, "VARCHAR"))
    {
      return CCI_U_TYPE_VARNCHAR;
    }
  if (startswithCI (trimline (p) + 1, "BIT"))
    {
      return CCI_U_TYPE_BIT;
    }
  if (startswithCI (trimline (p) + 1, "VARBIT"))
    {
      return CCI_U_TYPE_VARBIT;
    }
  if (startswithCI (trimline (p) + 1, "NUMERIC"))
    {
      return CCI_U_TYPE_NUMERIC;
    }
  if (startswithCI (trimline (p) + 1, "INT"))
    {
      return CCI_U_TYPE_INT;
    }
  if (startswithCI (trimline (p) + 1, "SHORT"))
    {
      return CCI_U_TYPE_SHORT;
    }
  if (startswithCI (trimline (p) + 1, "MONETARY"))
    {
      return CCI_U_TYPE_MONETARY;
    }
  if (startswithCI (trimline (p) + 1, "FLOAT"))
    {
      return CCI_U_TYPE_FLOAT;
    }
  if (startswithCI (trimline (p) + 1, "DOUBLE"))
    {
      return CCI_U_TYPE_DOUBLE;
    }
  if (startswithCI (trimline (p) + 1, "DATE"))
    {
      return CCI_U_TYPE_DATE;
    }
  if (startswithCI (trimline (p) + 1, "TIME"))
    {
      return CCI_U_TYPE_TIME;
    }
  if (startswithCI (trimline (p) + 1, "TIMESTAMP"))
    {
      return CCI_U_TYPE_TIMESTAMP;
    }
  if (startswithCI (trimline (p) + 1, "SET"))
    {
      return CCI_U_TYPE_SET;
    }
  if (startswithCI (trimline (p) + 1, "MULTISET"))
    {
      return CCI_U_TYPE_MULTISET;
    }
  if (startswithCI (trimline (p) + 1, "SEQUENCE"))
    {
      return CCI_U_TYPE_SEQUENCE;
    }
  if (startswithCI (trimline (p) + 1, "OBJECT"))
    {
      return CCI_U_TYPE_OBJECT;
    }
  if (startswithCI (trimline (p) + 1, "RESULTSET"))
    {
      return CCI_U_TYPE_RESULTSET;
    }
  if (startswithCI (trimline (p) + 1, "BIGINT"))
    {
      return CCI_U_TYPE_BIGINT;
    }
  if (startswithCI (trimline (p) + 1, "DATETIME"))
    {
      return CCI_U_TYPE_DATETIME;
    }
  if (startswithCI (trimline (p) + 1, "BLOB"))
    {
      return CCI_U_TYPE_BLOB;
    }
  if (startswithCI (trimline (p) + 1, "CLOB"))
    {
      return CCI_U_TYPE_CLOB;
    }
  if (startswithCI (trimline (p) + 1, "SMALLINT"))
    {
      return CCI_U_TYPE_INT;
    }

  return CCI_U_TYPE_STRING;
}

T_CCI_A_TYPE
getatype (T_CCI_U_TYPE utype)
{
  switch (utype)
    {
    case CCI_U_TYPE_CHAR:
      return CCI_A_TYPE_STR;
    case CCI_U_TYPE_STRING:
      return CCI_A_TYPE_STR;
    case CCI_U_TYPE_NCHAR:
      return CCI_A_TYPE_STR;
    case CCI_U_TYPE_VARNCHAR:
      return CCI_A_TYPE_STR;
    case CCI_U_TYPE_BIT:
      return CCI_A_TYPE_BIT;
    case CCI_U_TYPE_VARBIT:
      return CCI_A_TYPE_BIT;
    case CCI_U_TYPE_NUMERIC:
      return CCI_A_TYPE_STR;
    case CCI_U_TYPE_INT:
      return CCI_A_TYPE_INT;
    case CCI_U_TYPE_SHORT:
      return CCI_A_TYPE_INT;
    case CCI_U_TYPE_MONETARY:
      return CCI_A_TYPE_DOUBLE;
    case CCI_U_TYPE_FLOAT:
      return CCI_A_TYPE_FLOAT;
    case CCI_U_TYPE_DOUBLE:
      return CCI_A_TYPE_DOUBLE;
    case CCI_U_TYPE_DATE:
      return CCI_A_TYPE_DATE;
    case CCI_U_TYPE_TIME:
      return CCI_A_TYPE_DATE;
    case CCI_U_TYPE_TIMESTAMP:
      return CCI_A_TYPE_DATE;
    case CCI_U_TYPE_OBJECT:
      return CCI_A_TYPE_STR;
    case CCI_U_TYPE_BIGINT:
      return CCI_A_TYPE_BIGINT;
    case CCI_U_TYPE_DATETIME:
      return CCI_A_TYPE_DATE;
    case CCI_U_TYPE_BLOB:
      return CCI_A_TYPE_BLOB;
    case CCI_U_TYPE_CLOB:
      return CCI_A_TYPE_CLOB;
    default:
      return CCI_A_TYPE_STR;
    }
}

int
endswithsemicolon (char *source)
{
  int i = 0;

  if (source == NULL)
    return 0;

  i = strlen (source);
  if (source[i - 1] == ';')
    return 1;

  return 0;
}

char *
getvalue (char *p)
{
  if (strlen (p) < 1)
    {
      return NULL;
    }
  char *temp = trimline (p);

  if (endswithsemicolon (temp))
    {
      p[strlen (p) - 1] = 0;
    }

  return p + 1;
}

void
get_value_by_type (char *str, char *type, void *buf)
{
  char *p = NULL;
  char *temp = NULL;

  p = str;
  if (strlen (p) < 1)
    memcpy (buf, (void *) p, sizeof (char));

  temp = trimline (p);
  if (endswithsemicolon (temp))
    p[strlen (p) - 1] = 0;
  //remove $ character
  p = p + 1;

  if (strcmp (type, "int") == 0)
    {
      int i;
      i = atoi (p);
      memcpy (buf, (void *) &i, sizeof (int));
    }
  else if (strcmp (type, "float") == 0)
    {
      float f;
      f = atof (p);
      memcpy (buf, (void *) &f, sizeof (float));
    }
  else if (strcmp (type, "double") == 0)
    {
      double d;
      d = atof (p);
      memcpy (buf, (void *) &d, sizeof (double));
    }
}

int
startswith (char *source, char *str)
{
  int ret;
  ret = strncmp (source, str, strlen (str));
  return ((ret == 0) ? 1 : 0);
}

int
startswithCI (char *source, char *str)
{
  int ret;
  ret = strncasecmp (source, str, strlen (str));
  return ((ret == 0) ? 1 : 0);
}

int
ParStrLen (char *dest, char c)
{
  int temp = 0;
  int i = 0;
  for (i = 0; dest[i] != '\0'; i++)
    {
      if (dest[i] == c)
	temp++;
    }
  return temp;
}

int
executebindparameter (int req, char *str, int n)
{
  T_CCI_U_TYPE utype = CCI_U_TYPE_NULL;
  int res = 0;
  char *p;
  T_CCI_A_TYPE atype = CCI_A_TYPE_STR;
  const char *split = ",";
  int parLen = ParStrLen (str, '$');
  T_CCI_BIT value;

  if (parLen / 2 != n)
    return 0;

  p = strtok (str, split);
  parLen = 0;
  while (p != NULL)
    {
      if (!(parLen % 2))
	{
	  if (startswithCI (p, "$OUT:"))
	    {
	      parLen = parLen + 2;
	      p = strtok (NULL, split);
	      p = strtok (NULL, split);
	      continue;
	    }
	  utype = getutype (p);
	  atype = getatype (utype);
	}
      else
	{
	  if (atype == CCI_A_TYPE_BIT)
	    {
	      value.buf = getvalue (p);
	      value.size = strlen (getvalue (p));
	      res = cci_bind_param (req, (parLen / 2 + 1), atype, &value, utype, CCI_BIND_PTR);
	    }
	  else if (atype == CCI_A_TYPE_INT)
	    {
	      int x = 0;
	      get_value_by_type (p, "int", (void *) &x);
	      res = cci_bind_param (req, (parLen / 2 + 1), atype, &x, utype, CCI_BIND_PTR);

	    }
	  else if (atype == CCI_A_TYPE_FLOAT)
	    {
	      float f = 0;
	      get_value_by_type (p, "float", (void *) &f);
	      res = cci_bind_param (req, (parLen / 2 + 1), atype, &f, utype, CCI_BIND_PTR);

	    }
	  else if (atype == CCI_A_TYPE_DOUBLE)
	    {
	      double d = 0;
	      get_value_by_type (p, "double", (void *) &d);
	      res = cci_bind_param (req, (parLen / 2 + 1), atype, &d, utype, CCI_BIND_PTR);
	    }
	  else
	    {
	      res = cci_bind_param (req, (parLen / 2 + 1), atype, getvalue (p), utype, CCI_BIND_PTR);
	    }

	  if (res < 0)
	    return 0;
	}
      parLen++;
      p = strtok (NULL, split);
    }
  return 1;
}

int
readFile (char *fileName)
{
  FILE *sql_file;
  int sql_len = 0;
  int ascii1 = 0, ascii2 = 0;
  char line[MAX_SQL_LEN];
  char sql_buf[MAXLINELENGH];
  bool hasqp = 0;

  //initial the total sql count.
  total_sql = 0;

  memset (sql_buf, 0, sizeof (char) * (MAXLINELENGH));
  if ((sql_file = fopen (fileName, "r")) != NULL)
    {
      while (fgets (line, MAX_SQL_LEN, sql_file) != NULL)
	{
	  trimline (line);	// NOTE: in-line modification. line does not end with a '/n' after this line

	  if (strlen (line) == 0)
	    {
	      ;			// do nothing
	    }
	  else if (startswith (line, "--"))
	    {
	      if (startswith (line, "--@queryplan"))
		{
		  hasqp = 1;
		}
	      else if (startswithCI (line, "--+ server-message") ||
		       startswithCI (line, "--+server-message") ||
		       startswithCI (line, "--+ holdcas") || startswithCI (line, "--+holdcas"))
		{
		  sqlstate[total_sql].sql = (char *) malloc (sizeof (char) * strlen (line) + 1);
		  strcpy (sqlstate[total_sql].sql, line);
		  sqlstate[total_sql].hasqp = 0;
		  //if script like "? = call"
		  sqlstate[total_sql].iscallwithoutvalue = 0;

		  total_sql++;
		}
	    }
	  else if (line[0] == '$')
	    {
	      parameter[total_sql] = (char *) malloc (sizeof (char) * (strlen (line) + 1));
	      strcpy (parameter[total_sql], line);
	    }
	  else
	    {
	      // statement
              char* p = sql_buf + sql_len;
	      sql_len += strlen (line) + 1;
	      if (sql_len >= MAXLINELENGH)
		{
		  printf ("The sql statment is too long \n");
		  exit (1);
		}
	      sprintf(p, "%s\n", line);

	      scan_line (line);
	      // the following condition should be replaced with is_statement_end()
	      // but it hugely alters the test results.
	      if (endswithsemicolon (line) && !is_in_plcsql_text ())
		{
		  sqlstate[total_sql].sql = (char *) malloc (sizeof (char) * sql_len + 1);
		  strcpy (sqlstate[total_sql].sql, sql_buf);
		  sqlstate[total_sql].hasqp = hasqp;
		  //if script like "? = call"
		  sqlstate[total_sql].iscallwithoutvalue = startswith (line, "?");

		  total_sql++;

		  memset (sql_buf, 0, sql_len);
		  sql_len = 0;
		  hasqp = 0;
		}

	      if (is_statement_end ())
		{
		  clear_line_scanner_state ();
		}
	    }
	}
    }

  if (sql_file != NULL)
    fclose (sql_file);

  return 0;
}

void
formatplan (FILE * fp, char *plan)
{
  char *str, *p;
  int isplan = 0;
  int isstmt = 0;
  int i, planLen, newline;
  planLen = strlen (plan);
  str = (char *) malloc (sizeof (char) * (planLen + 1));
  memset (str, 0, sizeof (char) * (planLen + 1));
  p = (char *) malloc (sizeof (char) * (planLen + 1));
  memset (p, 0, sizeof (char) * (planLen + 1));
  newline = 0;
  if (plan != NULL)
    {
      for (i = 0; i < planLen; i++)
	{
	  if (plan[i] == 10)
	    {
	      strncpy (str, plan + newline, i - newline + 1);
	      strncpy (p, plan + newline, i - newline + 1);
	      str[i - newline + 1] = 0x00;
	      p[i - newline + 1] = 0x00;
	      newline = i + 1;

	      trimline (p);
	      if (strlen (p) == 0)
		{
		  continue;
		}

	      if (startswith (p, "Query plan:"))
		{
		  isplan = 1;
		  isstmt = 0;
		  fprintf (fp, "%s", str);
		  continue;
		}
	      else if (startswith (p, "Query stmt:"))
		{
		  isplan = 0;
		  isstmt = 1;
		  fprintf (fp, "%s", str);
		  continue;
		}
	      else if (startswith (p, "Trace Statistics:"))
		{
		  isplan = 0;
		  isstmt = 0;
		  fprintf (fp, "%s", str);
		  continue;
		}
	      else
		{
		  if (isplan)
		    {
		      trannum (str);
		      fprintf (fp, "%s", str);
		      continue;
		    }
		  if (isstmt == 1)
		    {
		      trannum (str);
		      fprintf (fp, "%s", str);
		      isplan = 0;
		      isstmt++;
		      continue;
		    }
		  if (isstmt == 2)
		    {
		      trannum (str);
		      if (strindex (str, "skip ORDER BY") > -1)
			{
			  fprintf (fp, "%s", str);
			  isplan = 0;
			  isstmt = 0;
			}

		      if (strindex (str, "skip GROUP BY") > -1)
			{
			  fprintf (fp, "%s", str);
			  isplan = 0;
			  isstmt = 0;
			}
		      continue;
		    }
		}
	    }
	  continue;
	}
      strncpy (str, plan + newline, i - newline + 1);
      str[i - newline + 1] = 0x00;
      fprintf (fp, "%s", str);
      free (str);
      free (p);
    }
}

int
dumptable (FILE * fp, int req, char con, bool hasqueryplan)
{
  int res = 0;
  int ind = 0, index_count = 0, col_count = 0, setsize = -1, index_set = 0;
  int u_type = 0, itemp = 0;
  static int seq2 = 0;
  void *buffer;
  char buffertemp[1024];
  float f;
  double d_value;
  void *bufferset;
  T_CCI_ERROR error;
  T_CCI_COL_INFO *res_col_info;
  T_CCI_SQLX_CMD cmd_type;
  char *plan = NULL;
  char class_str[24] = { 0x00 };

_NEXT_MULTIPLE_LINE_SQL:
  //getting column information when the prepared statement is the SELECT query
  res_col_info = cci_get_result_info (req, &cmd_type, &col_count);
  if (!res_col_info || col_count == 0)
    {
      if (cmd_type != CUBRID_STMT_SELECT && cmd_type != CUBRID_STMT_CALL)
	{
	  fprintf (fp, "%d\n", res);
	  goto _FOR_DML_STATEMENT_END;
	}
      else
	{
	  goto _DUMPTABLE_ERROR;
	}
    }

  for (index_count = 1; index_count <= col_count; index_count++)
    {
      u_type = CCI_GET_RESULT_INFO_TYPE (res_col_info, index_count);
      if (!(u_type == CCI_U_TYPE_FIRST || u_type == CCI_U_TYPE_UNKNOWN || u_type == CCI_U_TYPE_NULL))
	{
	  fprintf (fp, "%s", CCI_GET_RESULT_INFO_NAME (res_col_info, index_count));
	}
      else if (CCI_GET_RESULT_INFO_NAME (res_col_info, index_count) != NULL)
	{

	  fprintf (fp, "%s", CCI_GET_RESULT_INFO_NAME (res_col_info, index_count));
	  fprintf (stdout, "%s", CCI_GET_RESULT_INFO_NAME (res_col_info, index_count));
	}
      fprintf (fp, "    ");
      fprintf (stdout, "    ");
    }

  fprintf (stdout, "\n");
  fprintf (fp, "\n");
  res = cci_cursor (req, 1, CCI_CURSOR_FIRST, &error);
  if (res == CCI_ER_NO_MORE_DATA)
    {
      fprintf (fp, "\n");
      if (queryplan || hasqueryplan)
	{
	  goto _PRINT_QUERY_PLAN;
	}
      else
	{
	  goto _DUMPTABLE_WHILE_END;
	}
    }

  if (res < 0)
    {
      fprintf (stdout, "Error:%d\n", error.err_code);
      goto _DUMPTABLE_ERROR;
    }

  res = cci_fetch (req, &error);
  if (res < 0)
    {
      fprintf (stdout, "Error:%d\n", error.err_code);
      goto _DUMPTABLE_ERROR;
    }

  while (1)
    {
      for (index_count = 1; index_count <= col_count; index_count++)
	{
	  itemp = CCI_GET_RESULT_INFO_TYPE (res_col_info, index_count);
	  if (CCI_IS_SET_TYPE (itemp) || CCI_IS_MULTISET_TYPE (itemp) || CCI_IS_SEQUENCE_TYPE (itemp)
	      || CCI_IS_COLLECTION_TYPE (itemp))
	    {
	      //trimset((char*)buffer);
	      if ((res = cci_get_data (req, index_count, CCI_A_TYPE_SET, &buffer, &ind)) < 0)
		{
		  fprintf (stdout, "Error:%d\n", error.err_code);
		  goto _DUMPTABLE_ERROR;
		}
	      if (ind < 0)
		{
		  fprintf (fp, "null");
		}
	      else
		{
		  setsize = cci_set_size (buffer);
		  for (index_set = 1; index_set <= setsize; index_set++)
		    {
		      res = cci_set_get (buffer, index_set, CCI_A_TYPE_STR, &bufferset, &ind);
		      if (res < 0)
			{
			  goto _DUMPTABLE_ERROR;
			}

		      if (ind < 0)
			{
			  fprintf (fp, "null");
			}
		      else
			{
			  if (((char *) bufferset)[0] == '@')	//if the result is oid then transfer it to characters.
			    {
			      res =
				cci_oid_get_class_name (con, (char *) bufferset, class_str, sizeof (class_str), &error);
			      if (res < 0)
				{
				  //fprintf(stdout, "(%s, %d) ERROR : %s [%d] \n\n", __FILE__, __LINE__,error.err_msg, error.err_code );
				  fprintf (fp, "null");
				}
			      fprintf (fp, "%s", class_str);
			    }
			  else if (isdouble ((char *) bufferset))
			    {
			      trimdouble (fp, (char *) bufferset);
			    }
			  else if ((chrindex ((char *) bufferset, "-") > 0) && (chrindex ((char *) bufferset, ":") > 0))
			    {
			      fprintf (fp, "%s", (char *) bufferset);
			      fprintf (fp, ".0");
			    }
			  else
			    {
			      fprintf (fp, "%s", (char *) bufferset);
			    }
			}
		      fprintf (fp, ",");
		    }
		}
	      fprintf (fp, "     ");
	    }
	  else if (itemp == CCI_U_TYPE_DOUBLE || itemp == CCI_U_TYPE_MONETARY)
	    {
	      if ((res = cci_get_data (req, index_count, CCI_A_TYPE_DOUBLE, &d_value, &ind)) < 0)
		{
		  fprintf (stdout, "Error:%d\n", error.err_code);
		  goto _DUMPTABLE_ERROR;
		}
	      if (ind < 0)
		{
		  fprintf (fp, "null     ");
		}
	      else
		{
		  sprintf (buffertemp, "%.9f", d_value);
		  trimdouble (fp, (char *) buffertemp);
		  fprintf (fp, "     ");
		}
	    }
	  else if (itemp == CCI_U_TYPE_FLOAT)
	    {
	      if ((res = cci_get_data (req, index_count, CCI_A_TYPE_FLOAT, &f, &ind)) < 0)
		{
		  fprintf (stdout, "Error:%d\n", error.err_code);
		  goto _DUMPTABLE_ERROR;
		}
	      if (ind < 0)
		{
		  fprintf (fp, "null     ");
		}
	      else
		{
		  sprintf (buffertemp, "%f", f);
		  trimfloat (fp, (char *) buffertemp);
		  fprintf (fp, "     ");
		}
	    }
	  else
	    {
	      if ((res = cci_get_data (req, index_count, CCI_A_TYPE_STR, &buffer, &ind)) < 0)
		{
		  fprintf (stdout, "Error:%d\n", error.err_code);
		  goto _DUMPTABLE_ERROR;
		}

	      if (ind < 0)
		{
		  fprintf (fp, "null     ");
		}
	      else
		{
		  if (itemp == CCI_U_TYPE_BIT || itemp == CCI_U_TYPE_VARBIT)
		    {
		      trimbit ((char *) buffer);
		    }
		  if (itemp == CCI_U_TYPE_NUMERIC)
		    {
		      trimnumeric (fp, (char *) buffer);
		    }
		  else if (itemp == CCI_U_TYPE_DATETIME)
		    {
		      formatdatetime (fp, (char *) buffer);
		    }
		  else if (itemp == CCI_U_TYPE_NULL)
		    {
		      if (((char *) buffer)[0] == '@')
			{
			  res = cci_oid_get_class_name (con, (char *) buffer, class_str, sizeof (class_str), &error);
			  if (res < 0)
			    {
			      fprintf (stdout, "(%s, %d) ERROR : %s [%d] \n\n", __FILE__, __LINE__, error.err_msg,
				       error.err_code);
			    }
			  fprintf (fp, "%s", class_str);
			}
		      else
			{
			  fprintf (fp, "%s", (char *) buffer);
			}
		    }
		  else if (itemp == CCI_U_TYPE_OBJECT)
		    {
		      res = cci_oid_get_class_name (con, (char *) buffer, class_str, sizeof (class_str), &error);
		      if (res < 0)
			{
			  //fprintf(stdout, "(%s, %d) ERROR : %s [%d] \n\n", __FILE__, __LINE__,error.err_msg, error.err_code );
			  fprintf (fp, "null");
			}
		      fprintf (fp, "%s", class_str);
		    }
		  else
		    {
		      if (has_st)	//add by charlie for format the show trace
			{
			  trannum ((char *) buffer);
			  has_st = 0;
			}
		      fprintf (fp, "%s", (char *) buffer);
		    }

		  if (itemp == CCI_U_TYPE_TIMESTAMP)
		    {
		      fprintf (fp, ".0");
		    }
		  fprintf (fp, "     ");
		}
	    }
	  //finish to restrive data
	  // fprintf (fp, "\n");
	  // goto _DUMPTABLE_WHILE_END;
	}
      fprintf (fp, "\n");
      res = cci_cursor (req, 1, CCI_CURSOR_CURRENT, &error);
      if (res == CCI_ER_NO_MORE_DATA)
	{
	  fprintf (fp, "\n");
	  if (queryplan || hasqueryplan)
	    {
	      res = cci_get_query_plan (req, &plan);
	      {
		if (res < 0)
		  {
		    fprintf (stdout, "%s(%d) - %s ERROR : (%s %d) \n\n", __FILE__, __LINE__, __FUNCTION__,
			     get_err_msg (res), res);
		  }
		if (res >= 0)
		  {
		    formatplan (fp, plan);
		  }
	      }
	    }

	_FOR_DML_STATEMENT_END:
	  res = cci_next_result (req, &error);
	  if (res == CAS_ER_NO_MORE_RESULT_SET)
	    {
	      goto _DUMPTABLE_WHILE_END;
	    }
	  goto _NEXT_MULTIPLE_LINE_SQL;
	}
      if (res < 0)
	{
	  fprintf (stdout, "Error:%d\n", error.err_code);
	  goto _DUMPTABLE_ERROR;
	}
      res = cci_fetch (req, &error);
      if (res < 0)
	{
	  fprintf (stdout, "Error:%d\n", error.err_code);
	  goto _DUMPTABLE_ERROR;
	}
    }

  //release
  if (res < 0)
    {
      fprintf (stdout, "Error:%d\n", error.err_code);
      goto _DUMPTABLE_ERROR;
    }
  return 0;
_PRINT_QUERY_PLAN:
  res = cci_get_query_plan (req, &plan);
  if (res < 0)
    {
      fprintf (stdout, "%s(%d) - %s ERROR : (%s %d) \n\n", __FILE__, __LINE__, __FUNCTION__, get_err_msg (res), res);
    }
  if (res >= 0)
    {
      formatplan (fp, plan);
    }
  goto _DUMPTABLE_WHILE_END;
_DUMPTABLE_WHILE_END:return 0;
_DUMPTABLE_ERROR:return -1;
}

static int
set_server_message (FILE * fp, char conn, bool on)
{
  char sql[20];
  int req, res;
  T_CCI_ERROR error;

  if (on)
    {
      sprintf (sql, "call enable(%d)", DBMS_OUTPUT_BUFFER_SIZE);
    }
  else
    {
      sprintf (sql, "call disable()");
    }

  req = cci_prepare (conn, sql, CCI_PREPARE_CALL, &error);
  if (req < 0)
    {
      fprintf (stdout, "Set Server-Message Error:%d\n", error.err_code);
      fprintf (fp, "Set Server-Message Error:%d\n", error.err_code);
      res = -1;
      goto _END;
    }

  res = cci_execute (req, 0, 0, &error);
  if (res < 0)
    {
      fprintf (stdout, "Set Server-Message Error:%d\n", error.err_code);
      fprintf (fp, "Set Server-Message Error:%d\n", error.err_code);
      goto _END;
    }

  is_server_message_on = on;

_END:
  if (req > 0)
    {
      cci_close_req_handle (req);
    }
  return res;
}

static char *
get_server_output (FILE * fp, char conn)
{
  int req = 0, res = 0;
  T_CCI_ERROR error;
  static const char *sql = "call get_line(?, ?)";
  static char buff[DBMS_OUTPUT_BUFFER_SIZE];
  char *ret = NULL, *p, *str;
  int status, ind;

  req = cci_prepare (conn, sql, CCI_PREPARE_CALL, &error);
  if (req < 0)
    {
      fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
      fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
      goto _END;
    }

  res = cci_register_out_param (req, 1);
  if (res < 0)
    {
      fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
      fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
      goto _END;
    }

  res = cci_register_out_param (req, 2);
  if (res < 0)
    {
      fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
      fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
      goto _END;
    }

  buff[0] = '\n';
  buff[1] = '\0';
  p = buff + 1;
  while (1)
    {
      res = cci_execute (req, 0, 0, &error);
      if (res < 0)
	{
	  fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
	  fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
	  goto _END;
	}

      res = cci_cursor (req, 1, CCI_CURSOR_FIRST, &error);
      if (res == CCI_ER_NO_MORE_DATA)
	{
	  fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
	  fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
	  goto _END;
	}

      res = cci_fetch (req, &error);
      if (res < 0)
	{
	  fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
	  fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
	  goto _END;
	}

      res = cci_get_data (req, 2, CCI_A_TYPE_INT, &status, &ind);
      if (res < 0)
	{
	  fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
	  fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
	  goto _END;
	}

      if (ind == 0 && status == 0)
	{

	  res = cci_get_data (req, 1, CCI_A_TYPE_STR, &str, &ind);
	  if (res < 0)
	    {
	      fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
	      fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
	      goto _END;
	    }

	  assert (ind >= 0);
	  if (ind > 0)
	    {
	      sprintf (p, "%s\n", str);
	      p += (ind + 1);
	    }
	  else
	    {
	      strcpy (p, "\n");
	      p++;
	    }

	}
      else
	{
	  break;
	}

      res = cci_close_query_result (req, &error);
      if (res < 0)
	{
	  fprintf (stdout, "Get Server-Output Error:%d\n", error.err_code);
	  fprintf (fp, "Get Server-Output Error:%d\n", error.err_code);
	  goto _END;
	}
    }

  ret = buff;

_END:
  if (req > 0)
    cci_close_req_handle (req);
  return ret;
}

int
execute (FILE * fp, char conn, char *sql, bool hasqueryplan)
{
  static int seq = 0;
  int req = 0, res = 0, ret = 0;
  int execute_result = 0;
  T_CCI_ERROR error;
  int col_count = 0;
  char *plan = NULL;
  T_CCI_COL_INFO *res_col_info;
  T_CCI_SQLX_CMD cmd_type;
  char *server_output_buffer = NULL;

  fprintf (fp, "===================================================\n");

  req = cci_prepare (conn, sql, 0, &error);
  if (req < 0)
    {
      fprintf (stdout, "Error:%d\n", error.err_code);
      fprintf (fp, "Error:%d\n", error.err_code);
      if (is_server_message_on)
	{
	  char *end = strstr (error.err_msg, "[CAS INFO");
	  if (end)
	    {
	      *end = '\0';
	    }
	  fprintf (fp, "%s\n\n", error.err_msg);
	}
      ret = -1;
      goto _END;
    }

  res = cci_execute (req, CCI_EXEC_QUERY_ALL, 0, &error);
  if (is_server_message_on)
    {
      server_output_buffer = get_server_output (fp, conn);
    }
  if (res < 0)
    {
      fprintf (stdout, "Error:%d\n", error.err_code);
      fprintf (fp, "Error:%d\n", error.err_code);
      if (is_server_message_on)
	{
	  char *end = strstr (error.err_msg, "[CAS INFO");
	  if (end)
	    {
	      *end = '\0';
	    }
	  fprintf (fp, "%s\n", error.err_msg);
	}
      ret = -1;
      goto _END;
    }

  //if find the sql was "show trace;". then mark it.
  //add by charlie for format the show trace
  if (startswithCI (sql, "show trace;"))
    {
      has_st = 1;
    }

  //getting column information when the prepared statement is the SELECT query
  res_col_info = cci_get_result_info (req, &cmd_type, &col_count);
  if (cmd_type == CUBRID_STMT_SELECT || cmd_type == CUBRID_STMT_CALL || cmd_type == CUBRID_STMT_EVALUATE
      || cmd_type == CUBRID_STMT_GET_STATS)
    {
      dumptable (fp, req, conn, hasqueryplan);
      goto _END;
    }
  else if (cmd_type == CUBRID_STMT_UPDATE)
    {
      fprintf (fp, "%d\n", res);
      if (hasqueryplan)
	{
	  res = cci_get_query_plan (req, &plan);
	  if (res < 0)
	    {
	      fprintf (stdout, "%s(%d) - %s ERROR : (%s %d) \n\n",
		       __FILE__, __LINE__, __FUNCTION__, get_err_msg (res), res);
	    }
	  if (res >= 0)
	    {
	      formatplan (fp, plan);
	    }
	}
    }
  else
    {
      fprintf (fp, "%d\n", res);
    }

  while (1)
    {
      res = cci_next_result (req, &error);
      if (res == CAS_ER_NO_MORE_RESULT_SET)
	break;

      if (res < 0)
	{
	  fprintf (stdout, "Error:%d\n", error.err_code);
	  fprintf (fp, "Error:%d\n", error.err_code);
	  ret = -1;
	  goto _END;
	}

      res_col_info = cci_get_result_info (req, &cmd_type, &col_count);
      if (cmd_type == CUBRID_STMT_SELECT || cmd_type == CUBRID_STMT_CALL)
	{
	  dumptable (fp, req, conn, hasqueryplan);
	}
      else
	{
	  printf ("%d\n", res);
	  fprintf (fp, "%d\n", res);
	}
    }

_END:
  if (server_output_buffer)
    {
      fprintf (fp, server_output_buffer);
    }
  if (req > 0)
    cci_close_req_handle (req);
  return ret;
}

int
executebind (FILE * fp, char conn, char *sql1, char *sql2, bool hasqueryplan, bool iscall)
{
  static int seq = 0;
  int bnum = 0i, ind;
  int out_count = 0;
  int req = 0;
  int res = 0;
  int ret = 0;
  int execute_result = 0;
  T_CCI_ERROR error;
  int col_count = 0, t = 0;
  char *buffer;
  T_CCI_COL_INFO *res_col_info;
  T_CCI_SQLX_CMD cmd_type;
  char *server_output_buffer = NULL;

  fprintf (fp, "===================================================\n");

  if (iscall)
    {
      req = cci_prepare (conn, sql1, CCI_PREPARE_CALL, &error);
    }
  else
    {
      req = cci_prepare (conn, sql1, 0, &error);
    }

  if (req < 0)
    {
      fprintf (stdout, "Error:%d\n", error.err_code);
      fprintf (fp, "Error:%d\n", error.err_code);
      if (is_server_message_on)
	{
	  char *end = strstr (error.err_msg, "[CAS INFO");
	  if (end)
	    {
	      *end = '\0';
	    }
	  fprintf (fp, "%s\n\n", error.err_msg);
	}
      ret = -1;
      goto _END;
    }

  bnum = cci_get_bind_num (req);
  if (!executebindparameter (req, sql2, bnum))
    {
      fprintf (stdout, "bind parameter error\n");
      ret = -1;
      goto _END;
    }

  out_count = get_out_num_by_str (sql2);
  for (t = 1; t <= out_count; t++)
    {
      if ((cci_register_out_param (req, t)) < 0)
	{
	  fprintf (stdout, "register parameter error\n");
	  ret = -1;
	  goto _END;
	}
    }

  res = cci_execute (req, CCI_EXEC_QUERY_ALL, 0, &error);
  if (is_server_message_on)
    {
      server_output_buffer = get_server_output (fp, conn);
    }
  if (res < 0)
    {
      fprintf (stdout, "Error:%d\n", error.err_code);
      fprintf (fp, "Error:%d\n", error.err_code);
      if (is_server_message_on)
	{
	  char *end = strstr (error.err_msg, "[CAS INFO");
	  if (end)
	    {
	      *end = '\0';
	    }
	  fprintf (fp, "%s\n", error.err_msg);
	}
      ret = -1;
      goto _END;
    }

  if (iscall)
    {
      //print call statement column name
      for (t = 1; t <= out_count; t++)
	{
	  if (t == 1)
	    {
	      fprintf (fp, "%d", out_count);
	    }
	  else
	    {
	      fprintf (fp, " %d", out_count);
	    }
	}
      fprintf (fp, "\n");

      //print data
      res = cci_cursor (req, 1, CCI_CURSOR_CURRENT, &error);
      if (res == CCI_ER_NO_MORE_DATA)
	{
	  fprintf (fp, "Error:%d\n", error.err_code);
	  ret = -1;
	  goto _END;
	}

      if ((res = cci_fetch (req, &error) < 0))
	{
	  fprintf (fp, "Error:%d\n", error.err_code);
	  ret = -1;
	  goto _END;
	}

      if ((res = cci_get_data (req, 1, CCI_A_TYPE_STR, &buffer, &ind)) < 0)
	{
	  fprintf (fp, "Error:%d\n", error.err_code);
	  ret = -1;
	  goto _END;
	}
      // ind: string length, buffer: a string which came from the out binding parameter of test_out(?) Java SP.
      if (ind == -1)
	{
	  fprintf (fp, "null\n");
	}
      else
	{
	  fprintf (fp, "%s\n", buffer);
	}
      ret = -1;
      goto _END;
    }

  res_col_info = cci_get_result_info (req, &cmd_type, &col_count);
  if (cmd_type == CUBRID_STMT_SELECT || cmd_type == CUBRID_STMT_CALL)
    {
      dumptable (fp, req, conn, hasqueryplan);
    }
  else
    {
      fprintf (fp, "%d\n", res);
      while (1)
	{
	  res = cci_next_result (req, &error);
	  if (res == CAS_ER_NO_MORE_RESULT_SET)
	    break;
	  if (res < 0)
	    {
	      fprintf (stdout, "Error:%d\n", error.err_code);
	      fprintf (fp, "Error:%d\n", error.err_code);
	      ret = -1;
	      goto _END;
	    }
	  res_col_info = cci_get_result_info (req, &cmd_type, &col_count);
	  if (cmd_type == CUBRID_STMT_SELECT || cmd_type == CUBRID_STMT_CALL)
	    {
	      dumptable (fp, req, conn, hasqueryplan);
	    }
	  else
	    {
	      fprintf (fp, "%d\n", res);
	    }
	}
    }

_END:
  if (server_output_buffer)
    {
      fprintf (fp, server_output_buffer);
    }
  if (req > 0)
    cci_close_req_handle (req);
  return ret;
}

int
test (FILE * fp)
{
  int conn = -1, req = 0, res = 0;
  int sql_count = 0, length = 0, count = 0;
  int MAX_RETRY_COUNT = 50;
  T_CCI_ERROR error;

  //contect to the server until  success.
  while (conn < 0)
    {
      conn = cci_connect_with_url (url, user, passwd);
      if (conn > 0)
	{
	  break;
	}
      else if (conn < 0 && count >= MAX_RETRY_COUNT)
	{
	  fprintf (stdout, "(%s, %d) %s ERROR : cci_connect(%s %d)\n\n",
		   __FILE__, __LINE__, get_err_msg (conn), error.err_msg, error.err_code);
	  goto _END;
	}
      else
	{
	  fprintf (stdout, "CUBRID server is not available, waiting for recovery - (%s) \n", get_err_msg (conn));
	  sleep (5);
	}

      count++;
    }

  for (sql_count = 0; sql_count < total_sql; sql_count++)
    {
      if ((length = strlen (sqlstate[sql_count].sql)) <= 2)
	{
	  continue;
	}
      if (startswithCI (sqlstate[sql_count].sql, "AUTOCOMMIT OFF"))
	{
	  res = cci_set_autocommit (conn, CCI_AUTOCOMMIT_FALSE);
	  if (res < 0)
	    {
	      fprintf (stdout, "(%s, %d) %s ERROR : cci_connect(%s %d)\n\n",
		       __FILE__, __LINE__, get_err_msg (conn), error.err_msg, error.err_code);
	    }
	}
      else if (startswithCI (sqlstate[sql_count].sql, "AUTOCOMMIT ON"))
	{
	  res = cci_set_autocommit (conn, CCI_AUTOCOMMIT_TRUE);
	  if (res < 0)
	    {
	      fprintf (stdout, "(%s, %d) %s ERROR : cci_connect(%s %d)\n\n",
		       __FILE__, __LINE__, get_err_msg (conn), error.err_msg, error.err_code);
	    }
	}
      else if (startswithCI (sqlstate[sql_count].sql, "COMMIT"))
	{
	  res = cci_end_tran (conn, CCI_TRAN_COMMIT, &error);
	  if (res >= 0)
	    {
	      fprintf (fp, "===================================================\n");
	      fprintf (fp, "0\n");
	    }
	  else
	    {
	      fprintf (stdout, "(%s, %d) %s ERROR : cci_connect(%s %d)\n\n",
		       __FILE__, __LINE__, get_err_msg (conn), error.err_msg, error.err_code);
	    }
	}
      else if (startswithCI (sqlstate[sql_count].sql, "--+ SERVER-MESSAGE ON")
	       || startswithCI (sqlstate[sql_count].sql, "--+SERVER-MESSAGE ON"))
	{
	  set_server_message (fp, conn, 1);
	}
      else if (startswithCI (sqlstate[sql_count].sql, "--+ SERVER-MESSAGE OFF")
	       || startswithCI (sqlstate[sql_count].sql, "--+SERVER-MESSAGE OFF"))
	{
	  set_server_message (fp, conn, 0);
	}
      /*support --+ holdcas on; --+ holdcas off;
         2013.12.5 cn15209
         autocommit on;  <====> --+ holdcas off; <====>  CCI_CAS_CHANGE_MODE_AUTO
         autocommit off; <====> --+ holdcas on;  <====>  CCI_CAS_CHANGE_MODE_KEEP */
      else if (startswithCI (sqlstate[sql_count].sql, "--+ HOLDCAS ON")
	       || startswithCI (sqlstate[sql_count].sql, "--+HOLDCAS ON"))
	{
#ifdef CCI_SET_CAS_CHANGE_MODE_INTERFACE
	  //printf("%s\n",sqlstate[j].sql);
	  res = cci_set_cas_change_mode (conn, CCI_CAS_CHANGE_MODE_KEEP, &error);
	  if (res < 0)
	    {
	      fprintf (stdout, "(%s, %d) %s ERROR : cci_set_cas_change_mode(%s %d)\n\n",
		       __FILE__, __LINE__, get_err_msg (conn), error.err_msg, error.err_code);
	    }
#else
	  fprintf (stdout, "The program doesn't compile cci_set_cas_change_mode interface.\n");
#endif
	}
      else if (startswithCI (sqlstate[sql_count].sql, "--+ HOLDCAS OFF")
	       || startswithCI (sqlstate[sql_count].sql, "--+HOLDCAS OFF"))
	{
#ifdef CCI_SET_CAS_CHANGE_MODE_INTERFACE
	  res = cci_set_cas_change_mode (conn, CCI_CAS_CHANGE_MODE_AUTO, &error);
	  if (res < 0)
	    {
	      fprintf (stdout, "(%s, %d) %s ERROR : cci_set_cas_change_mode(%s %d)\n\n",
		       __FILE__, __LINE__, get_err_msg (conn), error.err_msg, error.err_code);
	    }
#else
	  fprintf (stdout, "The program doesn't compile cci_set_cas_change_mode interface.\n");
#endif
	}
      else if (startswithCI (sqlstate[sql_count].sql, "ROLLBACK"))
	{
	  char *p = NULL;
	  char *tmp = NULL;
	  int _len = 0;

	  p = sqlstate[sql_count].sql;

	  tmp = strcasestr (p, "to");
	  if (tmp != NULL && endswithsemicolon (tmp))
	    {
	      tmp[strlen (tmp) - 1] = 0;
	    }

	  if (tmp != NULL)
	    {
	      if (strcasestr (p, "savepoint") != NULL)
		{
		  _len = strlen ("to savepoint ");
		}
	      else
		{
		  _len = strlen ("to ");
		}

	      res = cci_savepoint (conn, CCI_SP_ROLLBACK, tmp + _len, &error);
	      if (res >= 0)
		{
		  fprintf (fp, "===================================================\n");
		  fprintf (fp, "0\n");
		}
	      else
		{
		  fprintf (stdout, "rollback error: %s\n", error.err_msg);
		}
	    }
	  else
	    {
	      res = cci_end_tran (conn, CCI_TRAN_ROLLBACK, &error);
	      if (res >= 0)
		{
		  fprintf (fp, "===================================================\n");
		  fprintf (fp, "0\n");
		}
	      else
		{
		  fprintf (stdout, "(%s, %d) %s ERROR : cci_connect(%s %d)\n\n",
			   __FILE__, __LINE__, get_err_msg (conn), error.err_msg, error.err_code);
		}
	    }
	}
      else if (startswithCI (sqlstate[sql_count].sql, "SAVEPOINT"))
	{
	  char *p = NULL;
	  p = sqlstate[sql_count].sql;
	  p = p + strlen ("savepoint ");
	  if (endswithsemicolon (p))
	    p[strlen (p) - 1] = 0;
	  res = cci_savepoint (conn, CCI_SP_SET, p, &error);
	  if (res >= 0)
	    {
	      fprintf (fp, "===================================================\n");
	      fprintf (fp, "0\n");
	    }
	  else
	    {
	      fprintf (stdout, "save point error: %s\n", error.err_msg);
	    }
	}
      else
	{
	  if (parameter[sql_count] != NULL)
	    {
	      executebind (fp, conn, sqlstate[sql_count].sql, parameter[sql_count], sqlstate[sql_count].hasqp,
			   sqlstate[sql_count].iscallwithoutvalue);
	    }
	  else
	    {
	      execute (fp, conn, sqlstate[sql_count].sql, sqlstate[sql_count].hasqp);
	    }
	}
    }

  //release
  if ((res = cci_disconnect (conn, &error)) < 0)
    {
      goto _END;
    }

  for (sql_count = 0; sql_count < total_sql; sql_count++)
    {
      if (sqlstate[sql_count].sql != NULL)
	free (sqlstate[sql_count].sql);
      if (parameter[sql_count] != NULL)
	free (parameter[sql_count]);
    }
  return 0;
_END:
  if (req > 0)
    cci_close_req_handle (req);
  if (conn > 0)
    res = cci_disconnect (conn, &error);
  for (sql_count = 0; sql_count < total_sql; sql_count++)
    {
      if (sqlstate[sql_count].sql != NULL)
	free (sqlstate[sql_count].sql);
      if (parameter[sql_count] != NULL)
	free (parameter[sql_count]);
    }
  return -1;
}

void
has_query_plan (char *casename)
{
  char *ret = NULL;

  if (casename == NULL)
    {
      queryplan = 0;
      exit (1);
    }

  int len = strlen (casename) + strlen (".queryPlan") - strlen (".sql") + 1;
  ret = (char *) malloc (len);
  memset (ret, 0, len);
  strncpy (ret, casename, strlen (casename) - strlen (".sql"));
  strcat (ret, ".queryPlan");
  if (access (ret, 0) != -1)
    {
      queryplan = 1;
    }
  free (ret);
}

int
cmp_result_files (char *a, char *b)
{
  FILE *fp1;
  FILE *fp2;
  int ch1, ch2;
  int ret = -1;

  fp1 = fopen (a, "r");
  fp2 = fopen (b, "r");

  if ((fp1 == NULL) || (fp2 == NULL))
    {
      printf ("Error in the file n");
    }
  else
    {
      ch1 = getc (fp1);
      ch2 = getc (fp2);
      while ((ch1 != EOF) && (ch2 != EOF) && (ch1 == ch2))
	{
	  ch1 = getc (fp1);
	  ch2 = getc (fp2);
	}

      if (ch1 == ch2)
	ret = 0;
      else if (ch1 != ch2)
	ret = 1;

      fclose (fp1);
      fclose (fp2);
    }
  return ret;
}

char *
getanswerfile (const char *casename)
{
  char *p1 = NULL;
  char *p2 = NULL;
  char *p = NULL;
  char *answer_str = NULL;
  char *ext_answer_str = NULL;
  int len_p1, len_p2, len;

  if (casename == NULL)
    return NULL;

  p1 = strstr (casename, "/cases/");
  p2 = p1 + strlen ("/cases/");
  len_p1 = p1 - casename;
  len_p2 = strlen (p2) - strlen ("sql");
  len = sizeof (char) * (len_p1 + len_p2 + strlen ("/answers/") + strlen ("answer") + 1);

  p = malloc (len);
  answer_str = p;

  memset (p, 0, len);
  p = strncpy (p, casename, len_p1);
  p += len_p1;
  p = strncpy (p, "/answers/", strlen ("/answers/"));
  p += strlen ("/answers/");
  p = strncpy (p, p2, len_p2);
  p += len_p2;
  p = strncpy (p, "answer", strlen ("answer"));

  ext_answer_str = malloc (strlen (answer_str) + strlen ("_cci") + 1);
  strcpy (ext_answer_str, answer_str);
  strcat (ext_answer_str, "_cci");
  if ((access (ext_answer_str, 0)) != -1)
    {
      free (answer_str);
      return ext_answer_str;
    }
  else
    {
      free (ext_answer_str);
      return answer_str;
    }
}

char *
getRelativeCasePath (char *filename)
{
  if (filename == NULL)
    return filename;

  char *res = NULL;
  int len = 0;
  char *pos = NULL;

  if (strstr (filename, "/scenario/"))
    {
      len = strlen ("/scenario");
      res = strstr (filename, "/scenario/");
    }
  else if (strstr (filename, "/cubrid-testcases-private/"))
    {
      len = strlen ("/cubrid-testcases-private");
      res = strstr (filename, "/cubrid-testcases-private/");
    }
  else if (strstr (filename, "/cubrid-testcases/"))
    {
      len = strlen ("/cubrid-testcases");
      res = strstr (filename, "/cubrid-testcases/");
    }

  res = res + len + 1;

  return res;
}

char *
merge_result_path (const char *result_path, char *res_path)
{
  int len = 0;
  char *new_path = NULL;
  char *res_ralative_path = NULL;

  res_ralative_path = getRelativeCasePath (res_path);

  len = strlen (result_path) + strlen ("/") + strlen (res_ralative_path);
  new_path = malloc (sizeof (char) * (len + 1));
  sprintf (new_path, "%s/%s", result_path, res_ralative_path);
  return new_path;
}

long
getCurrentTime ()
{
  struct timeval curTime;
  gettimeofday (&curTime, NULL);
  return ((long) curTime.tv_sec) * 1000 + (long) curTime.tv_usec / 1000;
}

char *
getlogname (char *casename)
{
  char *res = NULL;
  if (casename == NULL)
    return NULL;

  int len = strlen (casename) + strlen ("result") - strlen ("sql") + 1;
  res = (char *) malloc (len);
  memset (res, 0, len);
  res = strncpy (res, casename, strlen (casename) - 4);
  res = strcat (res, ".result");

  return res;
}

int
main (int argc, char *argv[])
{
  int sql_count;
  int rs = -1;
  char *result;
  char *ans_file = NULL;
  char *test_type = NULL;
  char *urlproperty = NULL;
  long start_time, end_time, elapse_time;
  if (argc < 4)
    {
      printf ("At least 4 paremeters.\n");
      return -1;
    }

  port = atoi (argv[1]);
  dbname = argv[2];
  test_type = argv[3];
  char *filename = argv[4];
  result = argv[5];
  urlproperty = argv[6];

  //construct the url as "cci:CUBRID:localhost:33888:test_db:dba:12345:"
  snprintf (url, 256, "cci:CUBRID:%s:%d:%s:%s:%s:%s", host, port, dbname, user, passwd,
	    urlproperty == NULL ? "" : urlproperty);

  printf ("Case Name:%s\n", filename);

  //initialise the sqlstate, which is store the sql statements.
  for (sql_count = 0; sql_count < MAX_SQL_NUM; sql_count++)
    {
      sqlstate[sql_count].sql = NULL;
      sqlstate[sql_count].hasqp = 0;
      parameter[sql_count] = NULL;
    }

  //get the sql statements from sql file.
  readFile (filename);

  //judge if this sql file have a queryplan file.
  has_query_plan (filename);

  //get the answer file path.
  answername = getanswerfile (filename);

  //construct a result file at the same dir as sql file.
  resname = getlogname (filename);
  if (resname == NULL)
    {
      return -1;
    }

  //open the sql file which will be test.
  FILE *fp = NULL;
  fp = fopen (resname, "w+");

  //begin to test the case by cci.
  start_time = getCurrentTime ();
  int ret = test (fp);
  end_time = getCurrentTime ();
  elapse_time = end_time - start_time;
  //end to test the case by cci.

  if (fp)
    {
      fclose (fp);
    }

  //start result log file
  init_log (result);
  rs = cmp_result_files (resname, answername);
  //write the compare result into summary file.
  char *case_file = getRelativeCasePath (filename);
  if (rs == 0)
    {
      fprintf (result_recorder, "TestCase: %s", case_file);
      fprintf (result_recorder, "\t%s:%ld\n", ":OK", elapse_time);
      printf ("Test Result: OK\n");
    }
  else
    {
      fprintf (result_recorder, "TestCase: %s", case_file);
      fprintf (result_recorder, "\t%s:%ld\n", ":NOK", elapse_time);
      char *result_p = NULL;
      char *pos = NULL;

      //mkdir folder for result, and create result file for failed cases
      result_p = merge_result_path (result, resname);
      mkdir_r (result_p, result);
      copyfile (resname, result_p);
      if (result_p != NULL)
	free (result_p);

      char sqllist[255] = { 0 };
      strcpy (sqllist, result);
      strcat (sqllist, "/");
      strcat (sqllist, case_file);
      copyfile (filename, sqllist);

      //mkdir folder for answer, and create result file for failed cases
      result_p = merge_result_path (result, answername);
      printf ("----answer name----: %s\n", answername);
      mkdir_r (result_p, result);
      copyfile (answername, result_p);
      if (result_p != NULL)
	free (result_p);
      printf ("Test Result: NOK\n");
    }

  close_log ();
  if (resname != NULL)
    free (resname);
  if (answername != NULL)
    free (answername);
  if (summarylog != NULL)
    free (summarylog);
  return ret;
}
