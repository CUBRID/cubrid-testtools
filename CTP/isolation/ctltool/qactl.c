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

#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <errno.h>
#include <sys/signal.h>
#include <string.h>
#include <unistd.h>
#include <netdb.h>
#include <netinet/in.h>
#include <setjmp.h>
#include <syslog.h>
#include <assert.h>
#include <sys/select.h>
#include <stdarg.h>
#include <ctype.h>
#include <limits.h>

#include "db_drv.h"

#if defined (CUBRID)
#include "dbi.h"
#endif

#include "common.h"
#include "qamccom.h"
#include "parse.h"


extern int error;
extern int parse_line_num;
extern char *Init_user_name;
extern const char *Password;
extern const char *Db_server_addr;
extern const char *Db_name;

extern int local_tm_isblocked (int tran_index);

#define MASTER_TIMEOUT_VALUE_IN_SECONDS       0
#define MASTER_TIMEOUT_VALUE_IN_MICROSECONDS  500
#define STRESSGEN_CMDNAME   "mc_gen.sh"
#define STRESSEXEC_CMDNAME  "mc_exec.sh"


#define CLIENT_READ_BUFFER_SIZE   8192
#define COMMAND_BUFFER_SIZE       8192
#define BONUS_CLIENT_CNT          10

/* Major commands */
#define DEADLOCK_PAUSE_TOKEN "pause for deadlock resolution;"
#define WAIT_TOKEN       "wait until c"
#define RENDEZVOUS_TOKEN "rendezvous with"
#define EXECUTE_TOKEN    "execute"
#define ALLOC_CLIENT_TOKEN "allocate client"
#define NOOP_TOKEN       "no-op"
#define SLEEP_TOKEN "sleep"

/* tokens within major commands */
#define WAIT_BLOCKED_TOKEN "blocked;"
#define WAIT_UNBLOCKED_TOKEN "unblocked;"
#define WAIT_READY_TOKEN "ready;"
#define WAIT_FINISH_TOKEN "finished;"
#define TDELAY_TOKEN      "delay"
#define STRESSGEN_TOKEN   STRESSGEN_CMDNAME
#define STRESSEXEC_TOKEN  STRESSEXEC_CMDNAME

#define SLEEP_MSECS 10
#define SHORT_DURATION_DEFAULT (30000)	/*  30 seconds */
#define LONG_DURATION_DEFAULT (300000)	/* 300 seconds */

#define VALID_CLIENT_ID(TBL, ID)  (((ID) > 0) && ((ID) <= (TBL)->slotsinuse))

#define WAIT_USAGE_FORMAT \
"command := %s<client ID> { blocked | unblocked | ready | finished };\n"


#define DEADLOCK_DURATION 3	/* In seconds */

  /* The number of arguments we can pass on to our children */
#define MAX_XTRA_ARGS                     100

/* New Proc Stuff */
#define PROC_GROWTH_STEP          10
#define GetProcInfo(Tbl, Id)      ((Tbl)->allprocs[(Id)-1].procinfo)
#define GetProcUserData(Tbl, Id)  ((Tbl)->allprocs[(Id)-1].userdata)
#define SetProcUserData(Tbl, Id, Dat) \
                       ((Tbl)->allprocs[(Id)-1].userdata = (void *)(Dat))
#define GetProcState(Tbl, Id)     ((Tbl)->allprocs[(Id)-1].status)
#define SetProcState(Tbl, Id, St) ((Tbl)->allprocs[(Id)-1].status = (St))
#define SetProcExitStatus(Tbl, Id, Val)  \
                       ((Tbl)->allprocs[(Id)-1].exitstatus = (Val))
#define GetProcExitStatus(Tbl, Id) ((Tbl)->allprocs[(Id)-1].exitstatus)

/* Define known client states, from pre-initialization, to completion */
typedef enum
{ PRE_INIT, READY, RUNNING, BLOCKED,
  FINISHED, OTHER_MC
} PRO_STATUS;

/* Define client types.  These affect which output processing routines
 * are invoked, etc.
 */
typedef enum
{ CL_UNKNOWN, CL_GENERIC, CL_QACSQL, CL_STRESSEXEC } CL_TYPE;

typedef struct procinfo_s *PROCINFO_PTR;

typedef struct procinfo_s
{
  int id;			/* cardinal client number */
  PROCINFO_PTR left;		/* doubly-linked list pointers to immed. neighbors */
  PROCINFO_PTR right;
  int pid;			/* Process ID of the client process */
  int tran_index;		/* Transaction index (or, identifier) of client */
  int statement_count;		/* Number of statements to process, before ready */
  CL_TYPE client_type;		/* define a class of client types */
  int (*cl_output_filter) ();	/* function called to read from that client */
  int write_fd;			/* fd to write to the client process via pipe */
  int read_fd;			/* fd to read from the client process via pipe */
  int error_fd;			/* fd to get errors of the client process via pipe */
} PROCINFO;

typedef struct
{
  PRO_STATUS status;		/* keep track of last known client state */
  PROCINFO_PTR procinfo;	/* ptr to rest of information related to PROC */
  void *userdata;		/* supplemental data, if needed */
  int exitstatus;		/* exit status returned by waitpid() */
} PROCSLOT;

typedef struct
{
  PROCSLOT *allprocs;		/* find procs index list, given id -> get PROCINFO */
  PROCINFO_PTR active;		/* active list for running PROCS */
  PROCINFO_PTR free;		/* free list for unused PROCs */
  int num_active;		/* number currently on the active list */
  int slotsinuse;		/* number used */
  int maxslots;			/* number malloc-ed */
} PROCTBL;

/* For slave mode, define a structure which contains all
 * relevant information regarding our parent, the super
 * controller, along with how to communicate to it.
 */
struct super_info
{
  char *hostname;		/* the host name of the meta controller */
  char *socketname;		/* the primary socket of the meta ctlr */
  int sock;			/* the local file descriptor */
  struct hostent hostent;	/* the host entry for the host name */
  struct sockaddr_in other_in;	/* socket address info for remote sock */
};

/* For some fatal errors, we have to abort prematurely and do a
 * longjump back to main()s cleanup routines.  All reasons must be
 * greater than 0 (because of longjmp).
 */
enum longjmp_reasons
{
  FATAL_MEMORY_OVERFLOW = 1,
  FATAL_SEND_FAILURE,
  PARENT_CONTROLLER_FORCED_SHUTDOWN,
  PARENT_CONTROLLER_EXITED,
  PARENT_RECV_ERROR,
  PROCTBL_INCONSISTENCY,
  CONTROLLER_SIGTERM
};


/* Define various types for the "userdata" field of the PROCSLOT.
 * For now, there is only one kind, but eventually there will be more.
 */
typedef enum
{ UD_UNKNOWN, UD_STRESSDATA } UD_TYPE;

/* Structure stores information about the MCStressGen execution.  It
 * is used by the MCStressExec execution.
 */
struct stress_data
{
  UD_TYPE udtype;		/* Every user data type must have this first */
  int didgen;
  int numclients;
  int numfiles;
  char templatename[PATH_MAX];
  char runfilename[PATH_MAX];
};


static int num_alive_clients (PROCTBL * procTbl);
static void kill_clients (PROCTBL * procTbl);
static void kill_aclient (PROCTBL * procTbl, int id);
static void client_not_responding (PROCTBL * procTbl, int client_no);

static int service_clients (PROCTBL * procTbl);

static void wait_blocked_command (int client_id, PROCTBL * procTbl,
				  char command_buffer[COMMAND_BUFFER_SIZE],
				  unsigned int *status);
static void wait_unblocked_command (int client_id, PROCTBL * procTbl,
				    char command_buffer[COMMAND_BUFFER_SIZE],
				    unsigned int *status, int internal);
static void wait_ready_command (int client_id, PROCTBL * procTbl,
				char command_buffer[COMMAND_BUFFER_SIZE],
				unsigned int *status, int internal);
static void wait_command (char *cmd_ptr, PROCTBL * procTbl,
			  int start_line,
			  char command_buffer[COMMAND_BUFFER_SIZE],
			  unsigned int *status);
static void wait_finish_command (int client_id, PROCTBL * procTbl,
				 char command_buffer[COMMAND_BUFFER_SIZE],
				 unsigned int *status, int internal);

static void rendezvous_command (char *cmd_ptr, PROCTBL * procTbl,
				int start_line, char command_buffer[],
				unsigned int *status);
static PROCINFO *start_process (PROCTBL * procTbl,
				const char *executable_name,
				const char **argv, int tdelay);
static int control_clients (PROCTBL * procTbl, const char *program_name,
			    const char *db_name, FILE * inp_file);
static int client_init (const char *inp_file_name, FILE ** inp_file,
			PROCTBL * procTbl, char *added_args[]);
static int master_timeout (PROCTBL * procTbl);

static int slave_mode_init (char **file, struct super_info *si);
static int slave_mode_cleanup (char *file, struct super_info *si);
static int qactl_output (FILE * fp, const char *fmt, ...);
static int all_blocked (PROCTBL * procTbl);
static void stopandstart_handler (int sig);
static void sigterm_handler (int sig);
static int copy_xtra_args (int argc, char *argv[], ARGDEF arguments[],
			   char *xtra_args[]);

static int make_argvfromstring (const char *arg_ptrs[], char *ptr);
static void execute_command (char *cmd_ptr, PROCTBL * procTbl,
			     int start_line,
			     char command_buffer[COMMAND_BUFFER_SIZE],
			     unsigned int *status);
static int exec_stressgen (PROCTBL * procTbl, char *cmd_ptr, int start_line,
			   int delay);
static int exec_stressexec (PROCTBL * procTbl, char *cmd_ptr, int start_line,
			    int delay);

/* New PROCTBL stuff */
static PROCTBL *initialize_procinfo_table (PROCTBL * pt);
static void free_procinfo_table (PROCTBL * pt);
static PROCINFO *allocate_proc (PROCTBL * pt);
static int deallocate_proc (PROCTBL * pt, PROCINFO * proc);
void dump_procinfo_table (PROCTBL * pt);
static int activate_proc (PROCTBL * pt, PROCINFO * proc);
static int deactivate_proc (PROCTBL * pt, PROCINFO * proc);
static int pll_add (PROCINFO ** proc_ll, PROCINFO * proc);
static PROCINFO *pll_drop (PROCINFO ** proc_ll, PROCINFO * specific);

static int qacsql_output_filter (PROCTBL * procTbl, PROCINFO * proc);
static int generic_output_filter (PROCTBL * procTbl, PROCINFO * proc);

static void free_arguments (ARGDEF * arguments);

/* COMMAND LINE OPTIONS AND/OR ARGUMENTS */
int ctlr_mode = 0;
int master_id;
char LocalHost[QAMC_MAXHOSTNAMELEN];
int sync_mode = 1;		/* only matters in slave mode */
const char *ClientProgName = NULL;
const char *db_name;
int ShortDuration = SHORT_DURATION_DEFAULT;
int LongDuration = LONG_DURATION_DEFAULT;
PROCTBL qa_ProcTbl;		/* keep track of spawned children */
int Client_no = 0;
char *Program;

/* OTHER GLOBALS */
		     /* only 1 in sync mode and if we have TOKEN */
int HaveToken = 0;
struct super_info superi;
jmp_buf main_env;
		     /* Store msgs from super in the order of arrival */
struct qamc_fifoq_s *FifoQ = NULL;
		     /* access to the next msg from super */
qamc_msg *LastMsg = NULL;



static void
sleepms (double milliseconds)
{
#if defined (WINDOWS)
  Sleep ((int) milliseconds);
#else /* WINDOWS */
  struct timeval to;

  to.tv_sec = (int) (milliseconds / 1000);
  to.tv_usec = ((int) (milliseconds * 1000)) % 1000000;

  select (0, NULL, NULL, NULL, &to);
#endif /* WINDOWS */
}


/*
 * rendezvous_command                                                         
 *                                                                            
 * arguments:                                                                 
 *      cmd_ptr:        ptr to input command string after word "rendezvous".  
 *      procTbl:        Table of all client data.                             
 *      start_line:     Line number of input command.                         
 *	command_buffer: The complete command string.                          
 *	status:         Zero = ok, non-zero = failure.                        
 * returns/side-effects:                                                      
 *	Returns 0                                                             
 * description:                                                               
 *	Waits for duration specified by ShortDuration for                     
 *	synchronizing event from super controller.  The syntax is             
 *	"rendezvous with super;" leaving in the possibility that in the       
 *	future the master controllers might be able to rendezvous with        
 *	each other or with other programs. For now, we ignore the rest        
 *	of the command.                                                       
 */

static void
rendezvous_command (char *cmd_ptr, PROCTBL * procTbl,
		    int start_line, char command_buffer[],
		    unsigned int *status)
{
  int client_active = 0;
  int mseconds_elapsed;

  *status = 0;

  /* Pretend to parse the "with super;" part of the command, for
   * now there is no other possibility.
   */

  for (mseconds_elapsed = 0;
       mseconds_elapsed < ShortDuration && !LastMsg;
       mseconds_elapsed += SLEEP_MSECS)
    {
      client_active = service_clients (procTbl);
      sleepms (SLEEP_MSECS);
    }

  /* Do extended wait here (follow wait logic) */
  if (mseconds_elapsed >= ShortDuration)
    {
      qactl_output (stdout, "WARNING! No rendezvous after waiting %d"
		    " seconds.\n => %s.\n", ShortDuration, command_buffer);
      /* Wait for duration specified by LongDuration 
         for super controller rendezvous. */
      for (mseconds_elapsed = 0;
	   mseconds_elapsed < LongDuration
	   && client_active && !LastMsg; mseconds_elapsed += SLEEP_MSECS)
	{
	  client_active = service_clients (procTbl);
	  sleepms (SLEEP_MSECS);
	}
      if (mseconds_elapsed >= LongDuration)
	{
	  *status = 1;
	  qactl_output (stdout, "ERROR! No rendezvous after waiting %d more"
			" seconds.\n"
			" Verify client needs more time to finish processing.\n"
			"If client finished processing then check qacsql for problems.\n"
			"=> %s.\n", (LongDuration / 1000), command_buffer);
	}
    }

  if (LastMsg && (LastMsg->msgtype == QAMC_SMSG_CONTINUE))
    {
      LastMsg = qamc_remove_fifoq (FifoQ);
    }
  else
    {
      if (LastMsg)
	{
	  qactl_output (stderr,
			"ERROR! rendezvous command did not get SMSG_CONTINUE, got %d, %s instead\n",
			LastMsg->msgtype, LastMsg->msg);
	}
      *status = 1;		/* Some other message came from super */
    }
}

/*
 * wait_blocked_command                                                       
 *                                                                            
 * arguments:                                                                 
 *      client_id:      Cardinal client id number.                            
 *      procTbl:        Table of all client data.                             
 *      command_buffer: Entire CSQL statement.                                
 *	status:         Zero = ok, non-zero = failure.                        
 * returns/side-effects:                                                      
 *	Returns 0                                                             
 * description:                                                               
 *	Waits for duration specified by ShortDuration for client to           
 *	become blocked.                                                       
 */

static void
wait_blocked_command (int client_id, PROCTBL * procTbl,
		      char command_buffer[COMMAND_BUFFER_SIZE],
		      unsigned int *status)
{
  int mseconds_elapsed;
  int client_active;
  qamc_msg msg;

  *status = 0;

  /* Wait for duration specified by ShortDuration for client to
   * become blocked.
   */

  for (mseconds_elapsed = 0;
       mseconds_elapsed < ShortDuration
       && GetProcState (procTbl, client_id) != FINISHED
       && !local_tm_isblocked (GetProcInfo (procTbl, client_id)->tran_index);
       mseconds_elapsed += SLEEP_MSECS)
    {
      client_active = service_clients (procTbl);
      sleepms (SLEEP_MSECS);
    }

  if (mseconds_elapsed >= ShortDuration)
    {
      if (GetProcState (procTbl, client_id) == READY)
	{
	  qactl_output (stdout,
			"ERROR! Client %d is ready.\n Check the script for errors.\n"
			"If the script is correct then check qacsql by testing manually.\n"
			"=> %s.\n", client_id, command_buffer);
	  *status = 1;
	}
      else
	{
	  qactl_output (stdout,
			"WARNING! Client %d was not blocked after waiting %d"
			" seconds.\n => %s.\n", client_id, ShortDuration,
			command_buffer);
	  /* Wait for duration specified by LongDuration 
	     for client to become blocked. */
	  for (mseconds_elapsed = 0;
	       mseconds_elapsed < LongDuration
	       && (GetProcState (procTbl, client_id) != FINISHED)
	       && !local_tm_isblocked (GetProcInfo (procTbl, client_id)->
				       tran_index);
	       mseconds_elapsed += SLEEP_MSECS)
	    {
	      client_active = service_clients (procTbl);
	      sleepms (SLEEP_MSECS);
	    }
	  if (mseconds_elapsed >= LongDuration)
	    {
	      *status = 1;
	      qactl_output (stdout,
			    "ERROR! Client %d was not blocked after waiting %d more"
			    " seconds.\n"
			    " Verify client needs more time to finish processing.\n"
			    "If client finished processing then check qacsql for problems.\n"
			    "=> %s.\n", client_id, (LongDuration / 1000),
			    command_buffer);
	    }
	}
    }

  if (ctlr_mode)
    {
      if (!*status)
	{
	  /* Tell Super that the client event occurred */
	  msg.msgtype = QAMC_MSG_CLIENTEVENT;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
      else
	{
	  /* Tell Super that the client event did not occur */
	  msg.msgtype = QAMC_MSG_ERROR;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
    }
}


/*
 * wait_unblocked_command                                                     
 *                                                                            
 * arguments:								      
 *      client_id:      Cardinal client id number.                            
 *      procTbl:        Table of all client data.                             
 *      command_buffer: Entire CSQL statement.                                
 *	status:         Zero = ok, non-zero = failure.                        
 *	internal:        1 if readiness is being checked internally,       
 *	                 0 if readiness is checked as part of input cmd.  
 * returns/side-effects:                                                      
 *	Returns 0                                                             
 * description:                                                               
 *	Waits for duration specified by ShortDuration for client to           
 *	become unblocked.                                                     
 */

static void
wait_unblocked_command (int client_id, PROCTBL * procTbl,
			char command_buffer[COMMAND_BUFFER_SIZE],
			unsigned int *status, int internal)
{
  /* LOCAL DECLARATIONS */

  int mseconds_elapsed;
  int client_active;
  int this_client;
  int all_clients_blocked;
  qamc_msg msg;

  /* LOGIC */

  *status = 0;

  /* Wait for duration specified by ShortDuration 
   * for client to become blocked.
   */
  for (mseconds_elapsed = 0;
       mseconds_elapsed < ShortDuration
       && (GetProcState (procTbl, client_id) != FINISHED)
       && local_tm_isblocked (GetProcInfo (procTbl, client_id)->tran_index);
       mseconds_elapsed += SLEEP_MSECS)
    {
      client_active = service_clients (procTbl);
      sleepms (SLEEP_MSECS);
    }

  if (mseconds_elapsed >= ShortDuration)
    {
      all_clients_blocked = all_blocked (procTbl);
      if (all_clients_blocked)
	{
	  qactl_output (stdout, "ERROR! All clients are blocked.\n"
			"Check script for unintentional deadlock situation.\n"
			"If the script is correct then check for possible deadlock bug.\n"
			"=> %s.\n", command_buffer);
	  *status = 1;
	}
      else
	{
	  int other_clients_ready;

/*!!!*/
	  other_clients_ready = 1;
	  for (this_client = 1;
	       this_client <= procTbl->slotsinuse; this_client++)
	    {
	      if (this_client != client_id)
		{
		  other_clients_ready &=
		    GetProcState (procTbl, this_client) == READY;
		}
	    }

	  if ((other_clients_ready) &&
	      (GetProcState (procTbl, client_id) == READY))
	    {
	      qactl_output (stdout,
			    "ERROR! Client %d is blocked and other clients are ready.\n"
			    "Check the script. If the script is correct then check\n"
			    "for possible qacsql bug.\n"
			    "=> %s.\n", client_id, command_buffer);
	      *status = 1;
	    }
	  else
	    {
	      qactl_output (stdout,
			    "WARNING! Client %d blocked after waiting %d seconds.\n=> %s.\n",
			    client_id, (ShortDuration / 1000),
			    command_buffer);
	      /* Wait for duration specified by LongDuration 
	         for client to become ready. */
	      for (mseconds_elapsed = 0;
		   (mseconds_elapsed < LongDuration)
		   && (GetProcState (procTbl, client_id) != FINISHED)
		   && local_tm_isblocked (GetProcInfo (procTbl, client_id)->
					  tran_index);
		   mseconds_elapsed += SLEEP_MSECS)
		{
		  client_active = service_clients (procTbl);
		  sleepms (SLEEP_MSECS);
		}

	      if (mseconds_elapsed >= LongDuration)
		{
		  *status = 1;
		  qactl_output (stdout,
				"ERROR! Client %d remains blocked after waiting %d more"
				" seconds.\n"
				"Verify client needs more time to finish processing.\n"
				"If client finished processing then check qacsql for"
				" problems.\n"
				"=> %s.\n",
				client_id, (LongDuration / 1000),
				command_buffer);
		}
	    }
	}
    }

  if (ctlr_mode && !internal)
    {
      if (!*status)
	{
	  /* Tell Super that the client event occurred */
	  msg.msgtype = QAMC_MSG_CLIENTEVENT;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
      else
	{
	  /* Tell Super that the client event did not occur */
	  msg.msgtype = QAMC_MSG_ERROR;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
    }
}

static int
all_blocked (PROCTBL * procTbl)
{
  int cnt;
  int all_clients_blocked = 1;
  PROCINFO *proc;

  proc = procTbl->active;
  cnt = 0;
  while ((proc != NULL) && (cnt < procTbl->num_active))
    {
      if ((GetProcState (procTbl, proc->id) != OTHER_MC) &&
	  (proc->client_type == CL_QACSQL))
	{
	  all_clients_blocked = (all_clients_blocked &&
				 local_tm_isblocked (proc->tran_index));
	}
      cnt++;
      proc = proc->right;
    }

  if (cnt != procTbl->num_active)
    {
      qactl_output (stderr,
		    "UNEXPECTED ERROR! num_active clients (%d) did not match actual number on active list (%d)\n",
		    procTbl->num_active, cnt);
      longjmp (main_env, PROCTBL_INCONSISTENCY);
    }

  return (all_clients_blocked);
}

/*
 * wait_ready_command                                                         
 *                                                                            
 * arguments:                                                                 
 *      client_id:       Cardinal client id number.                           
 *      procTbl:         Table of all client data.                            
 *      command_buffer:  Entire CSQL statement.                               
 *	status:          Zero = ok, non-zero = failure.                       
 *	internal:        1 if readiness is being checked internally,       
 *	                 0 if readiness is checked as part of input cmd.  
 * returns/side-effects:                                                      
 *	Returns 0                                                             
 * description:                                                               
 *	Waits for duration specified by ShortDuration for client to           
 *	become ready.                                                         
 */
static void
wait_ready_command (int client_id, PROCTBL * procTbl,
		    char command_buffer[COMMAND_BUFFER_SIZE],
		    unsigned int *status, int internal)
{
  int mseconds_elapsed;
  int client_active;
  int all_clients_blocked;
  int other_clients_ready;
  int this_client;
  qamc_msg msg;

  all_clients_blocked = all_blocked (procTbl);

  if (*status == 0)
    {
      wait_unblocked_command (client_id, procTbl, command_buffer, status, 1);
    }

  /* Wait for client's status to change to "READY". */
  if (*status == 0)
    {
      /* Wait for duration specified by ShortDuration 
         for client to become ready. */
      for (mseconds_elapsed = 0;
	   (mseconds_elapsed < ShortDuration)
	   && (GetProcState (procTbl, client_id) != FINISHED)
	   && (GetProcState (procTbl, client_id) != READY);
	   mseconds_elapsed += SLEEP_MSECS)
	{
	  client_active = service_clients (procTbl);
	  sleepms (SLEEP_MSECS);
	}

      if (mseconds_elapsed >= ShortDuration)
	{
	  all_clients_blocked = all_blocked (procTbl);
	  if (all_clients_blocked)
	    {
	      qactl_output (stdout, "ERROR! All clients are blocked.\n"
			    "Check the script for unintentional deadlock situation.\n"
			    "If the script is correct then check for possible deadlock"
			    "bug.\n" "=> %s.\n", command_buffer);
	      *status = 1;
	    }
	  else
	    {
/*!!!*/
	      for (this_client = 1, other_clients_ready = 1;
		   this_client <= procTbl->slotsinuse; this_client++)
		{
		  if (this_client != client_id)
		    {
		      other_clients_ready =
			(other_clients_ready
			 && (GetProcState (procTbl, this_client) == READY));
		    }
		}

	      if ((other_clients_ready)
		  && (GetProcState (procTbl, client_id) == READY))
		{
		  qactl_output (stdout,
				"ERROR! Client %d is blocked and other clients are ready.\n"
				"Check the script.\n"
				"If the script is correct then check for possible qacsql"
				" bug.\n"
				"=> %s.\n", client_id, command_buffer);
		  *status = 1;
		}
	      else
		{
		  qactl_output (stdout,
				"WARNING! Client %d was not ready after"
				" waiting %d seconds.\n=> %s.\n",
				client_id, (ShortDuration / 1000),
				command_buffer);
		  /* Wait for duration specified by LongDuration 
		     for client to become ready. */
		  for (mseconds_elapsed = 0;
		       (mseconds_elapsed < LongDuration)
		       && (GetProcState (procTbl, client_id) != FINISHED)
		       && (GetProcState (procTbl, client_id) != READY);
		       mseconds_elapsed += SLEEP_MSECS)
		    {
		      client_active = service_clients (procTbl);
		      sleepms (SLEEP_MSECS);
		    }

		  if (mseconds_elapsed >= LongDuration)
		    {
		      *status = 1;
		      qactl_output (stdout,
				    "ERROR! Client was not ready after"
				    " waiting %d more seconds.\n"
				    "Verify client needs more time to finish processing.\n"
				    "If client finished processing then check qacsql for"
				    " problems.\n" "=> %s.\n",
				    (LongDuration / 1000), command_buffer);
		    }
		}

	    }
	}
    }

  /* We only tell the meta-controller if an input script command said to. */
  if (ctlr_mode && !internal)
    {
      if (!*status)
	{
	  /* Tell Super that the client event occurred */
	  msg.msgtype = QAMC_MSG_CLIENTEVENT;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
      else
	{
	  /* Tell Super that the client event did not occur */
	  msg.msgtype = QAMC_MSG_ERROR;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
    }
}

/*
 * wait_finish_command                                                        
 *                                                                            
 * arguments:                                                                 
 *      client_id:       Cardinal client id number.                           
 *      procTbl:         Table of all client data.                            
 *      command_buffer:  Entire CSQL statement.                               
 *	status:          Zero = ok, non-zero = failure.                       
 *	internal:        1 if readiness is being checked internally,       
 *	                 0 if readiness is checked as part of input cmd.  
 * returns/side-effects:                                                      
 *	Returns 0                                                             
 * description:                                                               
 *	Waits for duration specified by ShortDuration for client process      
 *	to finish.                                                            
 */
static void
wait_finish_command (int client_id, PROCTBL * procTbl,
		     char command_buffer[COMMAND_BUFFER_SIZE],
		     unsigned int *status, int internal)
{
  int mseconds_elapsed;
  int client_active;
  int all_clients_blocked;
  qamc_msg msg;

  if (*status == 0)
    {
      wait_unblocked_command (client_id, procTbl, command_buffer, status, 1);
    }

  /* Wait for client state to become FINISH. */
  if (*status == 0)
    {
      for (mseconds_elapsed = 0;
	   (mseconds_elapsed < ShortDuration)
	   && (GetProcState (procTbl, client_id) != FINISHED);
	   mseconds_elapsed += SLEEP_MSECS)
	{
	  client_active = service_clients (procTbl);
	  sleepms (SLEEP_MSECS);
	}

      if (mseconds_elapsed >= ShortDuration)
	{
	  all_clients_blocked = all_blocked (procTbl);
	  if (all_clients_blocked)
	    {
	      qactl_output (stdout, "ERROR! All clients are blocked.\n"
			    "Check the script for unintentional deadlock situation.\n"
			    "If the script is correct then check for possible deadlock"
			    "bug.\n" "=> %s.\n", command_buffer);
	      *status = 1;
	    }
	  else
	    {
	      qactl_output (stdout, "WARNING! Client %d not finished after"
			    " waiting %d seconds.\n=> %s.\n",
			    client_id, (ShortDuration / 1000),
			    command_buffer);
	      /* Wait for duration specified by LongDuration 
	         for client to become ready. */
	      for (mseconds_elapsed = 0;
		   (mseconds_elapsed < LongDuration)
		   && (GetProcState (procTbl, client_id) != FINISHED);
		   mseconds_elapsed += SLEEP_MSECS)
		{
		  client_active = service_clients (procTbl);
		  sleepms (SLEEP_MSECS);
		}

	      if (mseconds_elapsed >= LongDuration)
		{
		  *status = 1;
		  qactl_output (stdout, "ERROR! Client did not finish after"
				" waiting %d more seconds.\n"
				"Verify client needs more time to finish processing.\n"
				"If client finished processing then check qacsql for"
				" problems.\n"
				"=> %s.\n", (LongDuration / 1000),
				command_buffer);
		}
	    }			/* END else */
	}
    }				/* END if *status == 0 */

  /* We only tell the meta-controller if an input script command said to. */
  if (ctlr_mode && !internal)
    {
      if (!*status)
	{
	  /* Tell Super that the client event occurred */
	  msg.msgtype = QAMC_MSG_CLIENTEVENT;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
      else
	{
	  /* Tell Super that the client event did not occur */
	  msg.msgtype = QAMC_MSG_ERROR;
	  msg.sender_id = master_id;
	  strcpy (msg.msg, command_buffer);
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      qactl_output (stderr, "ERROR - qamc_send failed, LINE %d\n",
			    __LINE__);
	    }
	}
    }
}

/*
 * wait_command                                                               
 *                                                                            
 * arguments:                                                                 
 *	cmd_ptr:        CSQL statement, stripped of the initial "MC:" target. 
 *      procTbl:        Table of all client data.                             
 *      start_line:     Starting line index of each CSQL command.             
 *      command_buffer: Entire CSQL statement.                                
 *	status:         Zero = ok, non-zero = failure.                        
 * returns/side-effects:                                                      
 *	Returns 0                                                             
 * description:                                                               
 * 	Initiates processing of the "wait until" command. Extracts and        
 *	verifies client ID. Determines "wait until" command and calls         
 *	relevant subroutine.                                                  
 */

static void
wait_command (char *cmd_ptr, PROCTBL * procTbl,
	      int start_line,
	      char command_buffer[COMMAND_BUFFER_SIZE], unsigned int *status)
{
  int client_id = 0;

  *status = 0;

  /* Skip over WAIT_TOKEN. */
  cmd_ptr += strlen (WAIT_TOKEN);

  /* Get the client ID. */
  if (isdigit ((int) (*cmd_ptr)))
    {
      client_id = (int) (atoi ((const char *) cmd_ptr));
    }
  else
    {
      qactl_output (stdout,
		    "ERROR! Expecting client identifier on line %d.\n=> %s.\n",
		    start_line, command_buffer);
      qactl_output (stdout, WAIT_USAGE_FORMAT, WAIT_TOKEN);
      *status = 1;
    }

  /* Check the client ID. */
  if (*status == 0)
    {
      if (!VALID_CLIENT_ID (procTbl, client_id))
	{
	  qactl_output (stdout,
			"ERROR! client identifier (%d) out of range on"
			" line %d.\n=> %s.\n", client_id, start_line,
			command_buffer);
	  *status = 1;
	}
    }

  /* Determine subcommand. */
  if (*status == 0)
    {
      /* Skip the client ID number (more than one digit allowed). */
      while (isdigit (*cmd_ptr++))
	;

      /* Skip the space character. */
      skip_white_comments (&cmd_ptr);

      /* Determine variation of the wait command. */

      if (strncasecmp (cmd_ptr, WAIT_BLOCKED_TOKEN,
		       strlen (WAIT_BLOCKED_TOKEN)) == 0)
	{
	  wait_blocked_command (client_id, procTbl, command_buffer, status);
	}
      else if (strncasecmp (cmd_ptr, WAIT_UNBLOCKED_TOKEN,
			    strlen (WAIT_UNBLOCKED_TOKEN)) == 0)
	{
	  wait_unblocked_command (client_id, procTbl, command_buffer, status,
				  0);
	}
      else if (strncasecmp (cmd_ptr, WAIT_READY_TOKEN,
			    strlen (WAIT_READY_TOKEN)) == 0)
	{
	  wait_ready_command (client_id, procTbl, command_buffer, status, 0);
	}
      else if (strncasecmp (cmd_ptr, WAIT_FINISH_TOKEN,
			    strlen (WAIT_FINISH_TOKEN)) == 0)
	{
	  wait_finish_command (client_id, procTbl, command_buffer, status, 0);
	}
      else
	{
	  qactl_output (stdout,
			"ERROR! Expecting wait subcommad of { %s | %s | %s }"
			" on line %d.\n=> %s.\n", WAIT_BLOCKED_TOKEN,
			WAIT_UNBLOCKED_TOKEN, WAIT_READY_TOKEN, start_line,
			command_buffer);
	  *status = 1;
	}
    }
}


/*
 * num_alive_clients							      
 *									      
 * arguments:								      
 *      procTbl:        Table of all client data.                             
 *									      
 * returns/side-effects: int 						      
 *									      
 * description: Returns the number of alive clients.			      
 */

static int
num_alive_clients (PROCTBL * procTbl)
{
  return (procTbl->num_active);
}


/*
 * kill_aclient							      	      
 *									      
 * arguments:								      
 *      procTbl: Table of all client data.                                    
 *	proc   : data block for a single client.                              
 *									      
 * returns/side-effects: Deallocates the PROCINFO which used to describe      
 *   the process.                                                             
 * description: Kills a single client.  The clients PROCINFO must be removed  
 *   from the "active" list before calling this routine.                      
 */

static void
kill_aclient (PROCTBL * procTbl, int client)
{
  int pid, status;
  PROCINFO *proc;

  proc = GetProcInfo (procTbl, client);
  if (proc == NULL)
    {
      return;
    }

  if (((GetProcState (procTbl, client)) != FINISHED) &&
      (GetProcState (procTbl, client)) != OTHER_MC)
    {
      close (proc->read_fd);
      close (proc->write_fd);
      close (proc->error_fd);

      /* Make an attempt to wait on children and kill them forcefully
       * if necessary. */
      sleepms (100);

    wait_again:
      if (proc->pid && (pid = waitpid (proc->pid, &status, WNOHANG)) <= 0)
	{
	  /* Means child not dead yet */
	  if (pid < 0 && errno == EINTR)
	    {
	      goto wait_again;
	    }

	  if (pid == 0)
	    {
	      if (kill (proc->pid, SIGKILL) == 0)
		{
		  qactl_output (stderr, "STATUS: forcefully killing pid %d\n",
				proc->pid);
		  sleepms (100);
		  pid = waitpid (proc->pid, &status, WNOHANG);
		}
	    }
	}
      proc->pid = 0;		/* No longer in use */

      SetProcExitStatus (procTbl, client, WEXITSTATUS (status));
      SetProcState (procTbl, client, FINISHED);
      deallocate_proc (procTbl, proc);
    }

  return;
}

/*
 * kill_clients							      	      
 *									      
 * arguments:								      
 *      procTbl: Table of all client data.                                    
 *									      
 * returns/side-effects: nothing 					      
 *									      
 * description: Kills all the alive clients.			      	      
 */

static void
kill_clients (PROCTBL * procTbl)
{
  int i;

  for (i = 0; i < procTbl->slotsinuse; i++)
    {
      kill_aclient (procTbl, i + 1);
    }

  return;
}

static int
master_timeout (PROCTBL * procTbl)
{
  int status;
  int pid;
  int i;
  PROCINFO *proc;

  /* This will tell you if any client has died. */
  while ((pid = waitpid (-1, &status, WNOHANG)) > 0)
    {
      /* traverse active list looking for the client which has just died */
      for (i = 0, proc = procTbl->active;
	   proc != NULL && i < procTbl->num_active; i++, proc = proc->right)
	{
	  if (proc->pid == pid)
	    {
	      /*
	       * This probably needs to do something useful if the client
	       * hasn't really exited (e.g., it's stopped or suspended), but
	       * it doesn't look like any of the other code in this module is
	       * prepared to cope with that.
	       */
	      SetProcState (procTbl, proc->id,
			    (WIFEXITED (status) ? FINISHED : FINISHED));
	      SetProcExitStatus (procTbl, proc->id, status);
	      proc->pid = 0;	/* No longer in use */
	      (void) deactivate_proc (procTbl, proc);
	      break;
	    }
	}
    }

  return (1);
}


/*
 * Sets the fd positions in fd_set for the input fds we are interested in
 * reading. 
 */
static fd_set *
enroll_master_read_pipes (PROCTBL * procTbl)
{
  static fd_set fd_var;
  int in_count;
  int i;
  PROCINFO *proc;

  in_count = 0;
  FD_ZERO (&fd_var);
  for (i = 0, proc = procTbl->active; proc != NULL && i < procTbl->num_active;
       i++, proc = proc->right)
    {
      FD_SET (proc->read_fd, &fd_var);
      FD_SET (proc->error_fd, &fd_var);
      in_count++;
    }

  /* In slave mode, also enroll the socket.  */
  if (ctlr_mode)
    {
      FD_SET (superi.sock, &fd_var);
      in_count++;
    }

  if (in_count)
    {
      return (&fd_var);
    }
  else
    {
      return (NULL);
    }
}

/*
 * Sets the fd positions in fd_set for the input fds we are interested in
 * writing. (none presently)
 */
static fd_set *
enroll_master_write_pipes (PROCTBL * procTbl)
{
  static fd_set fd_var;

  FD_ZERO (&fd_var);
  return (&fd_var);
}

/*
 * Sets the fd positions in fd_set for the input fds we are interested in
 * detecting error conditions (pipe closed, etc).
 */
static fd_set *
enroll_master_exception_pipes (PROCTBL * procTbl)
{
  static fd_set fd_var;
  PROCINFO *proc;
  int i;

  FD_ZERO (&fd_var);

  for (i = 0, proc = procTbl->active; proc != NULL && i < procTbl->num_active;
       i++, proc = proc->right)
    {
      FD_SET (proc->read_fd, &fd_var);
      FD_SET (proc->write_fd, &fd_var);
      FD_SET (proc->error_fd, &fd_var);
    }

  /* In slave mode, also enroll the socket.  */
  if (ctlr_mode)
    {
      FD_SET (superi.sock, &fd_var);
    }

  return (&fd_var);
}

/*
 * This indicates that the select failed (usually because of an invalid 
 * fd). Check status of all known fds and remove those that are closed.
 */
static int
master_select_error (PROCTBL * procTbl)
{
  int status = 0;
  int i;
  PROCINFO *proc;

restart_active:
  for (i = 0, proc = procTbl->active; proc != NULL && i < procTbl->num_active;
       i++, proc = proc->right)

    if ((fcntl (proc->read_fd, F_GETFL, status) < 0) ||
	(fcntl (proc->write_fd, F_GETFL, status) < 0) ||
	(fcntl (proc->error_fd, F_GETFL, status) < 0))
      {
	qactl_output (stderr,
		      "WARNING: mst_select_error() Client %d apparently died\n",
		      proc->id);
	deactivate_proc (procTbl, proc);
	kill_aclient (procTbl, proc->id);
	goto restart_active;
      }

  return (1);
}

/*
 * Callback routine to read a clients output.  This routine makes
 * no assumptions about the output it is reading, and will basically
 * copy it.
 * Does not even assume that the "client" program is even a database 
 * client program.
 */
static int
generic_output_filter (PROCTBL * procTbl, PROCINFO * proc)
{
  int rc;
  int count, count2;
  char buffer[CLIENT_READ_BUFFER_SIZE + 1];
  char buf2[(CLIENT_READ_BUFFER_SIZE + 5) * 3];

repeat_read:
  rc = read (proc->read_fd, buffer, CLIENT_READ_BUFFER_SIZE);
  if (rc <= 0)
    {
      if ((rc < 0) && (errno == EINTR))
	{
	  goto repeat_read;
	}
      else
	{
	  return (-1);
	}
    }
  else
    {
      buffer[rc] = '\0';

      qactl_output (stdout, "C%d output:\n", proc->id);
      strcpy (buf2, "| ");
      for (count = 0, count2 = strlen (buf2);
	   (buffer[count] != '\0') &&
	   (count < CLIENT_READ_BUFFER_SIZE); count++, count2++)
	{
	  buf2[count2] = buffer[count];
	  /* NOTE that if buffer is all \n's, buf2 must be 3 times larger
	   * than buffer! Otherwise we start writing outside of buf2.
	   */
	  if (buffer[count] == '\n' && buffer[count + 1] != '\0')
	    {
	      buf2[++count2] = '|';
	      buf2[++count2] = ' ';
	    }
	}
      buf2[count2++] = '\n';
      buf2[count2++] = '\0';	/* make sure to end the string */
      qactl_output (stdout, buf2);

      proc->statement_count = 0;
      SetProcState (procTbl, proc->id, READY);

      return (rc);
    }				/* end ELSE */

  return (rc);
}


/*
 * Callback routine to read output from QAUSQLX clients.
 * Understands the output format, and keeps track of the 
 * transaction id's, substatement counts, etc.
 * Always reads from the read_fd descriptor.
 *
 * Return <= 0 for an error or EOF, otherwise the number of bytes read.
 */
static int
qacsql_output_filter (PROCTBL * procTbl, PROCINFO * proc)
{
  int read_tran_index;
  int rc;
  int count, count2;
  char *buffer_ptr, *rdy_ptr;
  char buffer[CLIENT_READ_BUFFER_SIZE + 1];
  char buf2[(CLIENT_READ_BUFFER_SIZE + 5) * 3];

repeat_read:
  rc = read (proc->read_fd, buffer, CLIENT_READ_BUFFER_SIZE);
  if (rc <= 0)
    {
      if ((rc < 0) && (errno == EINTR))
	{
	  goto repeat_read;
	}
      else
	{
	  return (-1);
	}
    }
  else
    {
      buffer[rc] = '\0';
      buffer_ptr = strstr (buffer, "Transaction index = ");
      if (buffer_ptr != NULL)
	{
	  buffer_ptr += strlen ("Transaction index = ");
	  read_tran_index = atoi (buffer_ptr);
	  if (proc->tran_index != read_tran_index)
	    {
	      proc->tran_index = read_tran_index;
	    }
	}

      if (proc->tran_index == -1)
	{
	  qactl_output (stdout, "WARNING: Client %d has an invalid"
			" Transaction index\n %d\n", proc->id,
			proc->tran_index);
	}
      qactl_output (stdout, "C%d output (Transaction index = %d):\n",
		    proc->id, proc->tran_index);
      strcpy (buf2, "| ");
      for (count = 0, count2 = strlen (buf2);
	   (buffer[count] != '\0') &&
	   (count < CLIENT_READ_BUFFER_SIZE); count++, count2++)
	{
	  buf2[count2] = buffer[count];
	  /* NOTE that if buffer is all \n's, buf2 must be 3 times larger
	   * than buffer! Otherwise we start writing outside of buf2.
	   */
	  if (buffer[count] == '\n' && buffer[count + 1] != '\0')
	    {
	      buf2[++count2] = '|';
	      buf2[++count2] = ' ';
	    }
	}
      buf2[count2++] = '\n';
      buf2[count2++] = '\0';	/* make sure to end the string */
      qactl_output (stdout, "%s", buf2);

      /* Check for explicit ready message from the client to
       * determine when they are really ready.
       */
      rdy_ptr = buf2;
      while ((rdy_ptr = strstr (rdy_ptr, ") is ready.")) != NULL)
	{
	  rdy_ptr += strlen (") is ready.");
	  proc->statement_count--;
	  /* can only be ready when all substatements are also complete */
	  if (proc->statement_count == 0)
	    {
	      SetProcState (procTbl, proc->id, READY);
	    }
	  if (proc->statement_count < 0)
	    {
	      qactl_output (stderr,
			    "Internal Error: client %d statement count was negative\n",
			    proc->id);
	      qactl_output (stderr, "              : buf2 was %s\n", buf2);
	      assert (proc->statement_count >= 0);
	    }
	}
      return (count);
    }				/* end ELSE */

  return (rc);
}

/*
 * This indicates that there has been input from a client
 * pipe that must be processed.
 */
static int
check_master_pipe_input (PROCTBL * procTbl, fd_set * fd_var)
{
  int rc;
  int i;
  PROCINFO *proc;
  static qamc_msg qamc_msg_buffer;
  struct qamc_ctlr_status_s status_buf;
  char buffer[CLIENT_READ_BUFFER_SIZE + 1];

  /* First check for messages from the super controller.  Pending messages
   * are put on a fifo queue until someone uses them.  Messages of this type
   * must be used in the order they were received. Calling routines can
   * access the message from the LastMsg pointer.  Whenever a caller uses
   * the message therein (cause the were expecting it -- see rendezvous), the
   * caller is expected to get the next message from the queue.
   */
  if (ctlr_mode)
    {
      if (FD_ISSET (superi.sock, fd_var))
	{
	  FD_CLR (superi.sock, fd_var);
	  i = qamc_recv (superi.sock, &qamc_msg_buffer, NULL);
	  if (i < 0)
	    {
	      qactl_output (stderr,
			    "ERROR: qamc_recv() failure in check_master_pipe_input");
	      /* Treat as fatal. */
	      longjmp (main_env, PARENT_RECV_ERROR);
	    }
	  else
	    {
	      if ((i == 0) && ((int) qamc_msg_buffer.msglen < 0))
		{
		  /* This should rarely happen */
		  qactl_output (stderr,
				"ERROR: other end of socket apparently quit early");
		  longjmp (main_env, PARENT_CONTROLLER_EXITED);
		}
	      qamc_msg_buffer.msg[qamc_msg_buffer.msglen] = '\0';
	      /* Most messages might require immediate attention, rest should be
	       * put on the queue for consumption at the appropriate time.  */
	      switch (qamc_msg_buffer.msgtype)
		{
		case QAMC_SMSG_CONTINUE:
		  rc = qamc_insert_fifoq (&qamc_msg_buffer, &FifoQ);
		  if (rc <= 0)
		    {
		      qactl_output (stderr,
				    "ERROR: Problem saving CONTINUE message for later consumption, MSG LOST!, erc = %d\n",
				    rc);
		    }
		  break;

		case QAMC_SMSG_TOKEN:
		  /* We just got handed the token */
		  HaveToken = 1;
		  break;

		case QAMC_SMSG_FORCE_SHUTDOWN:
		  /* Force (premature) end of test */
		  longjmp (main_env, PARENT_CONTROLLER_FORCED_SHUTDOWN);
		  break;

		case QAMC_SMSG_STATUS_INQUIRY:
		  /* return a current account of our state */
		  status_buf.c_pid = getpid ();
		  status_buf.c_clients = 0;
		  status_buf.c_alive_clients = 0;
		  for (i = 0; i < procTbl->slotsinuse; i++)
		    {
		      if (GetProcState (procTbl, i + 1) != OTHER_MC)
			{
			  status_buf.c_clients++;
			  if (GetProcState (procTbl, i + 1) != FINISHED)
			    {
			      status_buf.c_alive_clients++;
			    }
			}
		    }

		  /* Host to Network byte ordering */
		  status_buf.c_pid = htonl (status_buf.c_pid);
		  status_buf.c_clients = htonl (status_buf.c_clients);
		  status_buf.c_alive_clients =
		    htonl (status_buf.c_alive_clients);

		  qamc_msg_buffer.msgtype = QAMC_MSG_STATUS;
		  memcpy (qamc_msg_buffer.msg, &status_buf,
			  sizeof (status_buf));
		  qamc_msg_buffer.sender_id = master_id;
		  qamc_msg_buffer.msglen = sizeof (status_buf);
		  if (qamc_send (superi.sock, &qamc_msg_buffer, NULL) !=
		      qamc_msg_buffer.msglen)
		    {
		      qactl_output (stderr,
				    "ERROR - qamc_send failed, LINE %d\n",
				    __LINE__);
		      return (1);
		    }
		  break;

		  /* List Bad ones */
		case QAMC_SMSG_POLL:
		default:
		  /* !!! ACTUALLY, need to explicitly catch the bad ones as
		   * well so we can't get hung waiting for something which
		   * isn't supposed to happen.
		   */
		  qactl_output (stdout,
				"check_master: ERROR received message type %d unexpectedly\n",
				qamc_msg_buffer.msgtype);
		  return (0);	/* Halt */
		   /*NOTREACHED*/ break;
		}
	    }			/* ELSE */
	}

      /* Have LastMsg point to next msg stored in fifo */
      if (LastMsg == NULL)
	{
	  LastMsg = qamc_remove_fifoq (FifoQ);
	}
    }				/* IF ?ctlr_mode? */

  /* Traverse only the active ones */
restart_active:		/* if the active list is modified, must start over */
  for (i = 0, proc = procTbl->active; proc != NULL && i < procTbl->num_active;
       i++, proc = proc->right)
    {

      if (FD_ISSET (proc->read_fd, fd_var))
	{
	  FD_CLR (proc->read_fd, fd_var);
	  /* Read the data with the callback routine registered for this proc */
	  if ((proc->cl_output_filter == NULL))
	    {
	      rc = generic_output_filter (procTbl, proc);
	    }
	  else
	    {
	      rc = proc->cl_output_filter (procTbl, proc);
	    }
	  if (rc <= 0)
	    {
	      /* End of file is reached, terminate this client */
	      (void) deactivate_proc (procTbl, proc);
	      kill_aclient (procTbl, proc->id);
	      goto restart_active;
	    }
	}
      else if (FD_ISSET (proc->error_fd, fd_var))
	{
	  FD_CLR (proc->error_fd, fd_var);
	  rc = read (proc->error_fd, buffer, CLIENT_READ_BUFFER_SIZE);
	  if (rc > 0)
	    {
	      buffer[rc] = '\0';
	      qactl_output (stderr, "\nClient C%d (with tran_index %d) send"
			    " on the STDERR:\n", proc->id, proc->tran_index);
	      qactl_output (stderr, buffer);
	    }
#if 0
	  /* WiN -- There seems to be a timing issue here. Sometimes we get 0 length
	   * reads from the error_fd descriptor, apparently when the client
	   * is going away.  In these cases, isn't it better to just let the
	   * exception code in the master_timeout loop handle things?  It seems
	   * like the situation goes away immediately.  This problem just became
	   * apparent with the addition of the "execute" command, because now we
	   * have more clients exiting in the middle of a test.
	   */
	  else
	    {
	      qactl_output (stderr, "Error on read from client C%d (with"
			    " tran_index %d), rc = %d, errno = %d\n",
			    proc->id, proc->tran_index, rc, errno);
	      sleepms (100);
	    }
#endif

#ifdef cutoff
	  /* Commented out since some messages from the query processor 
	     are being sent to stderr */
	  /* Terminate this client */
	  deactivate_proc (procTbl, proc);
	  kill_aclient (procTbl, proc->id);
	  goto restart_active;
#endif
	}
    }

  return (1);
}

/*
 * Currently noop since Bruce says master should not check its output pipe 
 * to see if it has already written something . Always return 1.
 */
static int
check_master_pipe_output (PROCTBL * procTbl, fd_set * fd_var)
{
  return (1);
}

/*
 * Checks for exception conditions for open pipes. This usually means that
 * a client has gone down. A return value of 0 will terminate the master.
 */
static int
check_master_pipe_exception (PROCTBL * procTbl, fd_set * fd_var)
{
  int i;
  PROCINFO *proc;

  /* traverse only the active ones */
restart_active:
  for (i = 0, proc = procTbl->active; proc != NULL && i < procTbl->num_active;
       i++, proc = proc->right)
    {

      if ((FD_ISSET (proc->read_fd, fd_var)) ||
	  (FD_ISSET (proc->write_fd, fd_var)) ||
	  (FD_ISSET (proc->error_fd, fd_var)))
	{

	  (void) deactivate_proc (procTbl, proc);
	  kill_aclient (procTbl, proc->id);
	  goto restart_active;
	}
    }
  return (1);
}


/*
 * The main service loop for the master program. We setup the interesting
 * read, write, and exception fds and wait for something to happen (an 
 * incoming request, or an exception condition. If we time out then we
 * return to the function controling clients.
 */

static int
service_clients (PROCTBL * procTbl)
{
  fd_set *read_fd_ptr, *write_fd_ptr, *exception_fd_ptr;
  static struct timeval timeout;
  int rc, run_code;

  run_code = 1;
  while (run_code)
    {

      read_fd_ptr = enroll_master_read_pipes (procTbl);
      if (read_fd_ptr == NULL)
	{
	  return (0);
	}

      write_fd_ptr = enroll_master_write_pipes (procTbl);
      exception_fd_ptr = enroll_master_exception_pipes (procTbl);
      timeout.tv_sec = MASTER_TIMEOUT_VALUE_IN_SECONDS;
      timeout.tv_usec = MASTER_TIMEOUT_VALUE_IN_MICROSECONDS;

    retry_select:
      rc =
	select (FD_SETSIZE, read_fd_ptr, write_fd_ptr, exception_fd_ptr,
		&timeout);
      if ((rc < 0) && (errno == EINTR))
	{
	  goto retry_select;
	}

      switch (rc)
	{
	case (0):
	  run_code = master_timeout (procTbl);
	  return (1);
	   /*NOTREACHED*/ break;

	case (-1):
	  /* switch error */
	  run_code = master_select_error (procTbl);
	  break;

	default:
	  run_code = check_master_pipe_input (procTbl, read_fd_ptr);
	  if (run_code)
	    {
	      run_code = check_master_pipe_output (procTbl, write_fd_ptr);
	    }
	  if (run_code)
	    {
	      run_code =
		check_master_pipe_exception (procTbl, exception_fd_ptr);
	    }
	  break;
	}
    }

  return (1);
}


static void
print_mc_ope (int client_no, const char *str)
{
#ifdef TRACE_CONTROLLER
  char ignore_line[200];
#endif

  if (ctlr_mode)
    {
      qactl_output (stdout, "MC-%d to C%d: %s\n", master_id, client_no, str);
    }
  else
    {
      qactl_output (stdout, "MC to C%d: %s\n", client_no, str);
    }

#ifdef TRACE_CONTROLLER
  fprintf (stdout, "Press return to execute the above operations\n");
  fflush (stdout);
  if (fgets (ignore_line, 200, stdin) == NULL)
    {
      exit (100);
    }
#endif
}

/* determine the CL_TYPE for the name given */
static CL_TYPE
set_clienttype (const char *name)
{
  if (strstr (name, "qacsql") != NULL || strstr (name, "qamysql") != NULL
      || strstr (name, "qaoracle") != NULL)
    {
      return (CL_QACSQL);
    }
  else if (strstr (name, STRESSEXEC_CMDNAME) == 0)
    {
      return (CL_STRESSEXEC);
    }
  else
    {
      return (CL_GENERIC);
    }
}

/*
 * FUNCTION: start_process()
 *
 * DESCRIPTION:
 *   Start a child process of executable name with read/write/error channels
 * of communication open.  Note that caller must do something sensible if
 * we fail.
 * 
 * NOTE: We allocate and populate a PROCINFO, but the caller must 
 * activate_proc() it, in order for its output to be processed in the
 * main service loop.
 *
 * RETURN:
 *   Child pid if SUCCESS;
 *   else print error message (!) and return (-1) if FAIL.
 *
 */

static PROCINFO *
start_process (PROCTBL * procTbl, const char *executable_name,
	       const char **argv, int time_delay)
{
  int childpid;
  int fd, new_fd;
  int r_pipefd[2];
  int w_pipefd[2];
  int e_pipefd[2];
  PROCINFO *proc;
  char *base_name;
  char new_env_value[2048];

  if (pipe (r_pipefd) != 0)
    {
      qactl_output (stderr,
		    "master cannot create pipe (read) for child process, errno = %d\n",
		    errno);
      return (NULL);
    }

  if (pipe (w_pipefd) != 0)
    {
      qactl_output (stderr,
		    "master cannot create pipe (write) for child process, errno = %d\n",
		    errno);
      close (r_pipefd[0]);
      close (r_pipefd[1]);
      return (NULL);
    }

  if (pipe (e_pipefd) != 0)
    {
      qactl_output (stderr,
		    "master cannot create pipe (error) for child process, errno = %d\n",
		    errno);
      close (r_pipefd[0]);
      close (r_pipefd[1]);
      close (w_pipefd[0]);
      close (w_pipefd[1]);
      return (NULL);
    }

  proc = allocate_proc (&qa_ProcTbl);
  if (proc == NULL)
    {
      qactl_output (stderr,
		    "master cannot allocate room for another child process\n");
      close (r_pipefd[0]);
      close (r_pipefd[1]);
      close (w_pipefd[0]);
      close (w_pipefd[1]);
      close (e_pipefd[0]);
      close (e_pipefd[1]);
      return (NULL);
    }

  proc->read_fd = r_pipefd[0];
  proc->write_fd = w_pipefd[1];
  proc->error_fd = e_pipefd[0];
  proc->tran_index = -1;
  proc->statement_count = -1;

  proc->client_type = set_clienttype (executable_name);
  switch (proc->client_type)
    {
    case CL_UNKNOWN:
    case CL_GENERIC:
    case CL_STRESSEXEC:
      proc->cl_output_filter = generic_output_filter;
      break;

    case CL_QACSQL:
      proc->cl_output_filter = qacsql_output_filter;
      break;

    default:
      qactl_output (stderr,
		    "ERROR! unknown client type %d, no output filtering.\n",
		    proc->client_type);
      proc->cl_output_filter = generic_output_filter;
      break;
    };

  /* Create unique error log name */
  sprintf (new_env_value, "CUBRID_ERROR_LOG=errlog.%5.5s.%s.%d.%d",
	   ((base_name = strrchr (executable_name, '/')) ?
	    (base_name + 1) : executable_name),
	   LocalHost, getppid (), proc->id);

  childpid = fork ();
  if (childpid < 0)
    {
      qactl_output (stderr,
		    "Controller cannot fork for a new client, errno = %d\n",
		    errno);
      return (NULL);
    }
  else if (childpid > 0)
    {				/* This is the master code */
      close (w_pipefd[0]);
      close (r_pipefd[1]);
      close (e_pipefd[1]);
      proc->pid = childpid;
      SetProcState (procTbl, proc->id, RUNNING);

      return (proc);
    }
  else
    {				/* This is the client code */
      /* close all master files */
      for (fd = 0; fd < FD_SETSIZE; fd++)
	{
	  if (!((fd == w_pipefd[0]) || (fd == r_pipefd[1]) ||
		(fd == e_pipefd[1])))
	    {
	      close (fd);
	    }
	}

      new_fd = dup2 (r_pipefd[1], 1);
      if (new_fd < 0)
	{
	  perror ("error on dup2\n");
	  fprintf (stdout, "Error on dup2 to stdout\n");
	}
      new_fd = dup2 (e_pipefd[1], 2);
      if (new_fd < 0)
	{
	  perror ("error on dup2\n");
	  fprintf (stdout, "Error on dup2 to stderr\n");
	}

      new_fd = dup2 (w_pipefd[0], 0);
      if (new_fd < 0)
	{
	  fprintf (stdout, "Error on dup2 to stdin\n");
	}

      sleepms (time_delay * 1000L);

      /* Give the unique error log to the child environment */
      execvp (executable_name, (char **) argv);

      fprintf (stderr, "could not exec %s\n, errno = %d", executable_name,
	       errno);
      perror ("Exec error");
      exit (-1);		/* Don't fall through if failure */
    }

  return (NULL);
}

/*
 * client_not_responding						      
 *									      
 * arguments:								      
 *	  clients: 							      
 *	client_no: Which client is not responding			      
 *									      
 * returns/side-effects: nothing					      
 *									      
 * description: 							      
 */

static void
client_not_responding (PROCTBL * procTbl, int client_no)
{
  PROCINFO *proc;

  assert (client_no > 0);

  proc = GetProcInfo (procTbl, client_no);
  if (proc == NULL)
    {
      qactl_output (stdout,
		    "ERROR: There is no process info about client C%d\n",
		    client_no);
      return;
    }

  qactl_output (stdout, "ERROR: Client C%d (with tran_index %d)"
		" is not responding\n", client_no, proc->tran_index);

  /* Check if the client process is still alive */
  /* Check if the server process is still alive and serving clients */


  /* Check if the client has been blocked by the server */
  if (local_tm_isblocked (proc->tran_index))
    {
      qactl_output (stdout, "Because it is BLOCKED by the server\n");
    }
  else
    {
      qactl_output (stdout, "Apparently it is NOT BLOCKED by the server\n");
    }
}				/* client_not_responding */


static int
control_clients (PROCTBL * procTbl, const char *prog_name,
		 const char *db_name, FILE * inp_file)
{
  int db_error = NO_ERROR;
  char command_buffer[COMMAND_BUFFER_SIZE];
  char *cmd_ptr;
  char *ptr2;			/* ptr used in strtol result interpretation */
  int rc;
  int client_no = -1;
  int client_alive = 1;		/* Assume that the clients are alive initially */
  int cntr;
  const char *stmt = NULL;	/* stmt returned from csql interpreter */
  int start_line = 0;		/* start line of each qacsql cmd */
  int wait_seconds;
  int i;
  unsigned int status;		/* Return status from wait_command(). */
  qamc_msg msg;
  int error_occurred = 0;	/* > 0 if some not fatal error registered */
  int eof_reached = 0;		/* Only true when EOF is actually read */
  int substmt_cnt;		/* count sql stmt's within a compound statement */
  PROCINFO *proc;

  /* Add signal handler for user controlled stops and starts */
  if (ctlr_mode)
    {
      (void) signal (SIGTSTP, stopandstart_handler);
      (void) signal (SIGCONT, stopandstart_handler);
    }

  /* Handler for orderly shutdown */
  (void) signal (SIGTERM, sigterm_handler);

  while (client_alive || sync_mode)
    {
      client_alive = service_clients (procTbl);

      /* Get next statement if we are supposed to */
      if ((!ctlr_mode) || (sync_mode && HaveToken) || !sync_mode)
	{
	  stmt = qamc_get_compound_stmt (inp_file, &substmt_cnt, 0);
	  if (stmt == NULL)
	    {
	      eof_reached = 1;
	    }

#if defined (_MYSQL)
	  /* sleep 50ms to make we could read transaction/lock information 
	   * from mysql information_schema table. Maybe this is a bug of 
	   * mysql information_schema.
	   */
	  sleepms (50);
#endif

	  start_line++;
	}
      else if (ctlr_mode)
	{
	  continue;
	}

      /* NOTE: Nothing but stmt processing below this point */

      if (eof_reached)
	{
      /*************** 
	END OF INPUT  
       ***************/

	  /* Give back the token, and never get it back */
	  if (sync_mode && HaveToken)
	    {
	      RETURN_TOKEN (superi.sock, master_id, 0);
	      HaveToken = 0;
	    }

	  /* Wait until all the clients finish their last statements */
	  for (i = 1; i <= procTbl->slotsinuse; i++)
	    {
	      if ((GetProcState (procTbl, i) != OTHER_MC) &&
		  (GetProcState (procTbl, i) != FINISHED))
		{
		  /* 
		   * If the client is still alive then we have to wait for it
		   * to stop.
		   */
		  for (cntr = 0;
		       (GetProcState (procTbl, i) != FINISHED) &&
		       (cntr < LongDuration); cntr++)
		    {

		      proc = GetProcInfo (procTbl, i);
		      if (proc == NULL)
			{
			  qactl_output (stderr,
					"ERROR! Expected client information for client %d\n",
					i);
			  break;
			}

		      /* 
		       * If the client is waiting for a new command tell it to 
		       * shutdown
		       */
		      if ((cntr == 0) && (GetProcState (procTbl, i) == READY))
			{
			  switch (proc->client_type)
			    {
			    case CL_QACSQL:
			      /* NOTE: writing this is only valid for qacsql clients */
			      rc = write (proc->write_fd, "quit;\n", 6);
			      if (rc < 1)
				{
				  error_occurred++;
				  qactl_output (stdout,
						"ERROR! Detected broken pipe for client %d.\n",
						client_no);
				  qactl_output (stdout,
						"Code: %d, errno = %d, (line %d)\n",
						rc, errno, __LINE__);

				  (void) deactivate_proc (procTbl, proc);
				  kill_aclient (procTbl, client_no);
				  proc = NULL;
				}
			      break;

			    default:
			      /* No action we can safely take */
			      break;
			    }
			}

		      sleepms (10);
		      client_alive = service_clients (procTbl);

		    }		/* end FOR */
		  if (GetProcState (procTbl, i) != FINISHED)
		    {
		      client_not_responding (procTbl, i);
		      return (1);
		    }
		}
	    }

	  /* All the clients have finished; Exit loop */
	  break;
	}

      /* Give back the token */
      if (sync_mode && HaveToken)
	{
	  RETURN_TOKEN (superi.sock, master_id, 1);
	  HaveToken = 0;
	}

      cmd_ptr = command_buffer;

      /* copy to local memory */
      strcpy (cmd_ptr, stmt);

      skip_white_comments (&cmd_ptr);

      if ((int) strlen (cmd_ptr) < 1)
	{
	  /* There may be something wrong; print out error message */
	  qactl_output (stdout, "Got an empty statement.\n");
	  continue;
	}

      /* INTERPRET THE STATEMENT */
      if (strncasecmp (cmd_ptr, "mc:", 3) == 0)
	{
      /************************************************ 
	 A COMMAND FOR THE MASTER CLIENT (CONTROLLER)
       ************************************************/
	  /*
	     qactl_output (stderr, "QACTL %d line: %d statement: (%s)\n",
	     master_id, start_line, cmd_ptr);
	   */
	  qactl_output (stderr, "QACTL %d line: %d statement: (%s)\n",
			master_id, parse_line_num, cmd_ptr);

	  /* Skip "MC:" */
	  cmd_ptr += 3;
	  skip_white_comments (&cmd_ptr);

	  if (strncasecmp (cmd_ptr, "setup", 5) == 0)
	    {
	      /* INITIAL SETUP COMMAND TO SPECIFY NUMBER OF CLIENTS */

	      /* Skip this statement since it was already processed in the main
	         fnc */
	    }
	  else if (strncasecmp (cmd_ptr, WAIT_TOKEN, strlen (WAIT_TOKEN)) ==
		   0)
	    {
	      wait_command (cmd_ptr, procTbl, start_line,
			    command_buffer, &status);
	      /* This is really disgusting... */
	      if (status != 0)
		{
#if defined (CUBRID)
		  lock_dump (stdout);
#endif
		  return (1);
		}
	    }
	  else if (strncasecmp (cmd_ptr, DEADLOCK_PAUSE_TOKEN,
				strlen (DEADLOCK_PAUSE_TOKEN)) == 0)
	    {
	      /* Wait until system resolves the deadlock. */
	      /* TO DO
	         Replace sleep() with smart logic to verify and wait for deadlock
	         resolution.
	       */
	      wait_seconds = DEADLOCK_DURATION;
	      for (; wait_seconds-- > 0;)
		{
		  client_alive = service_clients (procTbl);
		  sleepms (1000);
		}

	      /* Tell Super that the client event occurred */
	      if (ctlr_mode)
		{
		  msg.msgtype = QAMC_MSG_CONTROLLEREVENT;
		  msg.sender_id = master_id;
		  strcpy (msg.msg, command_buffer);
		  msg.msglen = strlen (msg.msg);
		  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
		    {
		      qactl_output (stderr,
				    "ERROR - qamc_send failed, LINE %d\n",
				    __LINE__);
		      return (1);
		    }
		}
	    }
	  else if (strncasecmp (cmd_ptr, "wait for", 8) == 0)
	    {
	      /* WAIT FOR STATEMENT */

	      /* Skip "wait for" */
	      cmd_ptr += 8;

	      if (sscanf (cmd_ptr, "%d", &wait_seconds) != 1)
		{
		  qactl_output (stdout,
				"A number is expected on line %d.\n=> %s\n",
				start_line, command_buffer);
		  return (1);
		}

	      for (; wait_seconds-- > 0;)
		{
		  client_alive = service_clients (procTbl);
		  sleepms (1000);
		}

	      /* Tell Super that the client event occurred */
	      if (ctlr_mode)
		{
		  msg.msgtype = QAMC_MSG_CONTROLLEREVENT;
		  msg.sender_id = master_id;
		  strcpy (msg.msg, command_buffer);
		  msg.msglen = strlen (msg.msg);
		  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
		    {
		      qactl_output (stderr,
				    "ERROR - qamc_send failed, LINE %d\n",
				    __LINE__);
		      return (1);
		    }
		}
	    }
	  else if (strncasecmp (cmd_ptr, "reconnect", 9) == 0)
	    {
	      /* RECONNECT STATEMENT */

	      db_error = reconnect_to_server ();
	      if (db_error != NO_ERROR)
		{
		  qactl_output (stdout,
				"ERROR: Master Controller is unable to reconnect"
				" to server. Got error:%d\n", db_error);
		  return (db_error);
		}
	    }
	  else if (strncasecmp (cmd_ptr, RENDEZVOUS_TOKEN,
				strlen (RENDEZVOUS_TOKEN)) == 0)
	    {
	      cmd_ptr += strlen (RENDEZVOUS_TOKEN);
	      rendezvous_command (cmd_ptr, procTbl, start_line,
				  command_buffer, &status);
	      if (status)
		{
		  qactl_output (stderr, "ERROR: rendezvous command failed\n");
		  return (1);
		}
	    }
	  /* EXECUTE: exection of programs */
	  else if (strncasecmp (cmd_ptr, EXECUTE_TOKEN,
				strlen (EXECUTE_TOKEN)) == 0)
	    {
	      cmd_ptr += strlen (EXECUTE_TOKEN);
	      execute_command (cmd_ptr, procTbl, start_line,
			       command_buffer, &status);
	      if (status)
		{
		  qactl_output (stderr, "ERROR: execution command failed\n");
		  return (1);
		}
	    }
	  /* NOOP: provide a statement which does nothing (mainly
	   *       used for slave mode synchronization from the parent
	   *       controller).
	   */
	  else if (strncasecmp (cmd_ptr, NOOP_TOKEN, strlen (NOOP_TOKEN)) ==
		   0)
	    {
	      /* do nothing */
	    }
	  /* Allocate a client id to another controller (we don't care
	   * which one).  Only allowed in "slave mode". */
	  else if (ctlr_mode
		   && strncasecmp (cmd_ptr, ALLOC_CLIENT_TOKEN,
				   strlen (ALLOC_CLIENT_TOKEN)) == 0)
	    {
	      PROCINFO *proc;

	      if ((proc = allocate_proc (procTbl)) == NULL)
		{
		  qactl_output (stderr,
				"ERROR: Failed allocating client to another MC\n");
		  return (1);
		}
	      else
		{
		  SetProcState (procTbl, proc->id, OTHER_MC);
		  SetProcExitStatus (procTbl, proc->id, 0);
		  deallocate_proc (procTbl, proc);
		}
	    }
	  else if (strncasecmp (cmd_ptr, SLEEP_TOKEN, strlen (SLEEP_TOKEN)) ==
		   0)
	    {
	      /* SLEEP STATEMENT */
	      /* Skip "sleep " */
	      cmd_ptr += strlen (SLEEP_TOKEN);
	      int sleep_time;

	      if (sscanf (cmd_ptr, "%d", &sleep_time) != 1)
		{
		  qactl_output (stdout,
				"A number is expected on line %d.\n=> %s\n",
				start_line, command_buffer);
		  return (1);
		}
	      //qactl_output(stdout, "%d\n", sleep_time);
	      sleepms (sleep_time * 1000);
	    }
	  else
	    {
	      qactl_output (stdout,
			    "Master controller syntax error. Try one of the following:\n"
			    "  WAIT UNTIL Cx {READY|BLOCKED|UNBLOCKED}, WAIT FOR x SECONDS,\n"
			    "  EXECUTE, RECONNECT, RENDEZVOUS, PAUSE FOR DEADLOCK, NOOP.\n"
			    "  Unable to process the statement on line %d: %s\n",
			    start_line, command_buffer);
	      return (1);
	    }
	}			/* Master controller command */
      else
	{
      /***************************
	 A COMMAND FOR A CLIENT 
       ***************************/
	  /* Some client commands (e.g. "save state by") are ambiguous
	   * w/ regard to quoting and so it is possible that the 
	   * qamc_unquoted_lowercase_conversion routine above destroyed
	   * necessary data (like strings).  So restore the original 
	   * pristine command and send that to the clients.
	   cmd_ptr = command_buffer;
	   strcpy(cmd_ptr, stmt);
	   */

	  /* Determine client name, 1 or more (e.g. C1-Cxxx ) 
	   * remember we have to tell the difference between:
	   *   C0: , C: , C : , c    , c10000:  etc. 
	   *   Bad   Bad  Bad   Bad    OK  
	   */
	  client_no = (int) strtol (cmd_ptr + 1, &ptr2, 0);
	  if ((toupper ((int) *cmd_ptr) == 'C') && (client_no > 0) &&
	      (*ptr2 == ':') && ((cmd_ptr + 1) != ptr2))
	    {

	      /* Skip past the colon in "C1:" string */
	      cmd_ptr = ptr2 + 1;

	      skip_white_comments (&cmd_ptr);
	    }
	  else
	    {
	      /* Send it to the default client */
	      client_no = 1;
	    }
	  if (!VALID_CLIENT_ID (procTbl, client_no))
	    {
	      qactl_output (stderr,
			    "ERROR! Invalid client id specified, %d\n",
			    client_no);
	      return (1);
	    }

	  /* If this is a client command to a client owned by another
	   * master controller, then we just ignore it.
	   */
	  if (ctlr_mode && GetProcState (procTbl, client_no) == OTHER_MC)
	    {
	      continue;
	    }

	  /* This command must be intended for one of ours */
	  if (GetProcState (procTbl, client_no) != FINISHED)
	    {
	      /* If the client is still alive then let it perform the next action */
	      for (cntr = 0;
		   (GetProcState (procTbl, client_no) != READY) &&
		   (GetProcState (procTbl, client_no) != FINISHED) &&
		   (cntr < LongDuration); cntr += SLEEP_MSECS)
		{
		  sleepms (SLEEP_MSECS);
		  client_alive = service_clients (procTbl);
		}
	      if (GetProcState (procTbl, client_no) != READY)
		{
		  client_not_responding (procTbl, client_no);
		  return (1);
		}

	      proc = GetProcInfo (procTbl, client_no);
	      assert (proc != NULL);
	      /* Remember how many statements client is currently processing */
	      proc->statement_count = substmt_cnt;

	      /* WARNING: Should be checking the result codes from write() */
	      rc = write (proc->write_fd, cmd_ptr, strlen (cmd_ptr));
	      rc = write (proc->write_fd, "\n", 1);

	      /* Print next cmd_ptr AFTER telling the client so the output
	       * is aligned better and the client can start sooner.
	       */
	      print_mc_ope (client_no, cmd_ptr);
	      /* Check for broken pipe. */
	      if (rc < 1)
		{
		  error_occurred++;
		  qactl_output (stdout,
				"ERROR! Detected broken pipe for client %d.\n",
				client_no);
		  qactl_output (stdout, "Code: %d, errno = %d (line %d)\n",
				rc, errno, __LINE__);
		  (void) deactivate_proc (procTbl, proc);
		  kill_aclient (procTbl, client_no);
		  proc = NULL;
		  if (rc == 0)
		    {
		      qactl_output (stdout,
				    "Possible EOF - connection closed by other"
				    " end.\n");
		    }
		}
	      else
		{
		  /* only set to running if successful sending last command */
		  SetProcState (procTbl, client_no, RUNNING);
		}
	      /*
	         sleepms (200);
	         client_alive = service_clients(procTbl);
	       */
	    }			/* if client is alive */
	  else
	    {
	      qactl_output (stdout, "ERROR: Client C%d has already finished."
			    "Unable to process\n statement: %s\n",
			    client_no, cmd_ptr);
	    }
	}			/* a command to be fed to a client */
    }				/* while */

  /* If the entire input script was read and the clients have shutdown
   * then ok, else some problem occurred.
   */
  if (eof_reached && !error_occurred)
    {
      rc = 0;
      for (i = 1; i <= procTbl->slotsinuse; i++)
	{
	  /* if any clients did not exit cleanly, report it. */
	  if (GetProcState (procTbl, i) != OTHER_MC)
	    {
	      rc = rc | (GetProcExitStatus (procTbl, i) != 0);
	    }
	}
      return (rc);
    }

  /* Some error occurred */
  return (1);
}				/* control_clients */


/* Initiate the socket connect to the super-controller's socket, retrying if
 * necessary.
 * Return the fd for the newly opened socket, or -1 if error.
 */

/*
 * q_make_connection                                                          
 *                                                                            
 * arguments:                                                                 
 *       si   :  pointer to information about out parent controller;          
 * returns/side-effects:                                                      
 *     Returns open socket fd for success, -1 if an error occurred.           
 * description:                                                              
 *    Open the physical connect (socket) to the parent controller.  Perform   
 * up to 5 retries if necessary, if we have trouble connecting.  Which        
 * sometimes occurs when many attempts to connect to the parent's socket      
 * occur simultaneously.  See man page descriptions of accept() and connect().
 * for more info.                                                             
 */

static int
q_make_connection (struct super_info *si)
{
  int fd;
  int max_retries = 1;

  /* create socket */
  while (max_retries <= 16)
    {
      fd = socket (AF_INET, SOCK_STREAM, 0);
      if (fd < 0)
	{
	  fprintf (stderr,
		   "make_connection: could not create a socket, errno = %d\n",
		   errno);
	  return (-1);
	}

      /* Connect to server socket using host, port info given */
      memset ((char *) &si->other_in, 0, sizeof (si->other_in));
      memcpy ((char *) &si->other_in.sin_addr, (char *) si->hostent.h_addr,
	      si->hostent.h_length);
      si->other_in.sin_family = si->hostent.h_addrtype;
      si->other_in.sin_port = htons (atoi (si->socketname));

      if (connect (fd, (struct sockaddr *) &si->other_in,
		   sizeof (si->other_in)) >= 0)
	{
	  return (fd);		/* SUCCESS */
	}

      if ((errno == ECONNREFUSED) || (errno == ETIMEDOUT))
	{
	  /* The other side's system is probably overloaded with connection 
	   * requests.  So we sleep for awhile, then try again.
	   */
	  close (fd);
	  fd = -1;
	  max_retries *= 2;	/* increase time between attempts 1, 2, 4, 8, 16 */
	  continue;
	}

      /* An unexpected error occurred iin connect if we are here. */
      fprintf (stderr,
	       "make_connection: connect() to socket failed, errno = %d\n",
	       errno);
      return (-1);
    }


  /* Failed retries */
  fprintf (stderr,
	   "make_connection: failed to connect to super in 5 retries.\n");
  return (-1);
}

/*
 * slave_mode_init                                                            
 *                                                                            
 * arguments:                                                                 
 *     file   :  name of input file(in slave mode this is really the socket  
 *               address.                                                     
 *       si   :  pointer to information about out parent controller;          
 * returns/side-effects:                                                      
 *     file is modified to name an actual file we created with the input we   
 *     copied from the parent.                                                
 *     Returns 0 for success, -1 to indicate an error occurred.               
 * description:                                                               
 *    Open communications to parent controller via socket; "download" the     
 * test script from the parent and save it in a temp file;                    
 * NOTE:                                                                      
 * Need a detailed description of the behavior of slave mode and the          
 * requirements/expectations of the super controller.                         
 *                                                                            
 */

static int
slave_mode_init (char **file, struct super_info *si)
{
  int fd;
  int complete = 0;
  char *p;
  char *filenamespace;
  FILE *fp;
  struct hostent *host;
  struct timeval timeout;

  /* Sanity checking */
  if (master_id <= 0)
    {
      fprintf (stderr, "smode_init: invalid master_id (%d) given\n",
	       master_id);
      return (-1);
    }

  /* Connect to the parent socket.  Initially *FILE points to a string
   * containing the hostname:socket_num pair which identifies the socket
   * owned by the super controller which will feed us our information.
   * This routine is also responsible for creating the input file which 
   * contains the client commands, super sends us a customized version 
   * of its input.
   */
  p = strchr (*file, ':');
  if (p == NULL)
    {
      fprintf (stderr, "smode_init: Invalid Host:socket specified, %s\n",
	       *file);
      return (-1);
    }
  *p = '\0';			/* Stomp on the ':' to end the hostname portion */

  si->hostname = *file;		/* Save host name   */
  si->socketname = (p + 1);	/* save socket name */

  /* Compare our host with parents host.  If they are different, then
   * we are on another machine, which means we were started with rsh,
   * which also means we need to disconnect from the controlling
   * terminal and start a new session.
   */
  /* This check may be too weak. (c.f. "eclipse" & "eclipse.unisql") */
  if (strcmp (LocalHost, si->hostname) != 0)
    {
      setsid ();
    }


  /* get remote host info for parent */
  host = gethostbyname (*file);
  if (host == NULL)
    {
      fprintf (stderr, "smode_init: unknown host %s\n", *file);
      return (-1);
    }
  else
    {
      /* Save a copy of it */
      memcpy ((char *) &si->hostent, (char *) host, sizeof (*host));
    }

  /* Get connected to the super controllers socket */
  si->sock = q_make_connection (si);
  if (si->sock < 0)
    {
      fprintf (stderr,
	       "smode_init: could not connect to remote parent socket, host: %s, id: %s\n",
	       si->hostname, si->socketname);
      return (-1);
    }

  /* Create the name and file */
  filenamespace = (char *) mktemp ("qactl");
  if (filenamespace == NULL)
    {
      qactl_output (stderr, "smode_init: no temp files available");
      return (-1);
    }

  fp = fopen (filenamespace, "w");
  if (fp == (FILE *) NULL)
    {
      qactl_output (stderr,
		    "smode_init: unable to create file %s, errno = %d\n",
		    filenamespace, errno);
      return (-1);
    }
  fd = fileno (fp);

  /* Now copy the input script from the socket and store it in
   * the file.  Each concurrently running qactl'er will have its
   * own version of the test script to read.
   */
  do
    {
      int rval, nb;
      fd_set readmask;
      qamc_msg msgbuf;

      timeout.tv_sec = 300;
      timeout.tv_usec = 0;

      /* Do a select first, making sure that there is some data, so we
       * can time out if necessary.
       */
      FD_ZERO (&readmask);
      FD_SET (si->sock, &readmask);
      rval = select (FD_SETSIZE, &readmask, NULL, NULL, &timeout);
      if (rval < 0)
	{
	  qactl_output (stderr, "smode_init: error in select(), errno = %d\n",
			errno);
	  continue;
	}
      if (rval == 0)
	{
	  qactl_output (stderr,
			"smode_init: Timeout waiting for input script, giving up\n");
	  return (-1);
	}

      /* Get the next message from the socket */
      rval = qamc_recv (si->sock, &msgbuf, NULL);
      if ((rval < 0) || ((int) msgbuf.msglen < 0))
	{
	  qactl_output (stderr,
			"smode_init: recv() failure, #bytes %d, errno = %d\n",
			rval, errno);
	  return (-1);
	}
      /* Copy data to the file */
      switch (msgbuf.msgtype)
	{
	case QAMC_SMSG_XFER_CONTINUE:
	  nb = write (fd, msgbuf.msg, msgbuf.msglen);
	  if (nb != msgbuf.msglen)
	    {
	      qactl_output (stderr,
			    "smode_init: write() failure, errno = %d\n",
			    errno);
	      qactl_output (stderr, "smode_init: msg was %s\n", msgbuf.msg);
	      return (-1);	/* ABORT */
	    }
	  break;

	case QAMC_SMSG_XFER_COMPLETE:
	  if (msgbuf.msgtype == QAMC_SMSG_XFER_COMPLETE)
	    {
	      complete = 1;
	    }
	  break;

	default:
	  /* UNKNOWN TYPE */
	  qactl_output (stderr, "smode_init: unknown message type, %d\n",
			msgbuf.msgtype);
	  return (-1);
	   /*NOTREACHED*/ break;
	}
    }
  while (!complete);

  qactl_output (stdout, "Script download complete\n");

  fclose (fp);

  /* Return the name of the file to the caller, it needs it */
  *file = filenamespace;

  return (0);
}

/*
 * slave_mode_cleanup                                                         
 *                                                                            
 * arguments:                                                                 
 *     file   :  name of copy of input file to be deleted;                    
 *       si   :  pointer to information about out parent controller;         
 * returns/side-effects:                                                      
 *    Permanenly deletes file.                                                
 * description:                                                              
 *   Perform necessary slave mode cleanup.                                    
 * Warning: can no longer call qactl_output() so must log anything to syslog  
 */

/* 
 * Cannot call qactl_output() routine, have to log to the console
 * to be sure.
 */
static int
slave_mode_cleanup (char *file, struct super_info *si)
{
  int rc = 0;

/* Delete the script file in temp dir */
  if (unlink (file) != 0)
    {
      syslog (LOG_WARNING,
	      "smode_cleanup: unlink() file %s failed, errno = %d\n", file,
	      errno);
      --rc;
    }

/* Tell the socket not needed anymore */
  if (shutdown (si->sock, 2) != 0)
    {
      syslog (LOG_WARNING, "smode_cleanup: shutdown() failed, errno = %d\n",
	      errno);
      --rc;
    }

/* What else to do ? */

  return (rc);
}

int
main (int argc, char **argv)
{
  int db_error = NO_ERROR;
  static int async_mode = 0;	/* see also sync_mode */
  FILE *inp_file;
  int reason = 0;
  static char *inp_file_name;
  char command[COMMAND_BUFFER_SIZE] = { 0 };
  qamc_msg msg;
  int xtra_argc;
  char *xtra_args[MAX_XTRA_ARGS];
  const char *prog_name;

  ARGDEF arguments[] = {
    /* Optional */
    {"controller-mode", ARG_BOOLEAN, QAMC_CONTROLLER_MODE_NAME,
     (void *) &ctlr_mode, "enable subordinate mode, default stand-alone",
     NULL},
    {"id-number", ARG_INTEGER, "-ident", (void *) &master_id,
     "(subordinate mode) controller id name/number given by parent", NULL},
    {"short duration", ARG_INTEGER, "-short", (void *) &ShortDuration,
     "initial timeout period in seconds to wait for command to complete (default 30)",
     NULL},
    {"long duration", ARG_INTEGER, "-long", (void *) &LongDuration,
     "additional timeout period in seconds to wait for command to complete (default 300)",
     NULL},
    {"sync-mode", ARG_BOOLEAN, QAMC_SYNC_MODE_NAME, (void *) &sync_mode,
     "default, enable multiple controller syncronizing mode", NULL},
    {"async-mode", ARG_BOOLEAN, QAMC_ASYNC_MODE_NAME, (void *) &async_mode,
     "disable multiple controller syncronizing mode", NULL},

    /* Required */
    {"volume-name", ARG_STRING, NULL, (void *) &db_name,
     "DB volume name", NULL},
    {"input-source", ARG_STRING, NULL, (void *) &inp_file_name,
     "input file name, or in slave mode - super controller socket address <host>:<sid>",
     NULL},
    {"default-client-name", ARG_STRING, NULL, (void *) &ClientProgName,
     "default name of client simulation program, when none other specified",
     NULL},

    {NULL}
  };

  /* Parse the arguments to program * */
  if (args_parse_strings_new (arguments, argc, argv))
    {
      fprintf (stderr, "parse error\n");
      exit (1);
    }

  if (ClientProgName == NULL)
    {
      fprintf (stderr,
	       "Usage: %s [options] database-name input-scenario-name default-client-name\n",
	       argv[0]);
      exit (1);
    }

  if (strstr (ClientProgName, "qamysql") != NULL
      || strstr (ClientProgName, "qaoracle") != NULL)
    {
#if defined (_MYSQL) || defined (ORACLE)
      init_db_client ();
      db_error = login_db ((char *) Db_server_addr, (char *) Init_user_name,
			   (char *) Password, (char *) Db_name);
      if (db_error != NO_ERROR)
	{
	  fprintf (stderr, "couldn't connect to mysql or oracle server: %s",
		   error_message ());

	  exit (1);
	}
#endif

    }
  else if (strstr (ClientProgName, "qacsql") == NULL)
    {
      fprintf (stderr, "Error: unknown server to connect.\n");
      exit (1);
    }

  /* Capture any additional arguments for later use * */
  xtra_argc = copy_xtra_args (argc, argv, arguments, xtra_args);

  /* Some usage checks */
  if (!ctlr_mode && async_mode)
    {
      fprintf (stderr,
	       "Usage error: async mode only available with controller-mode\n");
      exit (1);
    }

  prog_name = argv[0];

  /* sync_mode is flag used by all other routines * */
  sync_mode = !async_mode;

  /* Determine our machines name. */
  if (gethostname (LocalHost, QAMC_MAXHOSTNAMELEN) < 0)
    {
      fprintf (stderr, "init: Unable to determine local host name\n");
      return (2);
    }

  /* Provide a location to jump to for early fatal problems before
   * all initialization has begun.  See the qactl_output function.
   */
  if ((reason = setjmp (main_env)) != 0)
    {
      fprintf (stderr,
	       "QACTL: Error occurred while logging output, aborting immediately, line: %d",
	       __LINE__);
      exit (reason);
    }

  /* Prepare to be controlled by a meta-controller, if necessary.
   * See also "superc" and related.
   */
  if (ctlr_mode)
    {
      if (slave_mode_init (&inp_file_name, &superi) < 0)
	{
	  fprintf (stderr, "qactl: unable to initialize slave mode\n");
	  exit (1);
	}
    }

  qactl_output (stdout, "MC: Attempting to restart master client.\n");

  /*
   * If client restart fails, make sure the server is restarted, and try again
   * This is needed since recovery tests kill the server quite frequently and
   * the server may be down after restarting a new test.
   * Don't need to restart mysqld services.
   */
#if defined (CUBRID)
  sleepms (100);

  db_error = db_restart (prog_name, 0, db_name);
  if ((db_error == ER_NET_CANT_CONNECT_SERVER)
      || (db_error == ER_BO_CONNECT_FAILED))
    {
      strcpy (command, "cubrid server start ");
      strcat (command, db_name);
      system (command);
      db_error = db_restart (prog_name, 0, db_name);
    }

  if (db_error != NO_ERROR)
    {
      qactl_output (stderr,
		    "ERROR! attempting to restart the database: %d,\n %s\n",
		    db_error, error_message ());
      exit (db_error);
    }

#endif

  /* This is the signal handler for broken pipes. */
  (void) signal (SIGPIPE, SIG_IGN);

  /* Prepare the client processes */
  db_error = client_init (inp_file_name, &inp_file, &qa_ProcTbl, xtra_args);
  if (db_error != NO_ERROR)
    {
      qactl_output (stderr,
		    "ERROR: Client initialization failed, db_error = %d\n",
		    db_error);

      shutdown_db ();

      exit (db_error);
    }

  reason = setjmp (main_env);
  if (reason == 0)
    {
      /* Perform the test */
      db_error = control_clients (&qa_ProcTbl, prog_name, db_name, inp_file);
      if (db_error != NO_ERROR)
	{
	  if (ctlr_mode)
	    {
	      /* Tell parent that we failed */
	      msg.sender_id = master_id;
	      msg.msgtype = QAMC_MSG_RESULTS;
	      strcpy (msg.msg, "FAILURE");
	      msg.msglen = strlen (msg.msg);
	      if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
		{
		  fprintf (stderr, "ERROR: Unable to send failure results\n");
		}
	    }
	  fclose (inp_file);

	  shutdown_db ();

	  free_procinfo_table (&qa_ProcTbl);
	  exit (db_error);
	}
      else if (ctlr_mode)
	{
	  /* Tell parent that we exited successfully */
	  msg.sender_id = master_id;
	  msg.msgtype = QAMC_MSG_RESULTS;
	  strcpy (msg.msg, "SUCCESS");
	  msg.msglen = strlen (msg.msg);
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      fprintf (stderr, "ERROR: Unable to send success results\n");
	    }
	}
    }
  else
    {
      /* If anyone longjumps here, trouble occurred so exit gracefully.
       * For now, the normal cleanup seems sufficient, in addition to
       * telling someone about the problem.
       */
      /* Note: May no longer write to socket because an error occurred */
      syslog (LOG_ERR, "QACTL: Fatal error %d occurred, halting\n", reason);
    }


  /* Close the sequence file */
  fclose (inp_file);

  if (num_alive_clients (&qa_ProcTbl) > 0)
    {
      kill_clients (&qa_ProcTbl);
    }

  free_procinfo_table (&qa_ProcTbl);

  shutdown_db ();

  if (ctlr_mode)
    {
      if (slave_mode_cleanup (inp_file_name, &superi) != 0)
	{
	  syslog (LOG_WARNING, "QACTL: slave mode cleanup had errors\n");
	  exit (1);
	}
    }

  /* Successful Completion, or some errors */
  exit (reason);
}

/*
 * client_init                                                                
 *                                                                            
 * arguments:                                                                 
 * inp_file_name : name of input file;                                        
 *      inp_file : FILE handle to return open to inp_file_name;               
 *    num_clients: number of client programs to spawn;                        
 *        clients: array containing info about each child process;            
 *     added_args: argv like array containing additional args to pass on;    
 * returns/side-effects:                                                      
 *                                                                            
 * description:                                                               
 *    Initialize our client programs.  For all scripts, the num_clients       
 * is the total number of client programs used.  In slave mode if there       
 * is more than one master, then the number (and names) of clients            
 * this controller is responsible for is less than the total number.          
 * For now, the super controller allocates and specifies exactly              
 * which client id's belong to each master controller.                        
 * Sneak a peek into the file looking for "num_clients = " to determine       
 * the TOTAL.  If we are in slave mode, then there should also be             
 * another setup command in the input which tells us which client             
 * id's we own.                                                               
 */

static int
client_init (const char *inp_file_name, FILE ** inp_file, PROCTBL * procTbl,
	     char *added_args[])
{
  int i, a1 = 0, a2 = 0;
  int cl_num;
  int num_clients;
  int local_cnt = 0;
  PROCINFO *proc;
  PRO_STATUS *local_states = NULL;
  char cl_buf[32];
  char *buffer_ptr, *ptr2;
  int args;
  const char *argvector[MAX_XTRA_ARGS + 16];
  const char *stmt;		/* stmt returned from csql interpreter */
  unsigned int status;

  /* Open the input file */
  *inp_file = fopen (inp_file_name, "r");
  if (*inp_file == NULL)
    {
      qactl_output (stdout, "unable to open input file: %s, errno = %d\n",
		    inp_file_name, errno);
      return (1);
    }

  /* get the first statement to learn number of clients */
  stmt = get_next_stmt (*inp_file);
  if (stmt == NULL)
    {
      (void) qactl_output (stdout, "ERROR: %s\n", error_message ());
      fclose (*inp_file);
      return (1);
    }

  /* Convert statement to lowercase; promote case-insensitivity. */
  qamc_unquoted_lowercase_conversion ((unsigned char *) stmt);

  buffer_ptr = strstr (stmt, "num_clients = ");
  if (buffer_ptr != NULL)
    {
      buffer_ptr += strlen ("num_clients = ");
      args = sscanf (buffer_ptr, "%d", &num_clients);
      if (args != 1)
	{
	  qactl_output (stderr, "ERROR: Could not read number of clients\n");
	  fclose (*inp_file);
	  return (1);
	}
    }
  else
    {
      num_clients = 1;
    }

  if (ctlr_mode)
    {
      if ((num_clients > 0) &&
	  (local_states = malloc (num_clients * sizeof (PRO_STATUS))) == NULL)
	{
	  qactl_output (stderr,
			"ERROR: Could not allocate memory for client info, errno = %d\n",
			errno);
	  fclose (*inp_file);
	  return (1);
	}
      /* For slave mode, assume we don't own the clients, unless told
       * otherwise.
       */
      for (i = 0; i < num_clients; i++)
	{
	  local_states[i] = OTHER_MC;
	}

      /* Check if next statement is a setup "client_names".  If so, then
       * it tells us which client id's we own.
       */
      if (num_clients > 0)
	{
	  if (!(stmt = get_next_stmt (*inp_file)))
	    {
	      qactl_output (stderr, "ERROR: could not read statement 2\n");
	      fclose (*inp_file);
	      return (1);
	    }

	  /* Convert statement to lowercase; promote case-insensitivity. */
	  qamc_unquoted_lowercase_conversion ((unsigned char *) stmt);

	  /* Determine names of clients we own. */
	  if ((buffer_ptr = strstr (stmt, "client_names = ")) != NULL)
	    {
	      buffer_ptr += strlen ("client_names = ");
	      while ((*buffer_ptr != '\0')
		     && (buffer_ptr < (stmt + strlen (stmt))))
		{
		  args = sscanf (buffer_ptr, " c%d", &cl_num);
		  if (args != 1)
		    {
		      qactl_output (stderr,
				    "ERROR: Syntax error parsing client_names, %s, rc = %d\n",
				    buffer_ptr, args);
		      if (local_states != NULL)
			{
			  free (local_states);
			  local_states = NULL;
			}
		      fclose (*inp_file);
		      return (1);
		    }
		  else
		    {
		      if ((cl_num < 0) || (cl_num > num_clients))
			{
			  qactl_output (stderr,
					"ERROR: Received client id number out of range %d\n",
					cl_num);
			  if (local_states != NULL)
			    {
			      free (local_states);
			      local_states = NULL;
			    }
			  fclose (*inp_file);
			  return (1);
			}
		      if (cl_num != 0)
			{
			  local_states[cl_num - 1] = PRE_INIT;
			  local_cnt++;
			}
		    }

		  if ((ptr2 = strchr (buffer_ptr, ',')) != NULL)
		    {
		      buffer_ptr = ptr2 + 1;
		    }
		  else if ((ptr2 = strchr (buffer_ptr, ';')) != NULL)
		    {
		      buffer_ptr = ptr2 + 1;
		    }
		  else
		    {
		      qactl_output (stderr,
				    "ERROR: Syntax error parsing next client id, %s\n",
				    buffer_ptr);
		      if (local_states != NULL)
			{
			  free (local_states);
			  local_states = NULL;
			}
		      fclose (*inp_file);
		      return (1);
		    }
		}		/* end WHILE */
	    }
	}
    }				/* end IF ctlr_mode */

  end_stmts ();

  /* Get back to the beginning of the file */
  if (fseek (*inp_file, 0L, 0) < 0)
    {
      qactl_output (stderr,
		    "ERROR: Failed trying to fseek() to start of file, errno = %d\n",
		    errno);
      return (1);
    }

  /* Initialize the PROCINFO table */
  if (initialize_procinfo_table (procTbl) == NULL)
    {
      qactl_output (stderr, "ERROR: Table initialization failed\n");
      return (1);
    }

  if (ctlr_mode)
    {
      qactl_output (stdout,
		    "INFO! Setting up %d clients (out of %d total).\n",
		    local_cnt, num_clients);
    }
  else
    {
      qactl_output (stdout, "INFO! Setting up %d clients.\n", num_clients);
    }

  argvector[a1++] = ClientProgName;
  argvector[a1++] = db_name;

  argvector[a1++] = (char *) "-cl";
  argvector[a1++] = cl_buf;
  argvector[a1] = NULL;

  /* Pass any additional command line arguments through to our children */
  for (a2 = 0; added_args[a2] != NULL; a1++, a2++)
    {
      argvector[a1] = added_args[a2];
    }
  argvector[a1] = NULL;

  for (i = 0; i < num_clients; i++)
    {
      /* Only initialize those clients we are responsible for. */
      if (!ctlr_mode || (local_states[i] == PRE_INIT))
	{
	  /* Note: setting cl_buf here we are assuming we know the next client
	   * number which start_process will allocate. 
	   */
	  sprintf (cl_buf, "%d", i + 1);
	  proc = start_process (procTbl, ClientProgName, argvector, 0);
	  if (proc == NULL)
	    {
	      /* Handle failures in starting all the child processes */
	      qactl_output (stderr,
			    "ERROR: Failed to create all %d clients, client %d failed\n",
			    local_cnt, i);
	      fclose (*inp_file);
	      kill_clients (procTbl);
	      if (ctlr_mode && local_states != NULL)
		{
		  free (local_states);
		  local_states = NULL;
		}
	      free_procinfo_table (procTbl);
	      shutdown_db ();
	      return (-1);
	    }
	  if (activate_proc (procTbl, proc) < 0)
	    {
	      qactl_output (stderr,
			    "WARNING: Unable to activate Client %d \n",
			    proc->id);
	    }
	  status = 0;
	  proc->statement_count = 1;	/* expect 1 "is ready" message back */
	  wait_ready_command (proc->id, procTbl,
			      (char *)
			      "client_init: Initial Client readiness test",
			      &status, 1);
	}
      else
	{
	  /* This client process is owned by another master controller,
	   * so we save a slot and mark it as OTHER_MC.  This is important,
	   * because it keeps our internal client id numbers consistent with
	   * what the input script is expecting.
	   */
	  proc = allocate_proc (procTbl);
	  if (proc == NULL)
	    {
	      qactl_output (stderr,
			    "ERROR: Failed to allocate client to other MC\n");
	      kill_clients (procTbl);
	      free_procinfo_table (procTbl);
	      if (ctlr_mode)
		{
		  free (local_states);
		  local_states = NULL;
		}
	      shutdown_db ();
	      return (-1);
	    }
	  else
	    {
	      SetProcState (procTbl, proc->id, OTHER_MC);
	      SetProcExitStatus (procTbl, proc->id, 0);
	      deallocate_proc (procTbl, proc);
	    }
	}
    }				/* end FOR */

  if (ctlr_mode && local_states != NULL)
    {
      free (local_states);
      local_states = NULL;
    }
  return (NO_ERROR);
}

/*
 *                     UTILITY FUNCTIONS                                     
 */

/*
 * qactl_output                                                               
 *                                                                            
 * arguments:                                                                 
 *        fp  :  FILE handle;                                               
 *       fmt  :  printf style format string;                                
 *       ...  :  depends on format string ...                                 
 * returns/side-effects:                                                      
 *                                                                            
 * description:                                                               
 *   Common routine to send output to the stdout or stderr if we             
 * are running stand-alone, or else send the data down the socket             
 * to the super controller if we are running in ctlr_mode.                    
 * Every call to output any data to the user must go through this             
 * routine, because it is the only way to work in both stand-alone or        
 * slave mode.                                                                
 * Fatal errors result in an abort and a longjump back to main()              
 * for cleanup purposes.                                                      
 *                                                                            
 * When calling this routine, the caller should pretend it is                
 * calling fprintf() and still specify 'stdout' or 'stderr'.  This            *
 * routine will reroute it if necessary to the socket.                        
 */
static int
qactl_output (FILE * fp, const char *fmt, ...)
{
  va_list args;
  int rc;
  qamc_msg msg;

  va_start (args, fmt);
  if (ctlr_mode)
    {
      msg.sender_id = master_id;
      (void) vsprintf (msg.msg, fmt, args);
      msg.msglen = strlen (msg.msg);
      if (msg.msglen > QAMC_MAX_MSGLEN)
	{
	  /* Something really horrible just happened, we overwrote
	   * the msg buffer.  Tell super and then quit gracefully.
	   */
	  syslog (LOG_ERR,
		  "QACTL controller internal memory corrupted, bailing out, len: %d\n",
		  msg.msglen);
	  fprintf (stderr,
		   "QACTL controller internal memory corrupted, bailing out, len: %d\n",
		   msg.msglen);
	  strcpy (msg.msg,
		  "QACTL controller internal memory corrupted, bailing out\n");
	  msg.msglen = strlen (msg.msg);
	  msg.msgtype = QAMC_MSG_ERROR;
	  if (qamc_send (superi.sock, &msg, NULL) != msg.msglen)
	    {
	      fprintf (stderr,
		       "qactl_output: qamc_send() failure, Len: %d, Msg: %s\n",
		       msg.msglen, msg.msg);
	    }

	  longjmp (main_env, FATAL_MEMORY_OVERFLOW);
	}
      else
	{
	  /* Normal case */
	  if (fp == stdout)
	    {
	      msg.msgtype = QAMC_MSG_DATA_STDOUT;
	    }
	  else
	    {
	      msg.msgtype = QAMC_MSG_DATA_STDERR;
	    }
	}

      /* send it */
      rc = qamc_send (superi.sock, &msg, NULL);
      if (rc != msg.msglen)
	{
	  fprintf (stderr,
		   "qactl_output: qamc_send() failure, rc = %d, errno = %d, len: %d, msg: %s\n",
		   rc, errno, msg.msglen, msg.msg);
	  longjmp (main_env, FATAL_SEND_FAILURE);
	}
      return (0);
    }
  else
    {
      rc = vfprintf (fp, fmt, args);
    }

  fflush (fp);
  va_end (args);

  return (rc);
}

/*
 * sigterm_handler                                                          
 *                                                                          
 * arguments:                                                               
 *      sig   :  sig                                                        
 * returns/side-effects:                                                    
 *   Immediately stop this controller and all its children.                 
 * description:                                                             
 *   Signal handler for the SIGTERM signal.                                 
 *   Do something sensible when we get a TERM signal, like clean up the     
 * children and shutdown the database.                                      
 */

static void
sigterm_handler (int sig)
{
  qactl_output (stdout,
		"ERROR: Master controller %d halting because of SIGTERM.\n",
		master_id);

  longjmp (main_env, CONTROLLER_SIGTERM);
}

/*
 * stopandstart_handler                                                     
 *                                                                          
 * arguments:                                                               
 *      sig   :  sig                                                        
 * returns/side-effects:                                                    
 *    Ensures that all of our children also receive the SIGSTOP and SIGCONT 
 * signals.                                                                 
 * description:                                                             
 *   Signal handler for stopping and starting the test in the middle.       
 */

static void
stopandstart_handler (int sig)
{
  if (sig == SIGTSTP)
    {
      /* Send STOP signal to all children clients, then STOP ourselves
       * because we are the session and process group owner, this should
       * also STOP all of our children
       */
      qactl_output (stdout, "qactl controller pausing because of SIGTSTP.\n");
      kill (getpid (), SIGSTOP);
    }
  else if (sig == SIGCONT)
    {
      qactl_output (stdout, "qactl controller restarted with SIGCONT.\n");
    }
  else
    {
      qactl_output (stderr, "Controller got unexpected signal (%d)\n", sig);
    }

  return;
}


/*
 * copy_xtra_args                                                             
 *                                                                            
 * arguments:                                                                 
 *      argc  :  number of arguments in argv;                                 
 *      argv  :  array of UNIX style arguments;                               
 *  arguments :  array of ARGDEF style arguments;                             
 *  xtra_args :  room to copy previously unidentified args to;                
 * returns/side-effects:                                                      
 *   Returns the number of extra arguments saved in xtra_args.                
 * description:                                                               
 *   Extract all extra command line arguments from argv[] which are not       
 * part of our arguments, to the xtra-arg buffer.  They caller uses these     
 * to pass them on to the children we exec.                                   
 *                                                                            
 */

static int
copy_xtra_args (int argc, char *argv[], ARGDEF arguments[], char *xtra_args[])
{
  int i;
  int x_count = 0;
  int index;
  int found;


  for (i = 1; i < argc; i++)
    {
      index = 0;
      found = 0;
      while ((found != 1) && (arguments[index].name != NULL))
	{
	  if ((arguments[index].key != NULL))
	    {
	      if ((strcmp (arguments[index].key, argv[i]) == 0))
		{
		  found = 1;
		  if (arguments[index].type != ARG_BOOLEAN)
		    {
		      i += 1;	/* Skip argument to this option also */
		    }
		}
	    }
	  else if (strcmp (*(char **) arguments[index].variable, argv[i]) ==
		   0)
	    {
	      found = 1;
	    }
	  index++;
	}

      if (!found)
	{
	  xtra_args[x_count++] = argv[i];
	  if (x_count >= MAX_XTRA_ARGS)
	    {
	      qactl_output (stderr,
			    "Fatal Error: More than %d extra arguments\n",
			    MAX_XTRA_ARGS);
	      exit (99);
	    }
	}
    }

  xtra_args[x_count] = NULL;
  return (x_count);
}


/*
 * make_argvfromstring                                                        
 *                                                                            
 * arguments:                                                                 
 *   arg_ptrs : argument vector to fill in                                    
 *   ptr      : input string to be split                                      
 *                                                                            
 * returns/side-effects:                                                      
 *   return the number of elements stored into the vector.                    
 * Note: This routine modifies the input string by breaking it into sub-      
 * strings, it doesn't alloc new space, so the caller mustn't modify the      
 * input strings until the argvector is no longer in use.                     
 *                                                                            
 * description:                                                               
 *   Create an argv style vector pointing to the substrings of the input.     
 * Caveat:  For not, it doesn't understand quoting and such so embedded       
 * blanks may confuse the arg list.  This should be fixed, if the need arises.
 */

static int
make_argvfromstring (const char *arg_ptrs[], char *ptr)
{
  int index = 0;

  ptr += strspn (ptr, " 	\n");	/* skip whitespace: Space Tab Newline */
  arg_ptrs[index++] = ptr;
  while ((ptr = strchr (ptr, ' ')) != NULL)
    {
      *ptr++ = '\0';

      ptr += strspn (ptr, " 	\n");	/* skip whitespace: Space Tab Newline */
      arg_ptrs[index++] = ptr;
    }

  arg_ptrs[index] = NULL;	/* last one */
  return (index);
}

/*
 *           PROCESS TABLE MAINTENANCE AND ALLOCATION ROUTINES               
 */

/*
 * initialize_procinfo_table                                                               
 *                                                                            
 * arguments:                                                                 
 *   pt       : pointer to a table containing all known proc information.     
 *                                                                            
 * returns/side-effects:                                                      
 *                                                                            
 * description:                                                               
 *   This routine initializes a PROCTBL.                                      
 * A PROCTBL contains all relevant information about all sub-processes we have
 * initiated.  These are sometimes referred to as clients or children through 
 * this documentation.                                                        
 * To begin with, a PROCTBL consists of two major items.  First is an array   
 * used to find a given proc efficiently given only its client it.  This      
 * array is as 'allprocs'.  Second, a linked list of currently active         
 * clients helps us keep track of those children we expect to receive data    
 * from.  The remaining elements of the table are mainly bookkeeping type     
 * things for these two main elements.  It should be noted that conceptually  
 * the number of children we may wish to spawn is unlimited, therefore the    
 * allprocs array must grow dynamically, and since the active list is really  
 * a linked list, it can obviously grow as well.                              
 * See the macros "GetProcInfo", "SetProcState" and "GetProcState" for ideas  
 * of how to access the process information.                                  
 *                                                                            
 */

static PROCTBL *
initialize_procinfo_table (PROCTBL * pt)
{
  PROCSLOT *slots;

  slots = malloc (sizeof (PROCSLOT) * PROC_GROWTH_STEP);
  if (slots == NULL)
    {
      return (NULL);
    }

  pt->maxslots = PROC_GROWTH_STEP;
  pt->num_active = 0;
  pt->slotsinuse = 0;
  pt->active = NULL;
  pt->free = NULL;
  pt->allprocs = slots;

  return (pt);
}

/*
 * free_procinfo_table                                                               
 *                                                                            
 * arguments:                                                                 
 *   pt       : pointer to a table containing all known proc information.     
 *                                                                            
 * returns/side-effects:                                                      
 *                                                                            
 * description:                                                               
 *   Free everything allocated to the PROCTBL.                                
 */
static void
free_procinfo_table (PROCTBL * pt)
{
  int i;
  PROCINFO *proc;
  int rc;
  UD_TYPE ud;
  char docmd[2048];

  if (pt == NULL)
    {
      return;
    }

  /* free up the free list */
  while (pt->free != NULL)
    {
      proc = pll_drop (&pt->free, NULL);
      if (proc != NULL)
	{
	  free (proc);
	  proc = NULL;
	}
    }

  /* free up any remaining slots */
  if (pt->allprocs != NULL)
    {
      for (i = 0; i < pt->slotsinuse; i++)
	{
	  if (pt->allprocs[i].procinfo != NULL)
	    {
	      free (pt->allprocs[i].procinfo);
	      pt->allprocs[i].procinfo = NULL;
	    }
	  if (pt->allprocs[i].userdata != NULL)
	    {
	      ud = ((struct stress_data *) pt->allprocs[i].userdata)->udtype;
	      switch (ud)
		{
		case UD_STRESSDATA:
		  if (((struct stress_data *) pt->allprocs[i].userdata)->
		      didgen)
		    {
		      sprintf (docmd, "%s clean",
			       ((struct stress_data *) pt->allprocs[i].
				userdata)->runfilename);
		      rc = system (docmd);
		      if (rc < 0 || WEXITSTATUS (rc))
			{
			  qactl_output (stderr,
					"ERROR! system() command failed for: %s, rc = %d, errno = %d\n",
					docmd, WEXITSTATUS (rc), errno);
			}
		    }
		  break;

		default:
		  qactl_output (stderr,
				"ERROR! Unknown user data type discovered during cleanup, %d\n",
				ud);
		}		/* END switch */
	      free (pt->allprocs[i].userdata);
	      pt->allprocs[i].userdata = NULL;

	    }			/* END if */
	}			/* END for */
      free (pt->allprocs);
      pt->allprocs = NULL;
    }

  return;
}


/*
 * deallocate_proc                                                                
 *                                                                            
 * arguments:                                                                 
 *   pt       : pointer to a table containing all known proc information.     
 *   proc     : a proc structure we wish to deallocate.                       
 *                                                                            
 * returns/side-effects:                                                      
 *   Return -1 if we fail for some reason, otherwise return zero.             
 * The procinfo field of allprocs is set to NULL, to avoid attempts to        
 * dereference the block of memory which we are destroying.                   
 *                                                                            
 * description:                                                               
 *   Deallocate a given PROC structure.  This is only done when the process   
 * described by the structure is gone.  Before calling this function, the     
 * PROC must be freestanding, and must not be on any other lists (e.g. the    
 * active list.                                                               
 *   Procs are freed for reuse by placing them on the "free" list.            
 */

static int
deallocate_proc (PROCTBL * pt, PROCINFO * proc)
{
  if ((proc == NULL) || (pt == NULL) || (pt->allprocs == NULL))
    {
      return (0);
    }

  /* Don't point to it any more from the main index */
  pt->allprocs[proc->id - 1].procinfo = NULL;

  proc->id = -1;
  proc->left = proc->right = NULL;
  proc->pid = proc->write_fd = proc->read_fd = proc->error_fd = -1;
  proc->client_type = CL_UNKNOWN;
  proc->cl_output_filter = NULL;

  if (pll_add (&pt->free, proc) < 0)
    {
      return (-1);
    }

  return (0);
}

/*
 * allocate_proc                                                                  
 *                                                                            
 * arguments:                                                                 
 *   pt       : pointer to a table containing all known proc information.     
 *                                                                            
 * returns/side-effects:                                                      
 *   Return NULL if we fail to allocate a proc, a ptr to PROCINFO if we       
 * succeed.  This routine gives each client a unique "id" number, from 1      
 * to the number of clients, N.                                               
 *                                                                            
 * description:                                                               
 *   Allocate the next slot (sequentially) in the allprocs list, and get a    
 * free PROCINFO.  Allocation is where the client id numbers come from.       
 * Specifically the client id number - 1 is the array offset into allprocs.   
 * The allprocs array grows when full.  We attempt to use any blocks on the   
 * "free" list, (see deallocate_proc) before being forced to malloc() a new one.  
 */

static PROCINFO *
allocate_proc (PROCTBL * pt)
{
  PROCSLOT *tmpslots;
  PROCINFO *pinfo;

  /* Check if full, if so realloc more space */
  if (pt->slotsinuse >= pt->maxslots)
    {
      tmpslots =
	realloc (pt->allprocs,
		 (pt->maxslots + PROC_GROWTH_STEP) * sizeof (PROCSLOT));
      if (tmpslots == NULL)
	{
	  return (NULL);
	}
      else
	{
	  pt->maxslots += PROC_GROWTH_STEP;
	  pt->allprocs = tmpslots;
	}
    }

  /* Get a PROCINFO block.  If one is on the free list, use it, otherwise
   * malloc a new one.
   */
  pinfo = pll_drop (&pt->free, NULL);
  if (pinfo == NULL)
    {
      pinfo = malloc (sizeof (PROCINFO));
      if (pinfo == NULL)
	{
	  return (NULL);
	}
    }

  /* Now we have a PROCINFO, and a SLOT */
  pt->allprocs[pt->slotsinuse++].procinfo = pinfo;
  /* id must be cardinal number corresponding to the array offset in allprocs */
  pinfo->id = pt->slotsinuse;
  SetProcState (pt, pinfo->id, PRE_INIT);
  SetProcUserData (pt, pinfo->id, NULL);
  SetProcExitStatus (pt, pinfo->id, 0xABABCBCB);

  /* Initialize the pinfo */
  pinfo->left = pinfo->right = NULL;
  pinfo->pid = pinfo->write_fd = pinfo->read_fd = pinfo->error_fd = -1;
  pinfo->client_type = CL_UNKNOWN;
  pinfo->cl_output_filter = NULL;

  return (pinfo);
}

/*
 * Return the number of nodes on the list.  Mainly used for error checking.
 */
static int
count_nodes (PROCINFO ** proc_ll)
{
  PROCINFO *proc = NULL;
  int cnt = 0;

  if ((proc_ll == NULL) || (*proc_ll == NULL))
    {
      return (cnt);
    }

  proc = (*proc_ll);
  do
    {
      proc = proc->right;
      cnt++;
    }
  while (proc != (*proc_ll));

  return (cnt);
}

/*
 * activate_proc                                                               
 *                                                                            
 * arguments:                                                                 
 *   pt       : pointer to a table containing all known proc information.     
 *   proc     : a proc structure we wish to activate                          
 *                                                                            
 * returns/side-effects:                                                      
 *   Return -1 if we fail to activate the proc, a zero if we succeed.         
 * Also keeps accurate count of the number of currently active procs.         
 *                                                                            
 * description:                                                               
 *   An "active" proc is one which is on the "active" queue of the PT. Only   
 * those on the active queue will have their output read, etc.  We assume     
 * that the proc given is freestanding, and not already on any other queues.  
 * The PROC given must first be allocated with the allocate_proc routine.         
 */
static int
activate_proc (PROCTBL * pt, PROCINFO * proc)
{
  int cn;

  if ((pt == NULL))
    {
      return (-1);
    }

  if (pll_add (&pt->active, proc) < 0)
    {
      return (-1);
    }

  pt->num_active++;
  cn = count_nodes (&pt->active);
  if (pt->num_active != cn)
    {
      qactl_output (stderr,
		    "WARNING activate_proc: Count %d doesn't match %d nodes\n",
		    pt->num_active, cn);
      return (-1);
    }

  return (0);
}


/*
 * deactivate_proc                                                             
 *                                                                            
 * arguments:                                                                 
 *   pt       : pointer to a table containing all known proc information.     
 *   proc     : a proc structure we wish to deactivate                        
 *                                                                            
 * returns/side-effects:                                                      
 *   Return -1 if we fail to deactivate the proc, a zero if we succeed.      
 * Also keeps accurate count of the number of currently active procs.         
 *                                                                            
 * description:                                                               
 *   Deactivation is defined as removing from the "active" queue.  Procs which
 * are not on the active queue will not have their output read, etc.  We      
 * assume that the proc given is on the active queue, otherwise it is error.  
 * Caller is responsible for disposing of the procinfo, otherwise it will     
 * be left dangling.  For example, this routine is not responsible for freeing
 * memory or putting the proc back on the free list for reuse.                
 */
static int
deactivate_proc (PROCTBL * pt, PROCINFO * proc)
{
  int cn;

  if ((pt == NULL))
    {
      return (-1);
    }

  if (pll_drop (&pt->active, proc) == NULL)
    {
      return (-1);
    }

  pt->num_active--;
  if (pt->num_active != (cn = count_nodes (&pt->active)))
    {
      qactl_output (stderr,
		    "WARNING deactivate_proc: Count %d doesn't match %d nodes\n",
		    pt->num_active, cn);
      return (-1);
    }

  return (0);
}


/*
 * pll_add                                                                    
 *                                                                            
 * arguments:                                                                 
 *   proc_ll  : addr of pointer to start of a linked list;                    
 *   proc     : procinfo structure we wish to add to the list.                
 *                                                                            
 * returns/side-effects:                                                      
 *   Adds a new node to the list.                                             
 * The PROC_LL pointer may be moved when necessary.                           
 *                                                                            
 * description:                                                               
 *   Perform insertion of one node on a doubly-linked list.  Note that this   
 * list is neither LIFO or FIFO, and the order the nodes will be removed must 
 * not matter to the caller.                                                  
 */
static int
pll_add (PROCINFO_PTR * proc_ll, PROCINFO_PTR proc)
{
  PROCINFO_PTR one_removed;

  if ((proc_ll == NULL) || (proc == NULL))
    {
      return (-1);
    }

  /* Case 1: It was empty ... */
  if (*proc_ll == NULL)
    {
      proc->left = proc;
      proc->right = proc;
      *proc_ll = proc;
      return (0);
    }

  /* Case 2: It had more than one thing ...
   * Note: we always insert one to the right from the beginning node,
   * this makes insertion a little easier.
   */
  one_removed = (*proc_ll)->right;

  proc->right = (*proc_ll)->right;
  proc->left = one_removed->left;
  (*proc_ll)->right = proc;
  one_removed->left = proc;

  return (0);
}


/*
 * pll_drop                                                                   
 *                                                                            
 * arguments:                                                                 
 *   proc_ll  : addr of pointer to start of a linked list;                    
 *   specific : procinfo structure we wish to remove from the list.           
 *                                                                            
 * returns/side-effects:                                                      
 *   Removes SPECIFIC from the list, or else removes the first one.           
 * The PROC_LL pointer may be moved when necessary.                           
 *                                                                            
 * description:                                                               
 *   Perform a deletion of one node from a doubly-linked list.  If given,     
 * the caller can specify a SPECIFIC node to delete, otherwise the first      
 * element is the one removed.                                                
 */
static PROCINFO *
pll_drop (PROCINFO ** proc_ll, PROCINFO * specific)
{
  PROCINFO *proc = NULL;

  if ((proc_ll == NULL) || (*proc_ll) == NULL)
    {
      return (NULL);
    }

  /* Caller can request we drop a specific procinfo.  To do that with
   * the current cirular ll implementation, we simply move the "start" pointer
   * to the one they want and voila, the normal delete code will get rid
   * of that node.
   */
  if (specific != NULL)
    {
      /* First make sure that the one they ask for was actually on the
       * list, before we delete it. If not, it is an error.
       */
      proc = (*proc_ll);
      while (proc != specific)
	{
	  proc = proc->right;
	  if (proc == (*proc_ll))
	    {
	      return (NULL);	/* We have wrapped w/out finding it */
	    }
	}

      /* after we find it, move the start pointer */
      (*proc_ll) = specific;
    }

  /* Case 1: Removing the last entry ... */
  if (((*proc_ll) == (*proc_ll)->right) &&
      ((*proc_ll)->right == (*proc_ll)->left))
    {
      proc = (*proc_ll);
      assert (proc != NULL);
      (*proc_ll) = NULL;
    }
  else
    {
      /* Case 2: Removing one of many entries ... */
      proc = (*proc_ll);
      assert (proc != NULL);
      (*proc_ll) = proc->right;
      proc->right->left = proc->left;
      proc->left->right = proc->right;
    }

  proc->left = proc->right = NULL;
  return (proc);
}

/*
 * Dump ProcTbl, for debugging purposes.
 */
void
dump_procinfo_table (PROCTBL * pt)
{
  int i;
  PROCINFO *proc;

  if (pt == NULL)
    {
      pt = &qa_ProcTbl;		/* By default, use the global one */
    }


  fprintf (stdout,
	   "*******************************************************\n");
  fprintf (stdout, "Dump of QACTL %d ProcInfo Table:\n", master_id);
  if (pt->allprocs == NULL)
    {
      fprintf (stdout, "*** Table NEVER initialized. ***\n");
      return;
    }

  fprintf (stdout, "Num Active: %d	SlotsInUse: %d\n", pt->num_active,
	   pt->slotsinuse);

  /* dump info about each one */
  for (i = 1; i <= pt->slotsinuse; i++)
    {
      fprintf (stdout, "  Slot: %d	Status: %d\n", i,
	       GetProcState (pt, i));
      proc = GetProcInfo (pt, i);
      if (proc != NULL)
	{
	  fprintf (stdout, "    Id: %d	Pid: %d	TranIndex: %d\n", proc->id,
		   proc->pid, proc->tran_index);
	  fprintf (stdout, "    Remaining Statements: %d\n",
		   proc->statement_count);
	  fprintf (stdout, "    WFD: %d, RFD: %d, EFD: %d\n", proc->write_fd,
		   proc->read_fd, proc->error_fd);
	}
      else
	{
	  fprintf (stdout, "    NO PROC-INFO\n");
	}
    }

  return;
}

/*
 *                 EXECUTION COMMAND AND SUBCOMMAND ROUTINES                 
 */

/*
 * exec_stressexec                                                            
 *                                                                            
 * arguments:                                                                 
 *   procTbl  : pointer to a table containing all known proc information.     
 *   cmd_ptr  : remainder of the input string.                                
 *  start_line: input line count (used for error messages)                    
 *    delay   : specify the delay in seconds                                  
 *                                                                            
 * returns/side-effects:                                                      
 *   Return -1 for error, 0 if successful.                                    
 *                                                                            
 * description:                                                               
 *   Do the necessary pre and post processing top execute the MC stress-exec  
 * program (mc_exec.sh).  Before this is called, the input script should have 
 * already invoked exec_stressgen to build the necessary test files.  The     
 * client id number given (Cxxx) must correspond to the client id number      
 * allocated to the stressgen client.                                         
 *   The stressexec program is run asynchronously in the background and its   
 * output is captured and formatted like any other child process.             
 *   Some provision needs to be made for automatically cleaning up after      
 * the execution(s) are complete.  The question is how?                       
 * Expected syntax:                                                           
 *   execute [exec-options] mc_exec.sh using Cxxx;                            
 *                                                                            
 */
static int
exec_stressexec (PROCTBL * procTbl, char *cmd_ptr, int start_line, int delay)
{
  const char *argvec[MAX_XTRA_ARGS];
  int argi = 0;
  PROCINFO *proc;
  struct stress_data *xdata;
  int client_no;
  unsigned int status;

  if ((procTbl == NULL) || (procTbl->allprocs == NULL))
    {
      return (-1);
    }

  /* Find out which client number */
  if (strncasecmp (cmd_ptr, "using", strlen ("using")) == 0)
    {
      cmd_ptr += strlen ("using");
      skip_white_comments (&cmd_ptr);
      if (sscanf (cmd_ptr, "%*[cC]%d", &client_no) != 1)
	{
	  qactl_output (stderr,
			"Syntax ERROR! Expecting client id '[Cc][0-9]*', instead of %s\n",
			cmd_ptr);
	  return (-1);
	}
      if (!VALID_CLIENT_ID (procTbl, client_no))
	{
	  qactl_output (stderr,
			"ERROR! Client value given (%d) is out of range.\n",
			client_no);
	  return (-1);
	}
    }
  else
    {
      qactl_output (stderr,
		    "Syntax ERROR! Expected token 'using' next, instead of %s\n",
		    cmd_ptr);
      return (-1);
    }


  /* Insure that the client was FINISHED before we can continue ... */
  status = 0;
  wait_finish_command (client_no, procTbl,
		       (char *)
		       "exec_stressexec: Insure mc_gen client completed.",
		       &status, 1);

  if (status != 0)
    {
      qactl_output (stderr,
		    "ERROR! Client %d never completed its initialization, cannot execute.\n",
		    client_no);
      return (-1);
    }

  /* Get the necessary information from the mc_gen command */
  xdata = (struct stress_data *) GetProcUserData (procTbl, client_no);
  if ((xdata == NULL) || (xdata->didgen == 0))
    {
      qactl_output (stderr,
		    "ERROR! Must run mc_gen.sh on client %d before attempting mc_exec.sh\n",
		    client_no);
      return (-1);
    }

  argi = make_argvfromstring (&argvec[argi], xdata->runfilename);
  argi += make_argvfromstring (&argvec[argi], (char *) db_name);

  proc = start_process (procTbl, argvec[0], argvec, delay);
  if (proc == NULL)
    {
      qactl_output (stdout,
		    "ERROR! (input line %d) Failed to exec new process, %s\n",
		    start_line, cmd_ptr);
    }
  if (activate_proc (procTbl, proc) < 0)
    {
      qactl_output (stderr,
		    "WARNING: [line %d] Unable to activate %s Client %d\n",
		    __LINE__, argvec[0], proc->id);
      return (-1);
    }
  return (0);
}

/*
 * exec_stressgen                                                             
 *                                                                            
 * arguments:                                                                 
 *   procTbl  : pointer to a table containing all known proc information.     
 *   cmd_ptr  : remainder of the input string.                                
 *  start_line: input line count (used for error messages)                    
 *    delay   : specify the delay in seconds                                  
 *                                                                            
 * returns/side-effects:                                                      
 *   Return -1 for error, 0 if successful.                                    
 * The stress-gen program will create subdirectories and files in the current 
 * directory.                                                                 
 *                                                                            
 * description:                                                               
 *   Do the necessary pre and post processing top execute the MC stress-gen   
 * program (mc_gen.sh).   This routine keeps a copy of the parameters for     
 * later use by the exec_stressexec function when beginning an execution.     
 *                                                                            
 * Expected syntax:                                                           
 *   execute [exec-options] mc_gen.sh {gen-args};                             
 *                                                                            
 */
static int
exec_stressgen (PROCTBL * procTbl, char *cmd_ptr, int start_line, int delay)
{
  char pcmd[PATH_MAX * 4];
  const char *arg_ptrs[MAX_XTRA_ARGS];
  int argi = 0;
  PROCINFO *proc;
  int rc;
  struct stress_data *xdata;

  xdata = malloc (sizeof (struct stress_data));
  if (xdata == NULL)
    {
      qactl_output (stderr,
		    "ERROR! Unable to allocate space for mcstress data\n");
      return (-1);
    }
  else
    {
      xdata->didgen = 0;
    }

  xdata->udtype = UD_STRESSDATA;
  sscanf (cmd_ptr, "%d %s %d", &xdata->numfiles, xdata->templatename,
	  &xdata->numclients);

  argi = make_argvfromstring (arg_ptrs, (char *) STRESSGEN_CMDNAME);
  argi += make_argvfromstring (&arg_ptrs[argi], (char *) "-x");
  argi += make_argvfromstring (&arg_ptrs[argi], cmd_ptr);
  if (argi > MAX_XTRA_ARGS)
    {
      qactl_output (stderr,
		    "INTERNAL ERROR! Oops, overran array by %d elements (prog line: %d)\n",
		    __LINE__, argi - MAX_XTRA_ARGS);
      return (-1);		/* should probably be fatal */
    }

  strcpy (pcmd, STRESSGEN_CMDNAME);

  /* Allocate internal process resource info */
  proc = start_process (procTbl, pcmd, arg_ptrs, delay);
  if (proc != NULL)
    {
    repeat_read:
      rc = read (proc->read_fd, pcmd, PATH_MAX);
      if (rc < 0)
	{
	  if (errno == EINTR)
	    {
	      goto repeat_read;
	    }
	  qactl_output (stderr,
			"ERROR! internal read() failed for cmd on line %d of input (bytes = %d, errno = %d).\n",
			start_line, rc, errno);
	  kill_aclient (procTbl, proc->id);
	  return (-1);
	}
      else
	{
	  /* Get the name of the executable shell script which runs the test */
	  if (sscanf (pcmd, "%s", xdata->runfilename) <= 0)
	    {
	      qactl_output (stderr,
			    "ERROR! input parsing failed for cmd on line %d, expected a runfile name, got %s instead.\n",
			    start_line, cmd_ptr);
	      kill_aclient (procTbl, proc->id);
	      return (-1);
	    }
	  else
	    {
	      xdata->didgen = 1;
	    }
	}
    }				/* end IF */
  else
    {
      /* Couldn't start the process for some reason * */
      qactl_output (stderr,
		    "ERROR! Unable to start process for cmd on line %d of input.\n",
		    start_line);
      return (-1);
    }

  /* Save xdata for use by the corresponding mc_exec call. * */
  SetProcUserData (procTbl, proc->id, xdata);

  if (activate_proc (procTbl, proc) < 0)
    {
      return (-1);
    }
  SetProcState (procTbl, proc->id, READY);
  return (0);
}


/*
 * execute_command                                                            
 *                                                                            
 * arguments:                                                                 
 *      cmd_ptr:        ptr to input command string after word "rendezvous".  
 *      procTbl:        Table of all client data.                             
 *      start_line:     Line number of input command.                         
 *	command_buffer: The complete command string.                          
 *	status:         Zero = ok, non-zero = failure.                        
 * returns/side-effects:                                                      
 *	Returns 0                                                             
 * description:                                                               
 *	Execute another program, either synchronously, or asynchronously      
 *	using the optional 'background' keyword.   The expected input looks   
 *	like this:                                                            
 *	  execute [time-delay] cmd-token [args] ...                           
 *	Where 'time-delay' specifies how many seconds to delay prior to exec, 
 *	'cmd-token' specifies either a known program, for which this function 
 *	may take extra steps such as bookkeeping, cleanup or other things as  
 *	necessary, or else may specify any command which will be executed by  
 *	the system() call.  And 'args' are passed to the command when invoked.
 */

static void
execute_command (char *cmd_ptr, PROCTBL * procTbl, int start_line,
		 char command_buffer[COMMAND_BUFFER_SIZE],
		 unsigned int *status)
{
  char *ptr;
  int tdelay = 0;
  PROCINFO *proc;
  const char *argvec[MAX_XTRA_ARGS + 16];

  *status = 0;
  skip_white_comments (&cmd_ptr);

  /* Parse the time delay option. */
  if (strncasecmp (cmd_ptr, TDELAY_TOKEN, strlen (TDELAY_TOKEN)) == 0)
    {
      cmd_ptr += strlen (TDELAY_TOKEN);
      tdelay = strtol (cmd_ptr, &ptr, 10);
      cmd_ptr = ptr;
      skip_white_comments (&cmd_ptr);
    }

  /* Stomp on the trailing semicolon */
  ptr = strrchr (cmd_ptr, ';');
  if (ptr != NULL)
    {
      *ptr = '\0';
    }

  /* Now figure out which command was desired, and invoke the 
   * necessary program.
   */
  if (strncasecmp (cmd_ptr, STRESSGEN_TOKEN, strlen (STRESSGEN_TOKEN)) == 0)
    {
      cmd_ptr += strlen (STRESSGEN_TOKEN);
      skip_white_comments (&cmd_ptr);
      if (exec_stressgen (procTbl, cmd_ptr, start_line, tdelay) < 0)
	{
	  *status = 1;
	  return;
	}
    }
  else if (strncasecmp (cmd_ptr, STRESSEXEC_TOKEN,
			strlen (STRESSEXEC_TOKEN)) == 0)
    {
      cmd_ptr += strlen (STRESSEXEC_TOKEN);
      skip_white_comments (&cmd_ptr);
      if (exec_stressexec (procTbl, cmd_ptr, start_line, tdelay) < 0)
	{
	  *status = 1;
	  return;
	}
    }
  else
    {
      /* If we don't know in particular about this command, then just
       * start up a new client process assuming that the rest of the line
       * is a command line to execute.
       */
      (void) make_argvfromstring (argvec, cmd_ptr);
      proc = start_process (procTbl, argvec[0], argvec, tdelay);
      if (proc == NULL)
	{
	  qactl_output (stdout,
			"ERROR! (input line %d) Failed to exec new process, %s\n",
			start_line, cmd_ptr);
	}
      if (activate_proc (procTbl, proc) < 0)
	{
	  qactl_output (stderr,
			"WARNING: [line %d] Unable to activate Client %d \n",
			__LINE__, proc->id);
	}
    }

  return;
}

static void
free_arguments (ARGDEF * arguments)
{
  int index = 0;
  while (arguments[index].name != NULL)
    {
      switch (arguments[index].type)
	{
	case ARG_INTEGER:
	case ARG_FLOAT:
	case ARG_BOOLEAN:
	case ARG_NULL:
	  break;

	case ARG_STRING:
	  if (*((char **) arguments[index].variable) != NULL)
	    {
	      free (*((char **) arguments[index].variable));
	    }
	  break;

	default:
	  break;
	}
      index++;
    }
}
