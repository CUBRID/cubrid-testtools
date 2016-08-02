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
 
#include "db_drv.h"

#include <string>
#include <iostream>
#include <assert.h>
#include <vector>
#include <regex.h>
#include <occi.h>

#include "common.h"

using namespace std;
using namespace oracle::occi;

#define NULL_TRAN_INDEX (-1)

enum oracle_stmt_type
{
  ORACLE_STMT_UNKNOWN,
  ORACLE_STMT_INSERT,
  ORACLE_STMT_SELECT,
  ORACLE_STMT_UPDATE,
  ORACLE_STMT_DELETE,
  ORACLE_STMT_CALL
};

typedef enum oracle_stmt_type ORACLE_STMT_TYPE;

extern int Client_no;

const char *Init_user_name = "test";
const char *Password = "test1234";
const char *Db_server_addr = "localhost:1521/orcl";
const char *Db_name;

Environment *Oracle_env = NULL;
Connection *Oracle_conn = NULL;
int Sid = NULL_TRAN_INDEX;

static int oracle_result_set_print (FILE * fp, ResultSet *rs);
static ORACLE_STMT_TYPE oracle_get_statement_type (char *statement);

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
init_db_client()
{  
  Oracle_env = Environment::createEnvironment();
  if (Oracle_env == NULL)
    {
      printf ("Environment::createEnvironment failed.\n");
      return ER_FAILED;
    }
    
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
get_tran_id()
{
  return Sid;
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
is_client_restarted()
{ 
  return (Sid == NULL_TRAN_INDEX) ? 0 : 1 ;
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
#define QUERY_SID_STRING "select sid from v$mystat where rownum=1"

  Statement *stmt;
  ResultSet *result;
    
  Oracle_conn = Oracle_env->createConnection(username, password, host);   
  if (Oracle_conn == NULL)
    {
      printf ("Error: createConnection failed.\n");
      return ER_FAILED;
    }
   
  /* all the sid are the same, so we add rownum=1 in SQL statement */  
  stmt = Oracle_conn->createStatement(QUERY_SID_STRING);
  if (stmt == NULL)
    {
      printf ("Error: createStatement failed.\n");
      return ER_FAILED;
    }
    
  result = stmt->executeQuery();      
  if (result == NULL)
    {
      printf ("Error: executeQuery failed.\n");
      return ER_FAILED;
    }

  result->next();
  Sid = result->getInt(1);  
  
  stmt->closeResultSet(result);
  Oracle_conn->terminateStatement(stmt);
     
  return 0;

#undef QUERY_SID_STRING  
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
  /* error message has been outputed in execute_sql_statement as exceptions */
  return "";
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
  if (Oracle_env != NULL)
    {
      if (Oracle_conn != NULL)
	{
	  Oracle_env->terminateConnection(Oracle_conn);
	  Oracle_conn = NULL;
	}
	
      Environment::terminateEnvironment(Oracle_env);
      Oracle_env = NULL;  
    }
    
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
clear_db_client_related()
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
#define SET_TRANSACTION_ISOLATION_LEVEL_STRING "SET TRANSACTION ISOLATION LEVEL "
#define ORACLE_SET_TRANSACTION_ISOLATION_LEVEL_STRING "ALTER SESSION SET ISOLATION_LEVEL = %s;"
#define SERIALIZABLE_STRING "SERIALIZABLE"
#define REPEATABLE_READ_STRING "REPEATABLE READ"
#define READ_COMMITTED_STRING "READ COMMITTED"
#define DROP_TABLE_IF_EXISTS_STRING "DROP TABLE IF EXISTS "
#define DROP_TABLE_STRING "DROP TABLE %s"
#define CREATE_TABLE_STRING "CREATE TABLE "
#define ROWDEPENDENCIES " ROWDEPENDENCIES"
#define ENABLE_ROW_MOVE " ENABLE ROW MOVEMENT"

#define ORACLE_SET_STMT_LENGTH 1000

  Statement *stmt;
  ResultSet *results;
  int error = 0;
  int length;
  ORACLE_STMT_TYPE stmt_type;
  int num_rows = 0;
  char *arg_p = NULL;
  char set_statement_of_oracle[ORACLE_SET_STMT_LENGTH];
  bool is_drop_table_if_exists = FALSE;

  regex_t reg;
  regmatch_t pm[10];
  int z =0;
  const size_t nmatch = 10;
  char * create_partition_pattern = "create table .* partition .*";
  char * list_partition_pattern = "create table .* partition by list .* values in .*";
  char * select_partition_table = "select .* from [a-zA-z]+__p__.* .*";
  char * range_partition_pattern = "create table .* partition by range.*";
  char * sleep_pattern = "select sleep";
  char * mod_pattern = "([A-Za-z]+)%([0-9]+)";
    
  if (Oracle_conn == NULL)
    {
      fprintf (fp, "Connection to Oracle Server was not established \n");
      return ER_FAILED;    
    }
    
  /* special translation for "SET TRANSACTION ISOLATION LEVEL" statement */
  length = strlen (SET_TRANSACTION_ISOLATION_LEVEL_STRING);
  arg_p = strcasestr (statement, SET_TRANSACTION_ISOLATION_LEVEL_STRING);
  if (arg_p != NULL)
    {
      arg_p += length;

      if (strcasestr (arg_p, SERIALIZABLE_STRING) != NULL
	  || strcasestr (arg_p, REPEATABLE_READ_STRING) != NULL)
	{
	  sprintf (set_statement_of_oracle,
		   ORACLE_SET_TRANSACTION_ISOLATION_LEVEL_STRING,
		   SERIALIZABLE_STRING);
	}
      else if (strcasestr (arg_p, READ_COMMITTED_STRING) != NULL)
	{
	  sprintf (set_statement_of_oracle,
		   ORACLE_SET_TRANSACTION_ISOLATION_LEVEL_STRING,
		   READ_COMMITTED_STRING);
	}
      else
	{
	  fprintf (fp, "Unsupported transaction isolation level. \n");
	  return ER_FAILED;
	}

      statement = set_statement_of_oracle;
    }

  /* find if there is a "SET TRANSACTION LOCK TIMEOUT" statement */
  length = strlen (SET_TRANSACTION_LOCK_TIMEOUT_STRING);
  arg_p = strcasestr (statement, SET_TRANSACTION_LOCK_TIMEOUT_STRING);
  if (arg_p != NULL)
    {
      /* Oracle does not support set transaction lock timeout, so nothing will
       * be done here.
       */
      return NO_ERROR;
    }

  /* find if there is a "DROP TABLE IF EXISTS " statement */
  length = strlen (DROP_TABLE_IF_EXISTS_STRING);
  arg_p = strcasestr (statement, DROP_TABLE_IF_EXISTS_STRING);
  if (arg_p != NULL)
    {
      arg_p += length;
      sprintf (set_statement_of_oracle, DROP_TABLE_STRING, arg_p);
      statement = set_statement_of_oracle;

      is_drop_table_if_exists = TRUE;
    }

  /* there is only one sql in statement, we need to remove the semicolon ';' 
   * at the end of statement for ORACLE 
   */
  length = strlen (statement);
  while (length >= 0 && statement[length] != ';')
    {
      /* skip, do nothing */
      length--;
    }

  if (length >= 0 && statement[length] == ';') 
    {
      /* remove the semicolon ';' */
      statement[length] = '\0';
    }
    
  /* find if there is a "CREATE TABLE " statement */    
  arg_p = strcasestr (statement, CREATE_TABLE_STRING);
  if (arg_p != NULL)
    {
      sprintf (set_statement_of_oracle, "%s%s INITRANS 100", arg_p, ROWDEPENDENCIES);
      statement = set_statement_of_oracle;
    }    


  /* find if there is a "CREATE TABLE xx PARTITION" statement */
  z = regcomp(&reg, create_partition_pattern, REG_ICASE);
  z = regexec(&reg, statement, nmatch, pm, 0);
  if(z == 0){
	  sprintf (set_statement_of_oracle, "%s%s ", statement, ENABLE_ROW_MOVE);
	  statement = set_statement_of_oracle;
  }

  /* find if there is a "CREATE TABLE xx PARTITION BY LIST" statement */
  z = regcomp(&reg, list_partition_pattern, REG_ICASE);
  z = regexec(&reg, statement, nmatch, pm, 0);
  if(z == 0){
	 string s = (string)statement;

	 string FindWord = "values in";
	 string ReplaceWord = "values";
	 size_t index;

	 while ((index = s.find(FindWord)) != std::string::npos)
		 s.replace(index, FindWord.length(), ReplaceWord);

	 sprintf (set_statement_of_oracle, "%s ", s.c_str());
	 statement = set_statement_of_oracle;
  }

  /* find if there is a "select * from t__p__p1" statement */
  z = regcomp(&reg, select_partition_table, REG_EXTENDED);
  z = regexec(&reg, statement, nmatch, pm, 0);
  if(z == 0){
	 string s = (string)statement;

	 string FindWord = "t__p__p1";
	 string ReplaceWord = "t PARTITION(p1)";
	 size_t index;

	 while ((index = s.find(FindWord)) != std::string::npos)
		 s.replace(index, FindWord.length(), ReplaceWord);

	 FindWord = "t__p__p2";
	 ReplaceWord = "t PARTITION(p2)";

	 while ((index = s.find(FindWord)) != std::string::npos)
		 s.replace(index, FindWord.length(), ReplaceWord);

	 sprintf (set_statement_of_oracle, "%s ", s.c_str());
	 statement = set_statement_of_oracle;
  }

  /* find if there is a range partition */
  z = regcomp(&reg, range_partition_pattern, REG_EXTENDED);
  z = regexec(&reg, statement, nmatch, pm, 0);
  if(z == 0){
	  string s = (string)statement;

	  string FindWord = "MAXVALUE";
	  string ReplaceWord = "(MAXVALUE)";
	  size_t index;
	  if ((index = s.find(FindWord)) > 0)
	  {
		 if (index != -1){
			 s.replace(index, FindWord.length(), ReplaceWord);
			 sprintf (set_statement_of_oracle, "%s ", s.c_str());
			 statement = set_statement_of_oracle;
		 }
	  }
  }

  /* find if there is a select sleep pattern */
  z = regcomp(&reg, sleep_pattern, REG_EXTENDED);
  z = regexec(&reg, statement, nmatch, pm, 0);
  if(z == 0){
	 string s = (string)statement;

	 string FindWord = "select sleep";
	 string ReplaceWord = "sleep";
	 size_t index;

	 while ((index = s.find(FindWord)) != std::string::npos)
		 s.replace(index, FindWord.length(), ReplaceWord);

	 sprintf (set_statement_of_oracle, "%s ", s.c_str());
	 statement = set_statement_of_oracle;
  }

  /* find if there is a column%number pattern */
  z = regcomp(&reg, mod_pattern, REG_EXTENDED);
  z = regexec(&reg, statement, 10, pm, 0);
  if(z == 0){
	 string s = (string)statement;

	 char *numerator, *denominator;
	 int len;

	 len = pm[1].rm_eo - pm[1].rm_so;
	 numerator = (char *) malloc ((len + 1) * sizeof (char));
	 strncpy (numerator , statement + pm[1].rm_so, len);
	 numerator[len] = 0;

	 len = pm[2].rm_eo - pm[2].rm_so;
	 denominator = (char *) malloc ((len + 1) * sizeof (char));
	 strncpy (denominator , statement + pm[2].rm_so, len);
	 denominator[len] = 0;

	 char FindWordC[8];
	 strcat(FindWordC, numerator);
	 strcat(FindWordC, "%");
	 strcat(FindWordC, denominator);
	 string FindWord = (string)FindWordC;

	 char ReplaceWordC[11];
	 strcat(ReplaceWordC, "mod(");
	 strcat(ReplaceWordC, numerator);
	 strcat(ReplaceWordC, ",");
	 strcat(ReplaceWordC, denominator);
	 strcat(ReplaceWordC, ")");
	 string ReplaceWord = (string)ReplaceWordC;

	 size_t index;

	 while ((index = s.find(FindWord)) != std::string::npos)
		 s.replace(index, FindWord.length(), ReplaceWord);

	 sprintf (set_statement_of_oracle, "%s ", s.c_str());
	 statement = set_statement_of_oracle;
  }


  try 
    {
      stmt = Oracle_conn->createStatement(statement);
      if (stmt == NULL)
	{
	  fprintf (fp, "Error: failed to createStatement. \n");
	  return ER_FAILED;
	}
	
      stmt->setAutoCommit(FALSE);  
      
      stmt_type = oracle_get_statement_type (statement);
      if (stmt_type == ORACLE_STMT_SELECT)
	{
	  results = stmt->executeQuery();
  	
	  num_rows = oracle_result_set_print(fp, results);
	  fprintf (fp, "%d row%s selected\n", (int) num_rows,
		    (num_rows > 1 ? "s" : ""));

	  stmt->closeResultSet(results);  		    
  	}  
      else
	{
	  num_rows = stmt->executeUpdate();
  	
	  fprintf (fp, "%d row%s affected\n", (int) num_rows,
		  (num_rows > 1 ? "s" : ""));
	}
	 
      Oracle_conn->terminateStatement(stmt);
    }
  catch (SQLException &oraex) 
    {  
      if (!is_drop_table_if_exists)
	{
	  error = oraex.getErrorCode();
	  /* Oracle/OCCI errors */
	  string errmsg = oraex.getMessage();  
	  fprintf (fp, "Error: %s\n", errmsg.c_str ());  
	}
      
    }  
  catch (exception &ex) 
    {  
      /* any other C++/STL error */
      fprintf (fp, "Error: %s\n", ex.what());
      error = ER_FAILED;
    }      

  return error;
  
#undef SET_TRANSACTION_LOCK_TIMEOUT_STRING
#undef SET_TRANSACTION_ISOLATION_LEVEL_STRING
#undef ORACLE_SET_TRANSACTION_ISOLATION_LEVEL_STRING 
#undef SERIALIZABLE_STRING
#undef REPEATABLE_READ_STRING
#undef READ_COMMITTED_STRING
#undef DROP_TABLE_IF_EXISTS_STRING
#undef DROP_TABLE_STRING
#undef CREATE_TABLE_STRING
#undef ROWDEPENDENCIES
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
is_executing_end(int errid)
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
  if (Oracle_env != NULL)
    {
      if (Oracle_conn != NULL)
	{
	  Oracle_env->terminateConnection(Oracle_conn);
	  Oracle_conn = NULL;
	}
	
      Environment::terminateEnvironment(Oracle_env);
      Oracle_env = NULL;  
    }
 
  Oracle_env = Environment::createEnvironment();
  if (Oracle_env == NULL)
    {
      printf ("Environment::createEnvironment failed.\n");
      return ER_FAILED;
    }

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

      /* need a OCCI API to find all the tables, because QA doesn't need the 
       * the class information, we currently don't implement it. 
       */	
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
 * oracle_get_statement_type			
 *						
 * arguments:					
 *	statement: statement to be executed	
 *						
 * returns/side-effects: ORACLE_STMT_TYPE 	
 *						
 * description: 				
 */
static ORACLE_STMT_TYPE
oracle_get_statement_type (char *statement)
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
      return ORACLE_STMT_INSERT;
    }
  else if (strncasecmp (statement, STMT_SELECT_HEADER,
			strlen (STMT_SELECT_HEADER)) == 0)
    {
      return ORACLE_STMT_SELECT;
    }
  else if (strncasecmp (statement, STMT_UPDATE_HEADER,
			strlen (STMT_UPDATE_HEADER)) == 0)
    {
      return ORACLE_STMT_UPDATE;
    }
  else if (strncasecmp (statement, STMT_DELETE_HEADER,
			strlen (STMT_DELETE_HEADER)) == 0)
    {
      return ORACLE_STMT_DELETE;
    }
  else if (strncasecmp (statement, STMT_CALL_HEADER,
			strlen (STMT_CALL_HEADER)) == 0)
    {
      return ORACLE_STMT_CALL;
    }
  return ORACLE_STMT_UNKNOWN;

#undef STMT_INSERT_HEADER
#undef STMT_SELECT_HEADER
#undef STMT_UPDATE_HEADER
#undef STMT_DELETE_HEADER
#undef STMT_CALL_HEADER

}


/*
 * oracle_result_set_print			
 *						
 * arguments:	
 *	fp:	  output file handler.
 *	res:	  result which return by oracle server 
 *						
 * returns/side-effects: int 	
 *						
 * description: 				
 */
static int
oracle_result_set_print (FILE * fp, ResultSet *results)
{
  vector<MetaData> cols;
  int num_fields;
  int i, num_rows;
  ResultSet::Status status;

  assert (fp != NULL);
  
  fprintf (fp, "=================   Q U E R Y   R E S U L T S   "
	   "=================\n");
  fprintf (fp, "\n");
  
  if (results == NULL)
    {
      return 0;
    }
  
  status = results->next();
  if (status == ResultSet::END_OF_FETCH)
    {
      return 0;
    }   
  
  cols = results->getColumnListMetaData();
  num_fields = cols.size();
  if (num_fields > 0)
    {      
      num_rows = 0;
      while (status != ResultSet::END_OF_FETCH) 
	{ 
	  fprintf (fp, "\n");

	  i = 1;
	  while (i <= num_fields)
	    {
	      fprintf (fp, "%s\t", 
		results->isNull(i) ? "NULL" : results->getString(i).c_str());
	      fprintf (fp, "  ");
    	  
	      i++;
	    }   
	  
	  num_rows++;
	  status = results->next();	    
	}
    }

  fprintf (fp, "\n");
  
  return num_rows;
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
#define ORACLE_TRX_QUERY				\
  "select LOCKWAIT from  v$session where sid=%d"

#define ORACLE_TRX_QUERY_MAX_LEN 1024

  int blocked = 0;
  char query_buffer[ORACLE_TRX_QUERY_MAX_LEN] = { 0 };
  Statement *stmt;
  ResultSet *results;
  int error = 0;
  string lock_wait;
  ResultSet::Status status;
  
  
  if (tran_index == NULL_TRAN_INDEX)
    {
      return 0;
    }
 
 sprintf (query_buffer, ORACLE_TRX_QUERY, tran_index);
 
 if (Oracle_conn == NULL)
    {
      printf ("Connection to Oracle Server was not established \n");
      return 0;    
    }    
    
  try 
    {
      stmt = Oracle_conn->createStatement(query_buffer);
      if (stmt == NULL)
	{
	  printf ("Error: failed to createStatement. \n");
	  return ER_FAILED;
	}
	
      stmt->setAutoCommit(TRUE);  
      results = stmt->executeQuery();
      if (results != NULL)
	{
	  status = results->next();
	  if (status != ResultSet::END_OF_FETCH)
	    {
	      lock_wait = results->getString(1);
	      if (lock_wait.length() > 0)
		{
		  blocked = 1;
		}
	    }       	
          
	  stmt->closeResultSet(results); 
	}     
         
      Oracle_conn->terminateStatement(stmt);
    }
  catch (SQLException &oraex) 
    {  
      error = oraex.getErrorCode();
      /* Oracle/OCCI errors */
      string errmsg = oraex.getMessage();  
      printf ("Error: %s\n", errmsg.c_str ());
    }  
  catch (exception &ex)  
    {  
      /* any other C++/STL error */
      printf ("Error: %s\n", ex.what());
      error = ER_FAILED;
    }      

  return blocked;

#undef ORACLE_TRX_QUERY
#undef ORACLE_TRX_QUERY_MAX_LEN
}


