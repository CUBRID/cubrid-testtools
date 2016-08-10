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

/*
 *      Overview: Routines common to master controllers and/or the            
 *                supercontroller.                                            
 *                                                                            
 */


#include <sys/types.h>
#include <sys/socket.h>
#include <string.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <netinet/in.h>
#include <assert.h>

#include "parse.h"
#include "qamccom.h"

#define MIN(a,b)                ((a) < (b) ? (a) : (b))

struct stmttype_tuples
{
  const char *str;
  qamc_stmt_type type;
};

static int recv_x_bytes (int sock, char *ptr, int n);
static int get_tokenindex (char *p, int max, struct stmttype_tuples *tokens);
static int qamc_init_fifoq (int size, struct qamc_fifoq_s **mq);


/*
 * recv_x_bytes()                                                             
 *                                                                            
 * arguments:                                                                 
 *      sock : file descriptor for a connected socket to receive msg from;    
 *       ptr : a ptr to memory where data is to be stored;                    
 *         n : the number of bytes to read from the socket,                   
 *                                                                            
 * returns/side-effects:                                                      
 *   -1 if an error occurs in the initial read;                               
 *   a 0 if the sender has disconnected (i.e. EOF);                           
 *   otherwise, a non-negative number of bytes readved                        
 *                                                                            
 * description:                                                               
 *   Perform a 'recv()' on the given socket (presumes there is something      
 * there or else it will block until the necessary number of bytes arrives).  
 * This routine handles data only in bytes, and therefore is not concerned    
 * with things like host or network byte ordering.                            
 *   This routine must handle cases like EINTR and/or where fewer bytes       
 * are received than we asked for in the recv() call.  It turns out that      
 * this happens frequently with sockets when the system is under moderate     
 * or greater stress.                                                         
 *                                                                            
 */

static int
recv_x_bytes (int sock, char *ptr, int n)
{
  int rc;
  int tot = n;

  while (n > 0)
    {				/* Read until we get n total bytes */
    restart_rx:
      rc = recv (sock, ptr, n, 0);
      if (rc < 0)
	{
	  if (errno == EINTR)
	    {
	      goto restart_rx;
	    }
	  /* Otherwise it is a true error. */
	  fprintf (stderr,
		   "recv_X: failure trying to read n bytes, errno = %d\n",
		   errno);
	  return (-1);
	}
      if (rc == 0)
	{
	  /* Seem to be at EOF. */
	  return (0);
	}

      n -= rc;			/* read only that many */
      ptr += rc;		/* ditto               */
    }

  return (tot);
}


/*
 * qamc_recv()                                                                 
 *                                                                            
 * arguments:                                                                 
 *    socket : file descriptor for a connected socket to receive msg from,    
 *       msg : a "qamc_msg" message to receive,                                
 *        ci : an optional structure to maintain statistics about messages.   
 *                                                                            
 * returns/side-effects:                                                      
 *   -1 if an error occurs in the initial read;                               
 *   a 0 with msglen == -1 if the sender has disconnected (i.e. EOF);         
 *   otherwise, a non-negative number of bytes received (not counting the     
 *     message header information.                                            
 *   This function is also responsible for the network-host byte ordering of  
 * the qamc_msg header information.  When first received, these fields will be 
 * in network byte order and must be converted to host byte order.            
 *                                                                            
 * description:                                                               
 *   Perform a 'recv()' on the given socket S (presumes there is something    
 * there or else it will block).  Also does super controller bookkeeping      
 * on tracking time and status of the messages.  Assumes that we are          
 * reading formatted qamc_msg-es, and not any other data.  Also assumes that   
 * data is available for reading, or else will block in the recv() call.      
 * This routine is not intended for sockets which may have O_NDELAY set.      
 *                                                                            
 * Remember, 0 length messages are allowed, so msg->msglen is set to -1, to   
 * indicate the difference between EOF (-1) and a 0 length message.           
 *                                                                            
 */

int
qamc_recv (int s, qamc_msg * msg, struct comm_info *ci)
{
  int rc;

  errno = 0;

  /** Read header info only, at first.  At this point, an 0 length
   ** indicates EOF.  **/
  rc = recv_x_bytes (s, (char *) msg, QAMC_MSGHDR_SIZE);
  if (rc < 0)
    {
      fprintf (stderr,
	       "qamc_recv: Rec'd msg with no hdr for (pid %d): len = %d, errno = %d\n",
	       getpid (), rc, errno);
      return (rc);
    }
  if (rc == 0)
    {				/* EOF */
      msg->msglen = -1;		/* returning 0 with msglen -1 indicates EOF to caller
				 * and distinguishes EOF from 0 length messages */
      return (rc);
    }

  /** Convert network byte order to host byte order **/
  msg->sender_id = ntohl (msg->sender_id);
  msg->msgtype = ntohl (msg->msgtype);
  msg->msglen = ntohl (msg->msglen);

  /** This means data must be screwed up **/
  if (msg->msglen > QAMC_MAX_MSGLEN)
    {
      fprintf (stderr,
	       "qamc_recv: MSGLEN out of range (%d), sender: %d, bytes read: %d, errno = %d\n",
	       msg->msglen, msg->sender_id, rc, errno);
      return (-1);
    }

  /** Read all of the message body now, we expect exactly msg->msglen
   ** bytes to be available (or become available really, really soon)
   **/
  rc = recv_x_bytes (s, (char *) msg->msg, msg->msglen);
  if (rc != msg->msglen)
    {
    /** Cannot have a header only if not zero-length message **/
      fprintf (stderr,
	       "qamc_recv (pid %d): Expected msg body %d bytes, only got %d\n",
	       getpid (), msg->msglen, rc);
      return (-1);
    }

  if (ci != NULL)
    {
      /* update statistics */
      ci->rx_time = time ((time_t *) NULL);
      ci->rx_num++;
    }

  return (msg->msglen);
}

/*
 * qamc_send()                                                                 
 *                                                                            
 * arguments:                                                                 
 *         s : file descriptor for a connected socket to send msg to,         
 *       msg : a "qamc_msg" message to receive,                                
 *        ci : an optional structure to maintain statistics about messages.   
 *                                                                            
 * returns/side-effects:                                                      
 *   -1 if an error occurs in the initial read;                               
 *   a 0 with msglen == -1 if the sender has disconnected (i.e. EOF);         
 *   otherwise, a non-negative number of bytes received (not including        
 *   the message header information).                                         
 * description:                                                               
 *   Perform a 'send()' on the given socket S.  Also does super controller    
 *   bookkeeping on tracking time and status of the messages.  Assumes that   
 *   we are writing a formatted qamc_msg, and not any other data.              
 *   This function splits large messages (>1024 bytes) into smaller pieces    
 *   for sending across the network.                                          
 *   This function is also responsible for the network-host byte ordering of  
 *   the qamc_msg header information.  When first called, these fields will    
 *   be in host byte order and must be converted to network byte order.       
 *                                                                            
 *                                                                            
 * Remember, 0 length messages are allowed, so msg->msglen is set to -1, to   
 * indicate the difference between EOF (-1) and a 0 length message.           
 *                                                                            
 */

int
qamc_send (int s, qamc_msg * msg, struct comm_info *ci)
{
  int n, rc;
  int wlen;			/* length of bytes to send */
  int orig_len;			/* have to keep the host ordered length around */

  errno = 0;

  orig_len = msg->msglen;
  if (orig_len > QAMC_MAX_MSGLEN)
    {
    /** It may be better to automatically break it up into multiple
     ** writes.
     **/
      fprintf (stderr, "qamc_send: message len too large %d\n", orig_len);
      errno = EMSGSIZE;
      return (-1);
    }

  /** Convert host byte order to network byte order **/
  msg->sender_id = htonl (msg->sender_id);
  msg->msgtype = htonl (msg->msgtype);
  msg->msglen = htonl (msg->msglen);

  /** Send all of the message body now.  We have to break up really large
   ** messages into network sized pieces (using 1024 for now). **/

  wlen = MIN (1024, orig_len);
  n = send (s, (char *) msg, wlen + QAMC_MSGHDR_SIZE, 0);
  if (n < 0)
    {
      if (ci != NULL)
	{
	  ci->tx_errs++;
	}
      return (n);
    }

  /** Convert back to host byte order **/
  msg->sender_id = ntohl (msg->sender_id);
  msg->msgtype = ntohl (msg->msgtype);
  msg->msglen = ntohl (msg->msglen);

  n -= QAMC_MSGHDR_SIZE;	/* don't count the header */
  while (n < orig_len)
    {
      wlen = MIN (1024, orig_len - n);
      rc = send (s, (char *) &msg->msg[n], wlen, 0);
      if (rc <= 0)
	{
	  fprintf (stderr, "qamc_send: error sending on socket, errno = %d\n",
		   errno);
	  if (ci != NULL)
	    {
	      ci->tx_errs++;	     /** trouble **/
	    }
	  return (-1);
	}
      n += rc;
    }

  if (n != orig_len)
    {
      fprintf (stderr,
	       "qamc_send: Number bytes sent %d not same as msg len %d\n", n,
	       orig_len);
      if (ci != NULL)
	{
	  ci->tx_errs++;	   /** too short **/
	}
      return (-1);
    }

  /* update statistics */
  if (ci != NULL)
    {
      ci->tx_time = time ((time_t *) NULL);
      ci->tx_num++;
    }

  return (n);
}

/*
 * get_tokenindex                                                             
 *                                                                            
 * arguments:                                                                 
 *           p:  Pointer to a statement to find                               
 *         max:  number of stmt / token tuples to check.                      
 *     tokens :  Array of stmt/token tuples.                                  
 * returns/side-effects:                                                      
 *      Returns the index into the token structure corresponding to the       
 *      string being searched for.                                            
 *      Returns -1 if no matching strings are found.                          
 * description:                                                               
 *      Return the index of the first token contained in the string P         
 *                                                                            
 */

static int
get_tokenindex (char *p, int max, struct stmttype_tuples *tokens)
{
  int i = 0;

  while (i < max)
    {
      if (strstr (p, tokens[i].str) != NULL)
	{
	  return (i);
	}
      i++;
    }

  return (-1);
}

/*
 * qamc_determine_stmt_type                                                   
 *                                                                            
 * arguments:                                                                 
 *   orig_stmt:  Pointer to a client or controller statement.                 
 *      cmdptr:  return ptr to rest of original statement following the ':'.  
 *      tokptr:  return pointer can be set to next most "interesting" token.  
 *     referid:  some statements can return a reference to a specific entity, 
 *               for example, MC-3: refers to master controller, 3.           
 * returns/side-effects:                                                      
 *      Converts the original statement to lower case (side-effect);          
 *      returns the qamc_stmt_type.  Also returns two pointers to somewhere   
 *      within the stmt.                                                      
 * description:                                                               
 *    Determine the type of the given statement string and return             
 *    a unique identifier which corresponds to the statement type.            
 *    Also returns a pointer TOKPTR to the apparent next useful token in the  
 *    stmt  so that the caller doesn't have to reparse the whole string.      
 *    Also return a pointer to the main statement, after the first ':'.       
 *    Also modifies the referid to give the identity of any specific MC       
 *    referent in the input statement.  A referid value of zero corresponds   
 *    to a non-specific master reference (i.e. MC:), while a non-zero value   
 *    indicates the specific master (i.e. for MC-1: referid = 1).             
 *    This routine understands the current set of super/qactl commands        
 *    However, to handle different input command sets for diff. controllers   
 *    (if we ever do that) a different method will probably need to be used.  
 * WARNING: Does not understand compound statements, except to simply         
 *  assume they are for clients and simply return QAMC_STMT_CL_ACTION.        
 */

qamc_stmt_type
qamc_determine_stmt_type (char *orig_stmt, char **cmdptr, char **tokptr,
			  int *referid)
{
  char *p;
  int index;
  int len;
  char stmt[QAMC_MAX_MSGLEN];
  struct stmttype_tuples mc_setup_tokens[] = {
    {"num_clients", QAMC_STMT_MC_SETUP_NUMCLIENTS},
    {"client_name", QAMC_STMT_MC_SETUP_CLIENTNAMES},
  };
  struct stmttype_tuples mc_wait_tokens[] = {
    {"blocked", QAMC_STMT_MC_WAITBLOCK},
    {"unblocked", QAMC_STMT_MC_WAITUNBLOCK},
    {"ready", QAMC_STMT_MC_WAITREADY},
    {"finished", QAMC_STMT_MC_WAITFINISHED},
  };
  struct stmttype_tuples mc_tokens[] = {
    {"setup", QAMC_STMT_MC_SETUP_PREFACE},
    {"wait until", QAMC_STMT_MC_WAIT_PREFACE},
    {"wait for", QAMC_STMT_MC_WAITFOR},
    {"pause for deadlock", QAMC_STMT_MC_PAUSE_DLOCK},
    {"rendezvous with", QAMC_STMT_MC_RENDEZVOUS},
    {"execute delay", QAMC_STMT_MC_EXECUTE_WDELAY},
    {"execute", QAMC_STMT_MC_EXECUTE},
  };
  struct stmttype_tuples client_sim_tokens[] = {
    {"crash", QAMC_STMT_CL_SIMULATE_CRASH},
  };
  /** OTHER SIMULATION COMMANDS -- Not all require master controller
   ** intervention, but some do (probably all the ones affecting the
   ** server).
  simulate crash | CRASH_SER | CRASH_SER_FLUSH_LOG            
   *       | CRASH_SER_FLUSH_LOG_DATAPOOL | CRASH_CL | CRASH_CL_FLUSH_WS
   *       | CRASH_CL_SER | CRASH_CL_SER_FLUSH_LOG                      
   *       | CRASH_CL_SER_FLUSH_LOG_DATAPOOL | CRASH_CL_SER_FLUSH_WS    
   *       | CRASH_CL_SER_FLUSH_WS_LOG                                  
   *       | CRASH_CL_SER_FLUSH_WS_LOG_DATAPOOL
  **/
  struct stmttype_tuples client_tokens[] = {
    {"simulate", QAMC_STMT_CL_SIMULATE_PREFACE},
  };

  *cmdptr = *tokptr = NULL;

  if (orig_stmt == NULL)
    {
      return (QAMC_STMT_UNKNOWN);
    }
  else
    {
      strcpy (stmt, orig_stmt);
    }

  /** get rid of case sensitivity **/
  qamc_unquoted_lowercase_conversion ((unsigned char *) stmt);

  if (strncmp (stmt, "super:", (len = strlen ("super:"))) == 0)
    {
    /** Super controller command **/
      *tokptr = stmt + len;
      *cmdptr = orig_stmt + len;
      return (QAMC_STMT_UNKNOWN);
    }
  else if (strncmp (stmt, "mc", 2) == 0)
    {
      len = 2;
      *referid = atoi (stmt + 2);
      if (*referid > 0)
	{
	  return (QAMC_STMT_UNKNOWN);	/* bad syntax */
	}
      if (*referid < 0)
	{
	  *referid *= -1;
	}
      len += strspn (stmt + 2, "-:0123456789 \t");

    /** Master controller command **/
      p = stmt + len;
      *cmdptr = orig_stmt + len;
      index =
	get_tokenindex (p,
			sizeof (mc_tokens) / sizeof (struct stmttype_tuples),
			mc_tokens);
      if (index >= 0)
	{
	  len = strlen (mc_tokens[index].str) + 1;
	  switch (mc_tokens[index].type)
	    {
	    case QAMC_STMT_MC_WAIT_PREFACE:
	      /* 'wait until' statement is followed by a client name and
	       * then a command such as { blocked | unblocked | ready}
	       * We must return a pointer to the start of the client name,
	       * and then the specific statement type token.  Note that
	       * there is no generic MC_WAIT_PREFACE statement, there is always
	       * a command qualifying it.
	       */
	      index = get_tokenindex (p + len, sizeof (mc_wait_tokens) /
				      sizeof (struct stmttype_tuples),
				      mc_wait_tokens);
	      if (index >= 0)
		{
		  *tokptr = p + len;	/* return a tokptr to the Client name */
		  *tokptr = orig_stmt + (*tokptr - stmt);	/* offset into orig stmt */
		  return (mc_wait_tokens[index].type);
		}
	      break;

	    case QAMC_STMT_MC_SETUP_PREFACE:
	      /* For now, there is only one setup command, but there will
	       * probably be more in the future.
	       */
	      index = get_tokenindex (p + len, sizeof (mc_setup_tokens) /
				      sizeof (struct stmttype_tuples),
				      mc_setup_tokens);
	      if (index >= 0)
		{
		  /* return a tokptr to the # clients */
		  *tokptr = p + len + strlen (mc_setup_tokens[index].str) + 1;
		  if ((p = strchr (*tokptr, '=')) != NULL)
		    {
		      *tokptr = p + 1;	/* reset tokptr to after the '=' */
		      *tokptr = orig_stmt + (*tokptr - stmt);	/* offset into orig stmt */
		      return (mc_setup_tokens[index].type);
		    }
		}
	      break;

	    case QAMC_STMT_MC_WAITFOR:
	    case QAMC_STMT_MC_PAUSE_DLOCK:
	    case QAMC_STMT_MC_RENDEZVOUS:
	    case QAMC_STMT_MC_EXECUTE:
	    case QAMC_STMT_MC_EXECUTE_WDELAY:
	      /* Generic action, return with tokptr at end of this token
	       * and return the stmt-type.
	       */
	      *tokptr = p + len + 1;
	      *tokptr = orig_stmt + (*tokptr - stmt);	/* offset into orig stmt */
	      return (mc_tokens[index].type);

	    default:
		   /** unknown type **/
	      fprintf (stderr, "DBG get_stmt_type: Unknown statement, %s\n",
		       p);
	      return (QAMC_STMT_UNKNOWN);
	    }
	}
      /** If we got here it was a totally unexpected MC: command **/
      return (QAMC_STMT_UNKNOWN);
    }
  else
    {
    /** Client command -- we don't care unless it is a simulate. **/
      p = stmt + strspn (stmt, "Cc0123456789: \t");	/* skip the client name */
      *cmdptr = orig_stmt + (p - stmt);
      index =
	get_tokenindex (p,
			sizeof (client_tokens) /
			sizeof (struct stmttype_tuples), client_tokens);
      if (index >= 0)
	{
	  len = strlen (client_tokens[index].str);
	  switch (client_tokens[index].type)
	    {
	    case QAMC_STMT_CL_SIMULATE_PREFACE:
	      /* 'simulate ...' statement is followed by a type of simulation. */
	      index = get_tokenindex (p + len, sizeof (client_sim_tokens) /
				      sizeof (struct stmttype_tuples),
				      client_sim_tokens);
	      if (index >= 0)
		{
		  *tokptr = orig_stmt;	/* return a tokptr to client name */
		  return (client_sim_tokens[index].type);
		}
	      else
		{
	       /** Some other kind of simulate statement that we
	        ** don't handle yet.
		**/
		  *tokptr = orig_stmt;
		  return (QAMC_STMT_CL_ACTION);
		}
	      break;
	    default:
	       /** All other client statements are passed through **/
	      *tokptr = orig_stmt;
	      return (QAMC_STMT_CL_ACTION);
	    }
	}
    /** pass through unknown client statements **/
      return (QAMC_STMT_CL_ACTION);
    }

  return (-1);
}

/*
 * qamc_unquoted_lowercase_conversion                                          
 *                                                                            
 * arguments:                                                                 
 *      string:  Pointer to null terminated string.                           
 * returns/side-effects:                                                      
 *      None.                                                                 
 * description:                                                               
 *	Converts uppercase alphabetical letters to lowercase if not enclosed  
 *      in quotes.  It tries (but fails, see below) to avoid modifying        
 *      values contained within single or double quotes.                      
 * WARNING: There is an ambiguity in the "save state by" syntax which gets    
 * this routine confused when the save query itself contains single quotes,   
 * which happens a lot (e.f. varchars, date & time constants).  So try not to 
 * send "save state by ..." commands to this routine.  It won't work.         
 */

void
qamc_unquoted_lowercase_conversion (unsigned char *string)
{
  int in_quote;
  unsigned char quote_type;

  quote_type = '\0';

  if (string == NULL)
    {
      return;
    }

  for (in_quote = 0; *string != '\0'; string++)
    {
      if (!in_quote)
	{
	  if ((*string == '\'') || (*string == '\"'))
	    {
	      quote_type = (*string);
	      in_quote = 1;
	    }
	  else if (*string == '{')
	    {
	      quote_type = '}';
	      in_quote = 1;
	    }
	  else if (common_char_isupper (*string))
	    {
	      *string = common_char_tolower (*string);
	    }
	}
      else
	{
	  if (*string == quote_type)
	    {
	      in_quote = 0;
	    }
	}

    }
}

/*
 * qamc_get_client                                                             
 *                                                                            
 * arguments:                                                                 
 *         str:  Pointer to null terminated string containing client id num   
 * <opt>  past:  return pointer to tell caller where end of client num is     
 * returns/side-effects:                                                      
 *      An integer corresponding to the client id number (i.e. C9).           
 * description:                                                               
 *	Determines the client id number, given string like C1: or C1          
 */

 /** return the client number pointed to at STR.  Handle all
  ** cases, i.e. where default client (C1) assumed, Cxxx:, and Cyyy (no ':').
  ** If the out parameter past is given, set it to point after the
  ** client name just obtained.
  **/
int
qamc_get_client (char *str, char **past)
{
  char *ptr;
  int c_num;

  c_num = 1;			/* Default client no */

  if (str == NULL)
    {
      return (c_num);
    }

  if ((*str == 'C') || (*str == 'c'))
    {
      c_num = strtol (str + 1, &ptr, 0);
      if ((c_num <= 0) || ((str + 1) == ptr))
	{
	  c_num = 1;		/*set back to default */
	}
    }
  else
    {
      ptr = str;
    }

    /** Skip colon if present **/
  if (*ptr == ':')
    {
      ptr++;
    }

    /** return a pointer after the client number if desired **/
  if (past != NULL)
    {
      *past = ptr;
    }

  return (c_num);
}

/*
 * qamc_init_fifoq                                                             
 *                                                                            
 * arguments:                                                                 
 *      size:  The number of elements to reserve in the queue.                
 *        mq:  pointer to message queue;                                      
 * returns/side-effects:                                                      
 *      malloc-ed space for a new queue, else NULL if fails.                  
 * description:                                                               
 *	Create and initialize a fifo_q queue.  These queues are used by the   
 * controllers in order to cache incoming messages from other controllers.    
 * These cached messages are used when the input script describes an event    
 * requiring such communication.                                              
 * Note that callers cannot call this routine and should just call insert_    
 * the first time, it will call this routine to allocate the queue.           
 */

static int
qamc_init_fifoq (int size, struct qamc_fifoq_s **mq)
{

/* Space for the queue */
  *mq = (struct qamc_fifoq_s *) calloc (1, sizeof (struct qamc_fifoq_s));
  if (*mq == NULL)
    {
      return (-1);
    }

/* space for the items stored by the queue */
  (mq)[0]->queue = (qamc_msg *) calloc (size, sizeof (qamc_msg));
  if ((mq)[0]->queue == NULL)
    {
      return (-1);
    }

  (*mq)[0].max_alloc_so_far = size;

  return (0);
}

/*
 * qamc_insert_fifoq                                                           
 *                                                                            
 * arguments:                                                                 
 *      elem:  Msg to put on the queue;                                       
 *        mq:  pointer to message queue;                                      
 * returns/side-effects:                                                      
 *    Return -1 if unable to insert element into queue.                       
 *    Return 0 if queue is full.                                              
 *    Return 1 if able to insert item.                                        
 * description:                                                               
 *    Insert message into FIFO queue.  If first time (que == NULL), then      
 * allocate enough space for a the queue.                                     * 
 */

int
qamc_insert_fifoq (qamc_msg * elem, struct qamc_fifoq_s **mq)
{
  if (mq == NULL)
    {
      return (-1);
    }

  if (*mq == NULL)
    {
      if (qamc_init_fifoq (300, mq) < 0)
	{
	  return (-2);
	}
    }

  if ((*mq)->queue == NULL)
    {
      return (-3);
    }

  if ((*mq)->num_elems >= (*mq)->max_alloc_so_far)
    {
      fprintf (stderr, "QUEUE REACHED CAPACITY OR OVERFLOW\n");
      return (0);
    }

  memcpy (&((*mq)->queue[(*mq)->tail]), (char *) elem, sizeof (qamc_msg));

  (*mq)->num_elems++;

  (*mq)->tail++;
  if ((*mq)->tail >= (*mq)->max_alloc_so_far)
    {
      (*mq)->tail = 0;		/* Roll around the edges */
    }

  return (1);
}

/*
 * qamc_remove_fifoq                                                           
 *                                                                            
 * arguments:                                                                 
 *         mq :  Pointer to a fifo_q                                          
 * returns/side-effects:                                                      
 *   Return -1 when an error occurs,                                          
 *   Return 0 when the queue is empty,                                        
 *   Return 1 when an element is actually removed.                            
 * description:                                                               
 *   Removes the next message from the given queue and returns a pointer      
 *   to the removed message.                                                  
 *                                                                            
 */

qamc_msg *
qamc_remove_fifoq (struct qamc_fifoq_s * mq)
{
  qamc_msg *ptr;

  if (mq == NULL)
    {
      return (NULL);
    }

  if (mq->num_elems == 0)
    {
      return (NULL);
    }

  if (mq->queue == NULL)
    {
      return (NULL);
    }

  ptr = &mq->queue[mq->head];

  mq->num_elems--;

  mq->head++;
  if (mq->head >= mq->max_alloc_so_far)
    {
      mq->head = 0;		/* Roll around the edges */
    }

  return (ptr);
}


/*
 * qamc_get_compound_stmt                                                      
 *                                                                            
 * arguments:                                                                
 *          fp:  pointer to an open FILE handle;                              
 *    stmt_cnt:  pointer to an int which returns the number of nested stmts   
 *      no_mod:  TRUE if this routine should pass the stmt back untouched,    
 *               or FALSE if we should remove the '{' and '}'                 
 *                                                                            
 * returns/side-effects:                                                      
 *      Returns a pointer to a buffer containing the next statement or        
 * compound statement.                                                        
 *                                                                            
 * description:                                                               
 *    This routine allows commands to clients to be "compound" statements,    
 * in other words, more than a single SQL statement.  This is provided so     
 * that clients can recieve more than one action at a time when desired       
 * and thus multiple clients can have greater parallelism.  In the example    
 * which follows, both select statements will be sent to the client in        
 * a single write().  And the client will return ready twice.                 
 * This routine is also responsible for blanking out the '{' and '};'         
 * which surround the statement when required.                                
 *                                                                            
 * Example:                                                                   
 *   C1: { select * from table;                                               
 *         select * from table_2;                                             
 *       };                                                                   
 *                                                                            
 * The parse strings returned by 'get_next_stmt()' will be as follows:        
 *   C1: { select * from table;                                               
 *   select * from table_2;                                                   
 *   };                                                                       
 * and we end up returning "C1:{select * from table; select * from table_2;}; 
 *                                                                            
 * NOTE: Should be more careful about the not overflowing the stmt_buf[];     
 */

#define _OpenBrace '{'
#define _CloseBrace '}'

char *
qamc_get_compound_stmt (FILE * fp, int *stmt_cnt, int no_mod)
{
  static char stmt_buf[QAMC_MAX_MSGLEN];
  char *stmt, *p = NULL;
  int not_done = 1;

  /** initialize **/
  *stmt_cnt = 0;
  stmt_buf[0] = '\0';

  /** Loop until end of compound statement discovered, or if not a
   ** compound statement, just return the next statement.
   **/
  stmt = get_next_stmt (fp);
  if (stmt == NULL)
    {
      /* Caller expects a NULL if no more input */
      return NULL;
    }

  *stmt_cnt += 1;
  strcpy (stmt_buf, stmt);
  p = strchr (stmt_buf, _OpenBrace);
  if (p != NULL)
    {
      /** Might be a compound statement **/
      if ((p == stmt_buf) || (*(p - 1) == ':') || (*(p - 2) == ':'))
	{
	/** Definitely a Compound statement beginning here **/
	  if (!no_mod)
	    {
	      *p = ' ';		/* destroy opening brace */
	    }
	  while (not_done && (stmt = get_next_stmt (fp)))
	    {
	      if ((stmt[0] == _CloseBrace)
		  && ((stmt[1] == ';') || (stmt[2] == ';')))
		{
		  not_done = 0;	/* End of Compound statement reached */
		}
	      if (no_mod || not_done)
		{
		  /* Only transfer real statements, not the final <close-brace>;" */
		  strcat (stmt_buf, stmt);
		  *stmt_cnt += 1;
		}
	    }

	}			/* end IF <statement is really compound> */
    }				/* end IF <statement may be compound> */

  return (stmt_buf);
}
