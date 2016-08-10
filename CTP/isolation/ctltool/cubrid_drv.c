/*
 * Copyright (C) 2008 Search Solution Corporation. All rights reserved by Search Solution.
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

#define _GNU_SOURCE

#include "db_drv.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <memory.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/param.h>
#include <setjmp.h>
#include <assert.h>

#include "dbi.h"
#include "common.h"

#define NULL_TRAN_INDEX (-1)

extern int tm_Tran_index;
extern int Client_no;

extern char *Program;

const char *Db_name;

extern void lock_dump (FILE * outfp);
extern bool tran_is_blocked (int tran_index);
extern char **help_class_names (const char *qualifier);
extern void help_free_class_names (char **all_classes);
extern void set_fprint (FILE * fp, DB_COLLECTION * set);


const char *Init_user_name = "public";
const char *Password = "";
const char *Db_server_addr = "localhost";

/* Output printing functions */
static int my_db_set_print (FILE * fp, DB_COLLECTION * set);
static void my_crs_printlist (FILE * fp, DB_QUERY_RESULT * result);
static void my_db_csql_debug_print_result (FILE * fp,
					   DB_QUERY_RESULT * result,
					   CUBRID_STMT_TYPE stmt_type);


/*
 * init_db_client						      
 *									      
 * arguments:					
 *									      
 * returns/side-effects: int			      
 *									      
 * description: 							      
 */
int
init_db_client ()
{
  return 0;
}

/*
 * get_tran_id						      
 *									      
 * arguments:					
 *									      
 * returns/side-effects: int			      
 *									      
 * description: 							      
 */
int
get_tran_id ()
{
  return tm_Tran_index;
}


/*
 * is_client_restarted						      
 *									      
 * arguments:				
 *									      
 * returns/side-effects: int, 1: yes, 0: no				      
 *									      
 * description: 							      
 */
int
is_client_restarted ()
{
  return (tm_Tran_index == NULL_TRAN_INDEX) ? 0 : 1;
}


/*
 * login_db						      
 *									      
 * arguments:									      
 *									      
 * returns/side-effects: int 					      
 *									      
 * description: 							      
 */
int
login_db (char *host, char *username, char *password, char *db_name)
{
  return db_login (username, password == NULL ? "" : password);
}

/*
 * error_message						      
 *									      
 * arguments:									      
 *									      
 * returns/side-effects: const char *					      
 *									      
 * description: 							      
 */
const char *
error_message ()
{
  return db_error_string (3);
}

/*
 * shutdown_db						      
 *									      
 * arguments:									      
 *									      
 * returns/side-effects: int 					      
 *									      
 * description: 							      
 */
int
shutdown_db (void)
{
  int error;
  char *env_val;

  error = db_shutdown ();
  /*
   * As a courtesy, also remove our errlog file if it is empty.
   */
  env_val = (char *) getenv ("CUBRID_ERROR_LOG");
  if (env_val)
    {
      struct stat stat_buf;

      if ((stat (env_val, &stat_buf) == 0) && stat_buf.st_size == 0)
	{
	  if (unlink (env_val) < 0)
	    {
	      fprintf (stderr,
		       "qacsql cleanup: unlink of error log file: %s failed, errno = %d\n",
		       env_val, errno);
	    }
	}
    }
  return error;
}

/*
 * clear_db_client						      
 *									      
 * arguments:								
 *									      
 * returns/side-effects: void 					      
 *									      
 * description: 							      
 */
void
clear_db_client_related ()
{
  char *env_val;

  /*
   * As a courtesy, also remove our errlog file if it is empty.
   */
  env_val = (char *) getenv ("CUBRID_ERROR_LOG");
  if (env_val)
    {
      struct stat stat_buf;

      if ((stat (env_val, &stat_buf) == 0) && stat_buf.st_size == 0)
	{
	  if (unlink (env_val) < 0)
	    {
	      fprintf (stderr,
		       "qacsql cleanup: unlink of error log file: %s failed, errno = %d\n",
		       env_val, errno);
	    }
	}
    }
}

/*
 * execute_sql_statement						      
 *									      
 * arguments:								      
 *	statement: statement to be executed				      
 *									      
 * returns/side-effects: int 					      
 *									      
 * description: 							      
 */
int
execute_sql_statement (FILE * fp, char *statement)
{
  int db_error = NO_ERROR;

  DB_SESSION *session;
  DB_QUERY_RESULT *query_result;
  int stmtid;
  CUBRID_STMT_TYPE stmt_type;

  session = db_open_buffer (statement);
  if (session == NULL)
    {
      return db_error_code ();
    }

  stmtid = db_compile_statement (session);
  if (stmtid < 0)
    {
      return db_error_code ();
    }

  stmt_type = db_get_statement_type (session, stmtid);

  db_error = db_execute_statement (session, stmtid, &query_result);
  if (db_error < 0)
    {
      return db_error_code ();
    }
  else if (stmt_type == CUBRID_STMT_UPDATE || stmt_type == CUBRID_STMT_DELETE)
    {
      fprintf (fp, "%d row%s %s\n", db_error, (db_error > 1 ? "s" : ""),
	       (stmt_type == CUBRID_STMT_SELECT ? "selected" : "affected"));
    }
  if (query_result != NULL)
    {
      if (stmt_type == CUBRID_STMT_SELECT || stmt_type == CUBRID_STMT_CALL)
	{
	  my_db_csql_debug_print_result (fp, query_result, stmt_type);
	}

      fprintf (fp, "%d row%s %s\n", db_error, (db_error > 1 ? "s" : ""),
	       (stmt_type == CUBRID_STMT_SELECT ? "selected" : "affected"));

      db_error = db_query_end (query_result);
      if (db_error != NO_ERROR)
	{
	  db_close_session (session);
	  return db_error;
	}
    }
  db_close_session (session);

  return NO_ERROR;
}

/*
 * is_executing_end							      
 *									      
 * arguments:								      
 *									      
 * returns/side-effects: int 					      
 *									      
 * description: 							      
 */
int
is_executing_end (int errid)
{
  return (errid == ER_IT_EMPTY_STATEMENT) ? 1 : 0;
}


/*
 * reconnect_to_server							      
 *									      
 * arguments:								      
 *									      
 * returns/side-effects: int 					      
 *									      
 * description: 							      
 */

int
reconnect_to_server (void)
{
#define MAX_SIZE_OTHER_COMMANDS 500
  int db_error = 0;

  int rval;

  db_error = db_restart (Program, 0, Db_name);

  if (db_error == ER_NET_CANT_CONNECT_SERVER)
    {

      char command[MAX_SIZE_OTHER_COMMANDS] = { 0 };

      strcpy (command, "cubrid server start ");
      strcat (command, Db_name);
      rval = system (command);
      if (rval)
	fprintf (stderr,
		 "qacsql: Line %d: system() failed on - %s, returned %08x\n",
		 __LINE__, command, rval);
      db_error = db_restart (Program, 0, Db_name);
    }

  return (db_error);

#undef MAX_SIZE_OTHER_COMMANDS
}


/*
 * print_class_info: print the table define.							      
 *									      
 * arguments:								      
 *									      
 * returns/side-effects: char * 					      
 *									      
 * description: 							      
 */
char *
print_class_info (char *cmd_ptr, FILE * file_handler)
{
#define CLASS_NAME_INFO 512

  char class_name[CLASS_NAME_INFO];
  char *temp_ptr;
  DB_OBJECT *class;		/* Class object to print contents */

  cmd_ptr += 6;
  skip_white_comments (&cmd_ptr);

  for (temp_ptr = class_name; (*cmd_ptr != ';'); *temp_ptr++ = *cmd_ptr++)
    ;

  *temp_ptr = '\0';

  /* User may want to see all class names "*", or class names of a
   * certain user ".*".  This syntax is also supported by
   * icsql et al.  Note: Even though the syntax is supported, the
   * output is a little different.
   */
  if (strchr (class_name, '*') != NULL)
    {
      char **all_classes = NULL;

      /* Send only the stuff before the '.' as the qualifier, if any */
      temp_ptr = strchr (class_name, '.');
      if (temp_ptr != NULL)
	{
	  *temp_ptr = '\0';
	}

      all_classes = help_class_names (class_name);
      if (all_classes != NULL)
	{
	  int x;

	  fprintf (file_handler, "Schema for %s classes:\n", class_name);
	  for (x = 0; all_classes[x] != NULL; x++)
	    {
	      fprintf (file_handler, "  %s\n", all_classes[x]);
	    }

	  help_free_class_names (all_classes);
	}
      else
	{
	  fprintf (file_handler,
		   "There do not seem to be any classes to show.\n");
	}

    }
  else
    {
      /* User wants to see a specific class */
      class = db_find_class (class_name);
      db_fprint (file_handler, class);
    }

  return cmd_ptr;

#undef CLASS_NAME_INFO
}

/*
 * print_ope			
 *						
 * arguments:					
 *	
 *						
 * returns/side-effects: void
 *						
 * description: 				
 */
void
print_ope (int num, const char *str)
{
  char ignore_line[200];
  static int trace_qacsql = -1;
  const char *env_value;

  fprintf (stdout, "Ope_no = %4d, Client_no = %d\n", num, Client_no);

  fprintf (stdout, "%s\n", str);

  if (trace_qacsql == -1)
    {
      /*
       * We don't known if the user that invoked the program wants to
       * control the execution. Find out..
       */
      env_value = getenv ("TRACE_QACSQL");
      trace_qacsql = ((env_value == NULL) ? 0 : atoi (env_value));
      if (trace_qacsql != 0)
	{
	  trace_qacsql = 1;
	}
    }

  if (trace_qacsql == 1)
    {
      fprintf (stdout, "Press return to execute the above operations\n");
      fflush (stdout);
      if (fgets (ignore_line, 200, stdin) == NULL)
	{
	  exit (100);
	}
    }

  fflush (stdout);
}


/*
 *       		 FUNCTIONS FOR PRINTING RESULTS
 */

/*
 * my_db_csql_debug_print_result						
 *									
 * arguments:	result - result to be displayed				
 *									
 * return/side-effect: void						
 *									
 * description:	display the result on standard output.			
 *									
 * NOTE: this function is only for DEBUGGING purpose. No product can	
 *	 this function to display the result.				
 */

static void
my_db_csql_debug_print_result (FILE * fp, DB_QUERY_RESULT * result,
			       CUBRID_STMT_TYPE stmt_type)
{
  if (result == NULL)
    {
      fprintf (fp, "There is no result.\n");
      return;
    }

  switch (stmt_type)
    {
    case CUBRID_STMT_SELECT:
    case CUBRID_STMT_CALL:
      my_crs_printlist (fp, result);
      break;

    default:
      fprintf (fp, "Invalid query result structure type: %d.\n", stmt_type);
      break;
    }
}

/*
 * my_crs_printlist								      
 *									      
 * arguments:								      
 *	result: resultset handle
 *									      
 * returns/side-effects: nothing					      
 *									      
 * description: Dump the content of the resultset to the standard output      
 */

static void
my_crs_printlist (FILE * fp, DB_QUERY_RESULT * result)
{
  DB_VALUE *value_list, *valp;
  int cnt, k;
  int pos;

  cnt = db_query_column_count (result);
  value_list = (DB_VALUE *) malloc (cnt * sizeof (DB_VALUE));
  if (value_list == NULL)
    {
      return;
    }

  fprintf (fp, "=================   Q U E R Y   R E S U L T S   "
	   "=================\n");
  fprintf (fp, "\n");

  pos = db_query_first_tuple (result);
  while (pos == DB_CURSOR_SUCCESS)
    {
      if (db_query_get_tuple_valuelist (result, cnt, value_list) != NO_ERROR)
	{
	  goto cleanup;
	}

      fprintf (fp, "\n ");

      for (k = 0, valp = value_list; k < cnt; k++, valp++)
	{
	  fprintf (fp, "  ");
	  if (DB_VALUE_TYPE (valp) == DB_TYPE_SET
	      || DB_VALUE_TYPE (valp) == DB_TYPE_MULTISET
	      || DB_VALUE_TYPE (valp) == DB_TYPE_SEQUENCE)
	    {
	      my_db_set_print (fp, DB_GET_SET (valp));
	    }
	  else
	    {
	      db_value_fprint (fp, valp);
	    }
	  fprintf (fp, "  ");
	}

      /* clear the value list */
      for (k = 0, valp = value_list; k < cnt; k++, valp++)
	{
	  db_value_clear (valp);
	}

      pos = db_query_next_tuple (result);
    }

  fprintf (fp, "\n");

cleanup:
  free (value_list);
}

/*
 * my_db_set_print								      
 *									      
 * arguments:								      
 *	set : set descriptor						      
 *									      
 * returns/side-effects: error						      
 *									      
 * description: Debugging function that prints a simple description of a set. 
 *    This should be used for information purposes only.  It doesn't 	      
 *    necessarily correspond to the CSQL syntax for set definition.	      
 */

static int
my_db_set_print (FILE * fp, DB_COLLECTION * set)
{
  /* allow all types */
  set_fprint (fp, set);
  return (NO_ERROR);
}

int
local_tm_isblocked (int tran_index)
{
  if (is_client_restarted ())
    {
      return (tran_is_blocked (tran_index));
    }
  else
    {
      return (0);
    }
}
