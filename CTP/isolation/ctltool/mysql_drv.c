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

#include <mysql.h>
#include <assert.h>
#include <string.h>

#include "common.h"

#define NULL_TRAN_INDEX (-1)

typedef enum mysql_stmt_type MYSQL_STMT_TYPE;
enum mysql_stmt_type
{
  MYSQL_STMT_UNKNOWN,
  MYSQL_STMT_INSERT,
  MYSQL_STMT_SELECT,
  MYSQL_STMT_UPDATE,
  MYSQL_STMT_DELETE,
  MYSQL_STMT_CALL
};

extern int Client_no;

const char *Init_user_name = "root";
const char *Password = "";
const char *Db_server_addr = "localhost";
const char *Db_name;

MYSQL Mysql;
int Mysql_thread_id = 0;


static MYSQL_STMT_TYPE mysql_get_statement_type (char *statement);
static void mysql_result_set_print (FILE * fp, MYSQL_RES * res);

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
  mysql_init (&Mysql);
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
  return Mysql_thread_id;
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
  const char *server_stat = NULL;

  server_stat = mysql_stat (&Mysql);
  return (server_stat == NULL) ? 0 : 1;
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
  if (mysql_real_connect (&Mysql, host, username, password, db_name, 0, NULL,
			  0) == NULL)
    {
      return mysql_errno (&Mysql);
    }

  if (mysql_autocommit (&Mysql, 0) != NO_ERROR)
    {
      return mysql_errno (&Mysql);
    }

  Mysql_thread_id = mysql_thread_id (&Mysql);

  return NO_ERROR;
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
  return mysql_error (&Mysql);
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
  mysql_close (&Mysql);
  return 0;
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
  /* do nothing */
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
#define SET_TRANSACTION_LOCK_TIMEOUT_STRING "SET TRANSACTION LOCK TIMEOUT "
#define INFINITE_STRING "INFINITE"
#define OFF "OFF"
#define MYSQL_LOCK_WAIT_TIMEOUT_STRING "set session innodb_lock_wait_timeout=%s;"
#define MYSQL_INFINITE_STRING "1073741824"
#define MYSQL_SET_STMT_LENGTH 100

#define SET_TRANSACTION_ISOLATION_LEVEL_STRING "SET TRANSACTION ISOLATION LEVEL "
#define MYSQL_SET_TRANSACTION_ISOLATION_LEVEL_STRING "set session transaction isolation level %s;"

  int db_error = NO_ERROR;
  MYSQL_RES *res;
  unsigned long long num_rows = 0;
  MYSQL_STMT_TYPE stmt_type;
  char *arg_p = NULL;
  int length = 0;
  char *timeout_str = NULL;
  char set_statement_of_mysql[MYSQL_SET_STMT_LENGTH];

  /* special translation for "SET TRANSACTION ISOLATION LEVEL" statement */
  length = strlen (SET_TRANSACTION_ISOLATION_LEVEL_STRING);
  arg_p = strcasestr (statement, SET_TRANSACTION_ISOLATION_LEVEL_STRING);
  if (arg_p != NULL)
    {
      arg_p += length;

      sprintf (set_statement_of_mysql,
	       MYSQL_SET_TRANSACTION_ISOLATION_LEVEL_STRING, arg_p);

      statement = set_statement_of_mysql;
    }

  /* special translation for "SET TRANSACTION LOCK TIMEOUT" statement */
  length = strlen (SET_TRANSACTION_LOCK_TIMEOUT_STRING);
  arg_p = strcasestr (statement, SET_TRANSACTION_LOCK_TIMEOUT_STRING);
  if (arg_p != NULL)
    {
      arg_p += length;

      /* MySQL timeout range is 1 ... 1073741824 */
      if (strcasestr (arg_p, INFINITE_STRING) != NULL)
	{
	  timeout_str = MYSQL_INFINITE_STRING;
	}
      else if (strcasestr (arg_p, OFF) != NULL)
	{
	  timeout_str = "1";
	}
      else
	{
	  timeout_str = arg_p;
	}

      sprintf (set_statement_of_mysql, MYSQL_LOCK_WAIT_TIMEOUT_STRING,
	       timeout_str);

      statement = set_statement_of_mysql;
    }


  Mysql_thread_id = mysql_thread_id (&Mysql);

  db_error = mysql_query (&Mysql, statement);
  if (db_error != NO_ERROR)
    {
      return db_error;
    }

  stmt_type = mysql_get_statement_type (statement);
  if (stmt_type == MYSQL_STMT_UPDATE || stmt_type == MYSQL_STMT_DELETE
      || stmt_type == MYSQL_STMT_INSERT)
    {
      num_rows = (unsigned long long) mysql_affected_rows (&Mysql);
      fprintf (fp, "%d row%s affected\n", (int) num_rows,
	       (num_rows > 1 ? "s" : ""));
    }
  else if (stmt_type == MYSQL_STMT_SELECT || stmt_type == MYSQL_STMT_CALL)
    {
      res = mysql_store_result (&Mysql);
      if (res == NULL)
	{
	  return mysql_errno (&Mysql);
	}

      mysql_result_set_print (fp, res);

      num_rows = (unsigned long long) mysql_num_rows (res);
      fprintf (fp, "%d row%s selected\n", (int) num_rows,
	       (num_rows > 1 ? "s" : ""));

      mysql_free_result (res);
    }

  return NO_ERROR;

#undef SET_TRANSACTION_LOCK_TIMEOUT_STRING
#undef INFINITE_STRING
#undef OFF
#undef MYSQL_LOCK_WAIT_TIMEOUT_STRING
#undef MYSQL_INFINITE_STRING
#undef MYSQL_TIMEOUT_LENGTH
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
  return 0;
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
  mysql_close (&Mysql);

  mysql_init (&Mysql);

  return 0;
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

  char command_buffer[CLASS_NAME_INFO + 10];
  MYSQL_RES *res;
  int num_fields = 0;
  MYSQL_ROW row;
  unsigned long *lengths;

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
      /* Send only the stuff before the '.' as the qualifier, if any */
      temp_ptr = strchr (class_name, '.');
      if (temp_ptr != NULL)
	{
	  *temp_ptr = '\0';
	}

      res = mysql_list_tables (&Mysql, NULL);
      if (res == NULL)
	{
	  fprintf (file_handler, "Error in geting Mysql table: %s",
		   mysql_error (&Mysql));
	}

      row = mysql_fetch_row (res);
      if (row == NULL)
	{
	  fprintf (file_handler,
		   "There do not seem to be any classes to show.\n");
	  return NULL;
	}

      num_fields = mysql_num_fields (res);
      if (num_fields != 1)
	{
	  fprintf (file_handler,
		   "There should only be one colume for table name.\n");
	  return NULL;
	}

      while (row != NULL)
	{
	  fprintf (file_handler, "\n ");

	  lengths = mysql_fetch_lengths (res);
	  if (lengths[0] != 0)
	    {
	      sprintf (command_buffer, "DESC %s;", row[0]);
	      execute_sql_statement (file_handler, command_buffer);
	    }

	  row = mysql_fetch_row (res);
	}
    }
  else
    {
      sprintf (command_buffer, "DESC %s;", class_name);
      execute_sql_statement (file_handler, command_buffer);
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

  fprintf (stdout, "Ope_no = %4d, Client_no = %d\n", num, Client_no);

  fprintf (stdout, "%s\n", str);
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
 * mysql_get_statement_type			
 *						
 * arguments:					
 *	statement: statement to be executed	
 *						
 * returns/side-effects: MYSQL_STMT_TYPE 	
 *						
 * description: 				
 */
static MYSQL_STMT_TYPE
mysql_get_statement_type (char *statement)
{
#define STMT_INSERT_HEADER "insert"
#define STMT_SELECT_HEADER "select"
#define STMT_UPDATE_HEADER "update"
#define STMT_DELETE_HEADER "delete"
#define STMT_CALL_HEADER   "call"

  assert (statement != NULL);

  if (strncasecmp (statement, STMT_INSERT_HEADER, strlen (STMT_INSERT_HEADER))
      == 0)
    {
      return MYSQL_STMT_INSERT;
    }
  else if (strncasecmp (statement, STMT_SELECT_HEADER,
			strlen (STMT_SELECT_HEADER)) == 0)
    {
      return MYSQL_STMT_SELECT;
    }
  else if (strncasecmp (statement, STMT_UPDATE_HEADER,
			strlen (STMT_UPDATE_HEADER)) == 0)
    {
      return MYSQL_STMT_UPDATE;
    }
  else if (strncasecmp (statement, STMT_DELETE_HEADER,
			strlen (STMT_DELETE_HEADER)) == 0)
    {
      return MYSQL_STMT_DELETE;
    }
  else if (strncasecmp (statement, STMT_CALL_HEADER,
			strlen (STMT_CALL_HEADER)) == 0)
    {
      return MYSQL_STMT_CALL;
    }
  return MYSQL_STMT_UNKNOWN;

#undef STMT_INSERT_HEADER
#undef STMT_SELECT_HEADER
#undef STMT_UPDATE_HEADER
#undef STMT_DELETE_HEADER
#undef STMT_CALL_HEADER

}


/*
 * mysql_result_set_print			
 *						
 * arguments:	
 *	fp:	  output file handler.
 *	res:	  result which return by mysql server 
 *						
 * returns/side-effects: void 	
 *						
 * description: 				
 */
static void
mysql_result_set_print (FILE * fp, MYSQL_RES * res)
{
  int num_fields = 0;
  int i = 0;
  MYSQL_ROW row;
  unsigned long *lengths;

  assert (fp != NULL);
  assert (res != NULL);

  fprintf (fp, "=================   Q U E R Y   R E S U L T S   "
	   "=================\n");
  fprintf (fp, "\n");

  row = mysql_fetch_row (res);
  if (row == NULL)
    {
      return;
    }

  num_fields = mysql_num_fields (res);
  while (row != NULL)
    {
      fprintf (fp, "\n ");

      lengths = mysql_fetch_lengths (res);
      for (i = 0; i < num_fields; i++)
	{
	  fprintf (fp, "%s\t", lengths[i] > 0 ? row[i] : "NULL");

	  fprintf (fp, "  ");
	}

      row = mysql_fetch_row (res);
    }
  fprintf (fp, "\n");
}

/*
 * local_tm_isblocked                                                         
 *                                                                            
 * arguments:                                                                 
 *      tran_index:     Server transaction index id.                          
 *                                                                            
 * description:                                                               
 *    Wrapper for server function tm_isblocked.  When the server is down,     
 * we don't bother to try to check the client.  In this case we always return 
 * a 0.                                                                   
 */
int
local_tm_isblocked (int tran_index)
{
#define MYSQL_TRX_QUERY						      \
  "SELECT trx.trx_mysql_thread_id, trx.trx_id, trx.trx_state,	      \
trx.trx_started, trx.trx_query,				      \
blocking.trx_mysql_thread_id AS blocking_mysql_thread_id,	      \
waits.blocking_trx_id AS blocking_trx_id,			      \
blocking.trx_state AS blocking_trx_state,			      \
blocking.trx_started AS blocking_started,			      \
blocking.trx_query AS blocking_query				      \
FROM information_schema.INNODB_TRX trx			      \
JOIN information_schema.INNODB_LOCK_WAITS waits		      \
ON trx.trx_id = waits.requesting_trx_id			      \
JOIN (SELECT trx_mysql_thread_id, trx_id, trx_state, trx_started, trx_query \
FROM information_schema.INNODB_TRX) blocking			      \
ON blocking.trx_id = waits.blocking_trx_id			      \
where trx.trx_mysql_thread_id=%d;"

#define MYSQL_TRX_QUERY_MAX_LEN 1024

#define MYSQL_TRX_INFO_HEADER "trx_mysql_thread_id,\t trx_id,\t trx_state,\t trx_started,\t trx_query,\t blocking_mysql_thread_id,\t blocking_trx_id,\t blocking_trx_state,\t blocking_started,\t blocking_trx_query\n"

  MYSQL_RES *res;
  unsigned long long num_rows = 0;
  int db_error = 0;
  int blocked = 0;
  char query_buffer[MYSQL_TRX_QUERY_MAX_LEN] = { 0 };

  if (tran_index == NULL_TRAN_INDEX)
    {
      return 0;
    }

  sprintf (query_buffer, MYSQL_TRX_QUERY, tran_index);
  db_error = mysql_query (&Mysql, query_buffer);
  if (db_error != NO_ERROR)
    {
      fprintf (stderr, "ERROR : %s\n", mysql_error (&Mysql));
      return 0;
    }

  res = mysql_store_result (&Mysql);
  if (res == NULL)
    {
      fprintf (stderr, "ERROR : %s\n", mysql_error (&Mysql));
      return 0;
    }

  num_rows = (unsigned long long) mysql_num_rows (res);
  if (num_rows == 0)
    {
      blocked = 0;
    }
  else
    {
      blocked = 1;

#if 0
      printf (MYSQL_TRX_INFO_HEADER);

      num_fields = mysql_num_fields (res);
      row = mysql_fetch_row (res);
      while (row != NULL)
	{
	  lengths = mysql_fetch_lengths (res);
	  for (i = 0; i < num_fields; i++)
	    {
	      printf ("%s\t", lengths[i] > 0 ? row[i] : "NULL");
	    }

	  printf ("\n");
	  row = mysql_fetch_row (res);
	}
#endif
    }

  mysql_free_result (res);

  return blocked;

#undef MYSQL_TRX_QUERY
#undef MYSQL_TRX_QUERY_MAX_LEN
#undef MYSQL_TRX_INFO_HEADER
}
