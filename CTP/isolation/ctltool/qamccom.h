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
 * Overview:  Header file for routines and data structures common to     
 * the super and qactl controllers.
 */

#ifndef _QAMC_COMMON_H
#define _QAMC_COMMON_H

#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>

/* NOTE: This size is 2 x CLIENT_READ_BUFFER_SIZE from qactl.c */
#define    QAMC_MAX_MSGLEN              4096*2

/** The following are defined for cross-platform portability
 ** of the super controller.  They should correspond directly to
 ** well-known system parameters, and usually correspond to the
 ** largest known value for that datum.
 ** Please remember to specify where the value was located.
 **/
#define    QAMC_MAXHOSTNAMELEN   256	/* Highest value from Solaris 5.3 */


#define    QAMC_CONTROLLER_MODE_NAME    "-slave"
#define    QAMC_SYNC_MODE_NAME          "-sync"
#define    QAMC_ASYNC_MODE_NAME         "-async"


/* The message types sent between superc and other controllers */
enum qamc_msgtype_e
{
  QAMC_MSG_DEBUG,
  /** SUPER controller message to its subordinates **/
  QAMC_SMSG_XFER_CONTINUE,	/* Each line of input sent as one of these */
  QAMC_SMSG_XFER_COMPLETE,	/* ind. no more script to download */
  QAMC_SMSG_CONTINUE,		/* Used for rendezvous or after pauses */
  QAMC_SMSG_POLL,		/* Polling to insure master still running */
  QAMC_SMSG_STATUS_INQUIRY,	/* Ask mc for current status */
  QAMC_SMSG_TOKEN,		/* for sync mode, emulate a token ring */
  QAMC_SMSG_FORCE_SHUTDOWN,	/* Tell masters to halt cleanly */

  /** Subordinate messages back to SUPER **/
  QAMC_MSG_DATA_STDOUT,		/* master returning client stdout output */
  QAMC_MSG_DATA_STDERR,		/* master returning client stderr output */
  QAMC_MSG_CLIENTEVENT,		/* an (expected) client event occurred */
  QAMC_MSG_CONTROLLEREVENT,	/*  an (expected) master event occurred */
  QAMC_MSG_TOKEN_RELEASE,	/* tell super we give up the token */
  QAMC_MSG_RESULTS,		/* master controller returning test results */
  QAMC_MSG_POLL_RESPONSE,	/* return value after receiving SMSG_POLL */
  QAMC_MSG_STATUS,		/* status info (tbd) */
  QAMC_MSG_ERROR		/* an unexpected error occurred */
};
typedef enum qamc_msgtype_e qamc_msg_type;

struct qamc_message_s
{
  unsigned int msgtype;		/* really a qamc_msg_type */
  unsigned int sender_id;
  unsigned int msglen;		/* Size of the remaining msg */
  char msg[QAMC_MAX_MSGLEN];
};
typedef struct qamc_message_s qamc_msg;

  /* structure returned in status inquiries.
   * Don't forget about host<-->network byte ordering.
   */
struct qamc_ctlr_status_s
{
  unsigned int c_pid;
  unsigned int c_clients;
  unsigned int c_alive_clients;
};

typedef enum
{
  QAMC_STMT_UNKNOWN,		/* OK to say (x <= STMT_UNKNOWN) */
  /** Super controller statements **/

  /** Master controller statements **/
  QAMC_STMT_MC_SETUP_NUMCLIENTS,
  QAMC_STMT_MC_SETUP_CLIENTNAMES,
  QAMC_STMT_MC_WAITREADY,
  QAMC_STMT_MC_WAITBLOCK,
  QAMC_STMT_MC_WAITUNBLOCK,
  QAMC_STMT_MC_WAITFINISHED,
  QAMC_STMT_MC_WAITFOR,
  QAMC_STMT_MC_PAUSE_DLOCK,
  QAMC_STMT_MC_RENDEZVOUS,
  QAMC_STMT_MC_EXECUTE_WDELAY,
  QAMC_STMT_MC_EXECUTE,
  QAMC_STMT_MC_NOOP,

  /** Client statements **/
  QAMC_STMT_CL_ACTION,		/* Generic Client statement */
  /* Client statements which require superc or master coordination */
  QAMC_STMT_CL_SIMULATE_CRASH,	/* simulate server crash */
  /* There are lots more simulations */

  /** INTERNAL USE ONLY TYPES **/
  QAMC_STMT_MC_SETUP_PREFACE,
  QAMC_STMT_MC_WAIT_PREFACE,
  QAMC_STMT_CL_SIMULATE_PREFACE
} qamc_stmt_type;

  /** Msg status buffer. **/
struct comm_info
{
  int tx_errs;			/* number of transmission errors */
  int rx_num;			/* Sequence # for each incoming message */
  int tx_num;			/* Sequence # for each outgoing message */
  time_t rx_time;		/* Last message arrival time */
  time_t tx_time;		/* Last message sent to master at time */
};


/** Header (HDR) size + strlen(amsg.msg) -> yields total size in bytes
 ** to send or receive.  Based on the msgtype, sender_id, and msglen
 ** fields in the qamc_msg structure.  If that changes, change this too.
 **/
#define QAMC_MSGHDR_SIZE     (sizeof(qamc_msg_type) + sizeof(int) * 2)


#define RETURN_TOKEN(s, id, val)     do {  \
  qamc_msg _rt_mbuf;                        \
  _rt_mbuf.sender_id = id;                 \
  _rt_mbuf.msgtype = QAMC_MSG_TOKEN_RELEASE; \
  _rt_mbuf.msglen = 1;                     \
  _rt_mbuf.msg[0] = val;                   \
  if (qamc_send(s, &_rt_mbuf, NULL) != 1)   \
    fprintf(stderr, "RETURN_TOKEN: PROBLEM RETURNING TOKEN, line %d\n", __LINE__); \
  } while (0)

#define PASS_TOKEN(s, id)          do {    \
  qamc_msg _rt_mbuf;                        \
  _rt_mbuf.sender_id = id;                 \
  _rt_mbuf.msgtype = QAMC_SMSG_TOKEN;       \
  strcpy(_rt_mbuf.msg, "=TOKEN=");                  \
  _rt_mbuf.msglen = strlen(_rt_mbuf.msg);                     \
  if (qamc_send(s, &_rt_mbuf, NULL) != _rt_mbuf.msglen)   \
    fprintf(stderr, "RETURN_TOKEN: PROBLEM SENDING TOKEN, line %d\n", __LINE__); \
  } while (0)


struct qamc_fifoq_s
{
  int max_alloc_so_far;		/* maximum slots in queue */
  int head;			/* always points to the first element */
  int tail;			/* always points to the last element */
  int num_elems;		/* Current number of elements in queue */
  qamc_msg *queue;		/* ptr to space allocated for queue */
};

int qamc_send (int s, qamc_msg * msg, struct comm_info *ci);
int qamc_recv (int s, qamc_msg * msg, struct comm_info *ci);
qamc_stmt_type qamc_determine_stmt_type (char *stmt, char **cmdptr,
					 char **tokptr, int *referid);
void qamc_unquoted_lowercase_conversion (unsigned char *string);
int qamc_get_client (char *str, char **past);
qamc_msg *qamc_remove_fifoq (struct qamc_fifoq_s *queue);
int qamc_insert_fifoq (qamc_msg * elem, struct qamc_fifoq_s **queue);
char *qamc_get_compound_stmt (FILE * fp, int *stmt_cnt, int no_mod);

#endif
