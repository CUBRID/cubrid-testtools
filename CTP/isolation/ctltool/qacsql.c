/*
 * qacsql.c - csql interpreter for QA
 *
 * Note:
 * This module is a superset of the csql interpreter. It provides several     
 * extensions for debugging purposes. For example, simulation statemants.     
 *                                                                            
 * TEST CLIENT COMMANDS                                                       
 *                                                                            
 *      Command: schema <class name> [<ignored>]                              
 *      Purpose: Displays the schema for the specified class.                 
 *                                                                            
 *      Command: quit                                                         
 *      Purpose: Stops the execution.                                        
 *                                                                            
 *      Command: save state by `<SQL command>` [<ignored>]                    
 *      Purpose: Records results of specified SQL command.                    
 *                                                                            
 *      Command: verify state unchanged                                       
 *      Purpose: Verifies results of previous specified SQL command matches   
 *      results recorded by a previous "save state by" command.               
 *                                                                            
 *      Command: verify state changed                                         
 *      Purpose: Verifies results of previous specified SQL command differs   
 *      results recorded by a previous "save state by" command.               
 *                                                                            
 *      Command: reconnect [<ignored>]                                        
 *      Purpose: Reconnects specified test client to the server.             
 *                                                                            
 *      Command: login as <database user> [<ignored>]                        
 *      Purpose: Invokes the db_login() API routine.                          
 *                                                                            
 *      Command: print [<comment>];                                         
 *      Purpose: Prints comment to client's output.                           
 */
#define _GNU_SOURCE

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

#include "db_drv.h"
#include "common.h"


extern char *get_next_stmt (FILE * fp);

#define PRINT_TOKEN "print "

#define MAX_SIZE_FOR_SAVE_STATE_CMD 500
#define MAX_SIZE_FOR_VERIFY_CMD     500
#define MAX_SIZE_OTHER_COMMANDS     500

#define VERIFY_NAMELEN              128

/* Parameter reading variables */
int Client_no = 0;
int Crash_case = 0;
char LocalHost[MAXHOSTNAMELEN];

const char *Inp_file_name = NULL;
int Skip_count = 0;
int VerifyFailedLines = 10;

int stmt_no = 0;
int cmd_cnt = 0;
FILE *inp_file = NULL;

char *Program;

extern char *Init_user_name;
extern const char *Password;
extern const char *Db_server_addr;
extern const char *Db_name;

static void save_state (char *file, char *commands);
static void verify_state (char *verify_command, int state_changed,
			  char *save_state_command, char *name);
static void copy_lines_of_file (FILE * outfp, int nlines, const char *name);




/*
 * save_state								      
 *									      
 * arguments:								      
 *	cmd_ptr: 							      
 *									      
 * returns/side-effects: nothing					      
 *									      
 * description: 							      
 */

static void
save_state (char *file, char *commands)
{
  FILE *file_handler;
  char *cmd_ptr;
  int db_error = NO_ERROR;

  if (strcasecmp (file, "stdout"))
    {
      /* Create temporary file */
      file_handler = fopen (file, "w");
      if (file_handler == NULL)
	{
	  fprintf (stdout, "Error in create file %s\n", file);
	}
    }
  else
    {
      file_handler = stdout;
    }


  /* INTERPRET THE STATEMENT */

  cmd_ptr = commands;
  /* CHECK IF THIS IS A TEST DRIVER COMMAND */

  if (strncasecmp (cmd_ptr, "schema", 6) == 0)
    {
      /* SCHEMA STATEMENT */
      cmd_ptr = print_class_info (cmd_ptr, file_handler);

    }
  else
    {
      /* THIS IS AN CSQL STATEMENT */

      /* execute the statement */
      db_error = execute_sql_statement (file_handler, cmd_ptr);
      if (db_error != NO_ERROR)
	{
	  fprintf (stdout, "save_state failed on the following"
		   " statement %s with int:%d \n", commands, db_error);
	}
    }

  if (strcasecmp (file, "stdout"))
    {
      fclose (file_handler);
    }
}

/*
 * verify_state								      
 *									      
 * arguments:								     
 *									      
 * returns/side-effects: nothing					      
 *									      
 * description: 							      
 */
static void
verify_state (char *verify_command, int state_changed,
	      char *save_state_command, char *name)
{
  int result;

  if (!verify_command)
    {
      /* diff returns number of chars different */
      return;
    }

  result = system (verify_command);
  if ((result == 0 && state_changed == 1)
      || (result != 0 && state_changed == 0))
    {

      fprintf (stdout, "/**********************************************"
	       "******************************\n");
      fprintf (stdout, "\nCHECK STATE FAILED ! The result of the "
	       "following statement has ");
      if (state_changed)
	{
	  fprintf (stdout, "NOT CHANGED\n *** Command: %s\n",
		   save_state_command);
	}
      else
	{
	  fprintf (stdout, "CHANGED\n *** Command: %s\n", save_state_command);
	}

      copy_lines_of_file (stdout, VerifyFailedLines, (const char *) name);

      fprintf (stdout, "**********************************************"
	       "******************************/\n");
      exit (1);
    }

}


static void
remove_save_verify_files (int cmd_cnt)
{
  int command_no;
  char file_name[32];

  for (command_no = 0; command_no < cmd_cnt; command_no++)
    {
      sprintf (file_name, "temp_c%d_%s_save_%d",
	       Client_no, LocalHost, command_no);
      remove (file_name);
      sprintf (file_name, "temp_c%d_%s_verify_%d",
	       Client_no, LocalHost, command_no);
      remove (file_name);
      sprintf (file_name, "temp_c%d_%s_sorted_save_%d",
	       Client_no, LocalHost, command_no);
      remove (file_name);
      sprintf (file_name, "temp_c%d_%s_sorted_verify_%d",
	       Client_no, LocalHost, command_no);
      remove (file_name);
      sprintf (file_name, "temp_c%d_%s_diff_%d",
	       Client_no, LocalHost, command_no);
      remove (file_name);
    }
}

/*
 *       	      MAIN: SQL INTERPRETER MAIN FUNCTION
 */

int
main (int argc, char *argv[])
{
  char s[8192];
  char save_state_cmd[12][MAX_SIZE_FOR_SAVE_STATE_CMD];
  char verify_command[12][MAX_SIZE_FOR_VERIFY_CMD];
  char user_name[64];
  char first_file[VERIFY_NAMELEN];
  char second_file[VERIFY_NAMELEN];
  char s_first_file[VERIFY_NAMELEN];
  char s_second_file[VERIFY_NAMELEN];
  char diff_file[VERIFY_NAMELEN];
  char *last_sq_ptr;		/* ptr to last single quote (') */

  int db_error = NO_ERROR;
  int command_no;		/* Number of save and verify state commands */
  char *cmd_ptr, *temp_ptr;
  const char *stmt;		/* stmt returned from CSQL interpreter */


  ARGDEF Arguments[] = {
    {"volume-name", ARG_STRING, NULL, (void *) &Db_name,
     "volume name, no default", NULL},
    {"input-file-name", ARG_STRING, "-i", (void *) &Inp_file_name,
     "input file", NULL},
    {"user-name", ARG_STRING, "-u", (void *) &Init_user_name, "user name",
     NULL},
    {"password", ARG_STRING, "-p", (void *) &Password, "password", NULL},
    {"client-number", ARG_INTEGER, "-cl", (void *) &Client_no,
     "client number", NULL},
    {"crash-case", ARG_INTEGER, "-crash", (void *) &Crash_case,
     "crash case no, default 0", NULL},
    {"skip-count", ARG_INTEGER, "-skip", (void *) &Skip_count,
     "skip counter, default 0", NULL},
    {NULL}
  };


  /* PARSE THE ARGUMENTS OF THE main */
//  if (args_parse_strings (Arguments, argc, argv, stdout, 0))
  if (args_parse_strings_new (Arguments, argc, argv))
    {
      printf ("parse error\n");
      return 1;
    }
#ifdef DEBUG
  else
    {
      printf ("parse ok\n");
      printf ("Db_name     = %s\n", Db_name);
      printf ("inp_file    = %s\n", Inp_file_name);
      printf ("user_name   = %s\n", Init_user_name);
      printf ("password    = %s\n", Password);
      printf ("client_no   = %s\n", Client_no);
      printf ("Crash_case  = %d\n", Crash_case);
      printf ("Skip_count  = %d\n", Skip_count);
    }
#endif


  init_db_client ();

  Program = argv[0];

  /* Determine the hostname of the machine we are runing on.  */
  if (gethostname (LocalHost, MAXHOSTNAMELEN) < 0)
    {
      fprintf (stderr, "init: Unable to determine local host name\n");
      exit (1);
    }

  db_error = reconnect_to_server ();
  if (db_error != NO_ERROR)
    {
      fprintf (stdout,
	       "Unable to restart the database; Got error: %d,\n %s\n",
	       db_error, error_message ());
      return (db_error);
    }

  db_error = login_db ((char *) Db_server_addr, (char *) Init_user_name,
		       (char *) Password, (char *) Db_name);
  if (db_error != NO_ERROR)
    {
      fprintf (stdout, "ERROR:%d in db_login as user %s\n", db_error,
	       Init_user_name);
      return (db_error);
    }

  if ((argc > 2) && Inp_file_name &&
      (strncasecmp (Inp_file_name, "stdin", 5)))
    {
      /* There is an input file */
      inp_file = fopen (Inp_file_name, "r");
      if (inp_file == NULL)
	{
	  fprintf (stdout, "unable to open input file\n");
	  shutdown_db ();
	  return (1);
	}

    }
  else
    {
      /* set interpreter input to standard input */
      inp_file = stdin;
    }

  /* PARSE TEST SCRIPT */

  /* get successive statements, will exit from the infinite loop with a
     break statement */
  for (;;)
    {

      db_error = 0;
      stmt_no++;

      /* Diagnose if the server halted, but no longer try to automatically
       * restart it.  This caused havoc with any unexpected server crashes
       * and for expected crashes (i.e. from the simulate command), the
       * script should be written to explicitly reconnect when necessary.
       */
      if (!is_client_restarted ())
	{
	  fprintf (stderr, "Client %d is not connected any longer, the server"
		   " seems to have died.  Try\nthe 'reconnect to server' command"
		   " to attempt to start a new connection.\n", Client_no);
	}

      if (inp_file == stdin)
	{
	  fprintf (stdout, "Client %d (Transaction index = %4d) is ready.\n",
		   Client_no, get_tran_id ());
	}

      if (fflush (stdout))
	{
	  printf ("Flush on stdout failed\n");
	}

      stmt = get_next_stmt (inp_file);
      if (stmt == NULL)
	{
	  /* NO more input; stop */
	  break;
	}

      /* copy to local memory */
      strcpy (s, stmt);

      if ((inp_file != NULL) && (Skip_count < 1))
	{
	  print_ope (stmt_no, s);
	}

      cmd_ptr = s;
      skip_white_comments (&cmd_ptr);

      /* May be removed */
      if (strlen (cmd_ptr) < 1)	/* done */
	{
	  break;
	}


      /* INTERPRET THE STATEMENT */

      /* CHECK IF THIS IS A TEST DRIVER COMMAND */


      /*
       * CHECK FOR QUIT
       */

      if (strncasecmp (cmd_ptr, "quit", 4) == 0)
	{
	  /*
	   * Finish...
	   * User wants to stop the execution; break
	   */
	  break;
	}

      /*
       * CHECK FOR SAVE STATE BY
       */

      if (strncasecmp (cmd_ptr, "save state by \'", 15) == 0)
	{
	  /* SAVE STATE */

	  /*
	   * Destroy any temporary file
	   */
	  if (cmd_cnt > 0)
	    {
	      remove_save_verify_files (cmd_cnt);
	    }

	  cmd_ptr += 15;
	  cmd_cnt = 0;
	  /* Be sure to allow embedded single quotes in the save commands! */
	  last_sq_ptr = strrchr (cmd_ptr, '\'');
	  if (last_sq_ptr == NULL)
	    {
	      fprintf (stdout,
		       "Syntax Error - save state command (= %s), missing closing single quote (')",
		       cmd_ptr);
	      continue;
	    }

	  do
	    {
	      for (temp_ptr = cmd_ptr;
		   ((*temp_ptr != ';') && (temp_ptr < last_sq_ptr));
		   temp_ptr++)
		;

	      if (*temp_ptr == ';')
		{
		  temp_ptr++;
		  /* Clear the area before copying the next statement */
		  memset (save_state_cmd[cmd_cnt], ' ',
			  MAX_SIZE_FOR_SAVE_STATE_CMD);
		  /* save the next command including the semicolon */
		  memcpy (save_state_cmd[cmd_cnt], cmd_ptr,
			  temp_ptr - cmd_ptr);
		  save_state_cmd[cmd_cnt][temp_ptr - cmd_ptr] = '\0';
		  cmd_cnt++;
		}

	      skip_white_comments (&temp_ptr);
	      cmd_ptr = temp_ptr;
	    }
	  while (temp_ptr < last_sq_ptr);


	  for (command_no = 0; command_no < cmd_cnt; command_no++)
	    {
	      /* NOTE: Keep these two file names of the same length.
	       *       It was otherwise causing a bug in this prog.
	       */
	      sprintf (first_file, "temp_c%d_%s_save_%d",
		       Client_no, LocalHost, command_no);
	      sprintf (second_file, "temp_c%d_%s_verify_%d",
		       Client_no, LocalHost, command_no);
	      sprintf (s_first_file, "temp_c%d_%s_sorted_save_%d",
		       Client_no, LocalHost, command_no);
	      sprintf (s_second_file, "temp_c%d_%s_sorted_verify_%d",
		       Client_no, LocalHost, command_no);
	      sprintf (diff_file, "temp_c%d_%s_diff_%d",
		       Client_no, LocalHost, command_no);


	      /* This is an ordinary (not multimedia) save statement */

	      sprintf (verify_command[command_no],
		       "sort %s > %s; sort %s > %s; diff %s %s > %s",
		       first_file, s_first_file, second_file,
		       s_second_file, s_first_file, s_second_file, diff_file);

	      if (Skip_count < 1)
		{
		  save_state (first_file, save_state_cmd[command_no]);
		}
	    }
	  continue;
	}


      /*
       * CHECK FOR SIMULATES
       */

      if (strncasecmp (cmd_ptr, "simulate", 8) == 0)
	{
	  if (Skip_count > 0)
	    {
	      Skip_count--;
	    }

	  continue;
	}

      if (Skip_count <= 0)
	{			/* DONT SKIP */
	  /*
	   * CHECK FOR VERIFY
	   */

	  if (strncasecmp (cmd_ptr, "verify", 6) == 0)
	    {
	      if (strncasecmp (cmd_ptr, "verify state unchanged;", 23) == 0)
		{
		  /* VERIFY STATEMENT */

		  for (command_no = 0; command_no < cmd_cnt; command_no++)
		    {
		      sprintf (second_file, "temp_c%d_%s_verify_%d",
			       Client_no, LocalHost, command_no);

		      save_state (second_file, save_state_cmd[command_no]);
		      verify_state (verify_command[command_no], 0,
				    save_state_cmd[command_no], second_file);
		    }

		  if (command_no)
		    {
		      printf ("State Verified\n");
		    }
		  else
		    {
		      printf ("There is no command to verify with\n");
		    }
		}
	      else if (strncasecmp (cmd_ptr, "verify state changed;", 21) ==
		       0)
		{
		  /* VERIFY STATEMENT */

		  for (command_no = 0; command_no < cmd_cnt; command_no++)
		    {
		      sprintf (second_file, "temp_c%d_%s_verify_%d",
			       Client_no, LocalHost, command_no);

		      save_state (second_file, save_state_cmd[command_no]);
		      verify_state (verify_command[command_no], 1,
				    save_state_cmd[command_no], second_file);
		    }
		  if (command_no)
		    {
		      printf ("State Verified\n");
		    }
		  else
		    {
		      printf ("There is no command to verify with\n");
		    }
		}
	      else
		{
		  printf ("Syntax error unknown verify command: %s", cmd_ptr);
		}
	      continue;
	    }

	  /*
	   * CHECK FOR SCHEMA
	   */

	  if (strncasecmp (cmd_ptr, "schema", 6) == 0)
	    {
	      /* SCHEMA STATEMENT */
	      print_class_info (cmd_ptr, stdout);

	      continue;
	    }

	  /*
	   * CHECK FOR RECONNECT
	   */
	  if (strncasecmp (cmd_ptr, "reconnect", 9) == 0)
	    {
	      /* RECONNECT STATEMENT */
	      if (argc < 3)
		{
		  inp_file = stdin;
		}

	      db_error = reconnect_to_server ();
	      if (db_error != NO_ERROR)
		{
		  fprintf (stdout, "Unable to reconnect to server."
			   " Got error:%d,\n %s\n",
			   db_error, error_message ());
		  goto cleanup;
		}

	      continue;
	    }

	  /*
	   * CHECK FOR PRINT
	   */
	  if (strncasecmp (cmd_ptr, PRINT_TOKEN, strlen (PRINT_TOKEN)) == 0)
	    {
	      fprintf (stdout, cmd_ptr);
	      continue;
	    }

	  /*
	   * CHECK FOR LOGIN AS.
	   *
	   * We should change the script to
	   * call login ('USR', 'PWD') on class db_user
	   */
	  if (strncasecmp (cmd_ptr, "login as", 8) == 0)
	    {
	      /* LOGIN STATEMENT */
	      fprintf (stdout, " %s should be changed to"
		       " call login ('USR', 'PWD') on class db_user",
		       cmd_ptr);
	      cmd_ptr += 8;
	      skip_white_comments (&cmd_ptr);

	      if (*cmd_ptr != '\'')
		{
		  fprintf (stdout,
			   "Single qoute is expected before user name\n");
		  db_error = 1;
		  goto cleanup;
		}

	      cmd_ptr++;

	      for (temp_ptr = user_name;
		   (*cmd_ptr != ';') && (*cmd_ptr != '\'');
		   *temp_ptr++ = *cmd_ptr++)
		;
	      *temp_ptr = '\0';

	      login_db ((char *) Db_server_addr, user_name, (char *) Password,
			(char *) Db_name);

	      continue;
	    }

	  /* THIS IS AN CSQL STATEMENT */

	  /* execute the statement */
	  db_error = execute_sql_statement (stdout, cmd_ptr);
	  if (db_error != NO_ERROR)
	    {
	      if (is_executing_end (db_error))
		{
		  break;
		}

	      /* error found */
	      fprintf (stdout,
		       "ERROR RETURNED: %s \n   on statement number: %d\n",
		       error_message (), stmt_no);
	      /* Continue processing after noticing an error */
	    }

	  /* add Cx: commit; after set transaction isolation level repeatable read */
	  char  *set_isolation_level_repeatable_read = "set transaction isolation level ";
	  if(!strncasecmp(cmd_ptr, set_isolation_level_repeatable_read,32))
	  	{
		  sprintf (cmd_ptr, "COMMIT;");

		  db_error = execute_sql_statement (stdout, cmd_ptr);
		  if (db_error != NO_ERROR)
			{
			  if (is_executing_end (db_error))
			{
			  break;
			}

			  /* error found */
			  fprintf (stdout,
				   "ERROR RETURNED: %s \n   on statement number: %d\n",
				   error_message (), stmt_no);
			  /* Continue processing after noticing an error */
			}
	     }

	}

      if (fflush (stdout))
	{
	  printf ("Flush on stdout failed\n");
	}
    }


cleanup:

  shutdown_db ();
  printf ("This client shutting down as commanded.\n");

  if ((argc > 2) && Inp_file_name &&
      (strncasecmp (Inp_file_name, "stdin", 5)))
    {
      /* There is an input file */
      fclose (inp_file);
    }

  /*
   * Destroy any temporary file
   */
  if (cmd_cnt > 0)
    {
      remove_save_verify_files (cmd_cnt);
    }

  clear_db_client_related ();
  return (db_error);
}


/*
 * copy_lines_of_file()                                                     
 *                                                                          
 * arguments:								    
 *                                                                          
 * returns/side-effects: 						    
 *                                                                          
 * description:								    
 *   Called to copy a number of lines from one file to an open file STREAM. 
 *                                                                          
 */
static void
copy_lines_of_file (FILE * outfp, int nlines, const char *name)
{
  char buffer[1024];
  FILE *infp;
  int i = 1;

  infp = fopen (name, "r");
  if (infp == NULL)
    {
      fprintf (outfp,
	       "WARNING - Copy failed, unable to open input file %s, errno = %d\n",
	       name, errno);
      return;
    }

  fprintf (outfp, " *** Copy of first %d lines of file %s\n", nlines, name);
  while ((nlines > 0) && (fgets (buffer, 1024, infp) != 0))
    {
      fprintf (outfp, " *** %d : %s", i++, buffer);
      nlines--;
    }
}
