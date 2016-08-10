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
#include <string.h>
#include <errno.h>

#include "parse.h"
#include "common.h"


char *get_next_stmt (FILE * fp);
void end_stmts (void);

static int grow_buf (void);

/*
 * Buffer area in which to receive raw lines from the input file.
 *
 *	raw_fp		The file pointer feeding the buffer
 * 	raw_buf		The actual buffer
 * 	raw_next	Pointer to the next character to be "read"
 * 	raw_limit	The end of the buffer
 */
#define RAW_BUF_SIZE	1024
static FILE *raw_fp;
static char raw_buf[RAW_BUF_SIZE];
static const char *raw_next = raw_buf;
static const char *raw_limit = raw_buf;

/*
 * The buffer in which processed lines are assembled:
 *
 * 	buf		Pointer to the start of the buffer
 * 	buf_next	Pointer to the next position to receive a char
 * 	buf_limit	The end of the assembly buffer
 */
static char *buf = NULL;
static char *buf_next = NULL;
static const char *buf_limit = NULL;
#define BUF_INC		4096	/* How much to grow BUF by */

int parse_line_num = 0;

/*
 * grow_buf: GROW THE BUFFER
 *
 * Arguments:
 *
 * Returns: int
 *
 * Errors:
 *
 * Description:
 */

static int
grow_buf (void)
{
  char *p;
  int n, size;

  n = buf_next ? buf_next - buf : 0;
  if (buf)
    {
      size = (buf_limit - buf) + BUF_INC;
      p = realloc (buf, size);
      if (p == NULL)
	return -1;
      buf = p;
    }
  else
    {
      size = BUF_INC;
      p = malloc (size);
      if (p == NULL)
	return -1;
      buf = p;
    }
  buf_next = buf + n;
  buf_limit = buf + size;

  return 0;

}


void
read_raw_fp (void)
{
  const char *p;

  p = fgets (raw_buf, sizeof (raw_buf), raw_fp);
  parse_line_num++;
  if ((p == NULL) && ferror (raw_fp))
    {
      fprintf (stderr,
	       "FATAL ERROR - read next raw buffer (size %d) failed, errno = %d\n",
	       (int) sizeof (raw_buf), errno);
      abort ();			/* No reasonable way out of this situation */
    }
  raw_limit = raw_buf + (p == NULL ? 0 : strlen (raw_buf));
  raw_next = raw_buf;
}


typedef enum
{

  ACCEPT,			/* Saw the naked semicolon that tells */
  /* us to stop */

  COPY,				/* Normal: copy the input to the */
  /* output */

  ONE_SLASH,			/* We've seen '/' while in the normal */
  /* state, which may start a C comment */

  IN_C_COMMENT,			/* Inside a C comment */

  ONE_STAR,			/* We've seen a '*' while inside a C */
  /* comment, which may introduce the */
  /* terminating star-slash sequence */

  ONE_DASH,			/* We've seen a '-' while in the */
  /* normal state, which may introduce a */
  /* SQL comment */

  IN_SQL_COMMENT,		/* Inside a SQL comment */

  IN_DQ_STRING,			/* Inside a double-quoted string */

  IN_DQ_STRING_ESCAPE,		/* Seen a '\' inside a double-quoted */
  /* string */

  IN_SQ_STRING,			/* Inside a single-quoted string */

  IN_SQ_STRING_ONE_SQ,		/* We've seen a single quote while */
  /* inside a single-quote string, which */
  /* may be the start of a double */
  /* single-quote escape sequence */

  IN_SQ_STRING_ESCAPE,		/* We've seen an escape character */
  /* while inside a single-quote string; */
  /* if followed by a newline we should */
  /* skip it. */

  NUM_STATES
} STATE;

typedef enum
{

  NL,				/* Newline: '\n' */
  WS,				/* Whitespace other than newline */
  SQ,				/* Single quote: '\'' */
  DQ,				/* Double quote: '"' */
  ESCAPE,			/* Backslash: '\\' */
  SLASH,			/* Regular slash: '/' */
  STAR,				/* Asterisk: '*' */
  DASH,				/* Dash: '-' */
  SEMI,				/* Semicolon: ';' */
  OTHER,			/* Anything else */

  NUM_CLASSES
} CHAR_CLASS;


typedef enum
{

  COPY_INPUT,
  COPY_INPUT_AND_INSERT_WS,
  HOLD,
  COPY_HOLD_AND_INPUT,
  COPY_HOLD_AND_INSERT_WS,
  INSERT_WS,
  SKIP,
  QUIT
} ACTION;


static next_state[NUM_STATES][NUM_CLASSES] = {
  /* ACCEPT */
  {
   ACCEPT,			/* NL */
   ACCEPT,			/* WS */
   ACCEPT,			/* SQ */
   ACCEPT,			/* DQ */
   ACCEPT,			/* ESCAPE */
   ACCEPT,			/* SLASH */
   ACCEPT,			/* STAR */
   ACCEPT,			/* DASH */
   ACCEPT,			/* SEMI */
   ACCEPT,			/* OTHER */
   },

  /* COPY */
  {
   COPY,			/* NL */
   COPY,			/* WS */
   IN_SQ_STRING,		/* SQ */
   IN_DQ_STRING,		/* DQ */
   COPY,			/* ESCAPE */
   ONE_SLASH,			/* SLASH */
   COPY,			/* STAR */
   ONE_DASH,			/* DASH */
   ACCEPT,			/* SEMI */
   COPY,			/* OTHER */
   },

  /* ONE_SLASH */
  {
   COPY,			/* NL */
   COPY,			/* WS */
   COPY,			/* SQ */
   COPY,			/* DQ */
   COPY,			/* ESCAPE */
   ONE_SLASH,			/* SLASH */
   IN_C_COMMENT,		/* STAR */
   COPY,			/* DASH */
   ACCEPT,			/* SEMI */
   COPY,			/* OTHER */
   },

  /* IN_C_COMMENT */
  {
   IN_C_COMMENT,		/* NL */
   IN_C_COMMENT,		/* WS */
   IN_C_COMMENT,		/* SQ */
   IN_C_COMMENT,		/* DQ */
   IN_C_COMMENT,		/* ESCAPE */
   IN_C_COMMENT,		/* SLASH */
   ONE_STAR,			/* STAR */
   IN_C_COMMENT,		/* DASH */
   IN_C_COMMENT,		/* SEMI */
   IN_C_COMMENT,		/* OTHER */
   },

  /* ONE_STAR */
  {
   IN_C_COMMENT,		/* NL */
   IN_C_COMMENT,		/* WS */
   IN_C_COMMENT,		/* SQ */
   IN_C_COMMENT,		/* DQ */
   IN_C_COMMENT,		/* ESCAPE */
   COPY,			/* SLASH */
   ONE_STAR,			/* STAR */
   IN_C_COMMENT,		/* DASH */
   IN_C_COMMENT,		/* SEMI */
   IN_C_COMMENT,		/* OTHER */
   },

  /* ONE_DASH */
  {
   COPY,			/* NL */
   COPY,			/* WS */
   COPY,			/* SQ */
   COPY,			/* DQ */
   COPY,			/* ESCAPE */
   COPY,			/* SLASH */
   COPY,			/* STAR */
   IN_SQL_COMMENT,		/* DASH */
   ACCEPT,			/* SEMI */
   COPY,			/* OTHER */
   },

  /* IN_SQL_COMMENT */
  {
   COPY,			/* NL */
   IN_SQL_COMMENT,		/* WS */
   IN_SQL_COMMENT,		/* SQ */
   IN_SQL_COMMENT,		/* DQ */
   IN_SQL_COMMENT,		/* ESCAPE */
   IN_SQL_COMMENT,		/* SLASH */
   IN_SQL_COMMENT,		/* STAR */
   IN_SQL_COMMENT,		/* DASH */
   IN_SQL_COMMENT,		/* SEMI */
   IN_SQL_COMMENT,		/* OTHER */
   },

  /* IN_DQ_STRING */
  {
   IN_DQ_STRING,		/* NL */
   IN_DQ_STRING,		/* WS */
   IN_DQ_STRING,		/* SQ */
   COPY,			/* DQ */
   IN_DQ_STRING_ESCAPE,		/* ESCAPE */
   IN_DQ_STRING,		/* SLASH */
   IN_DQ_STRING,		/* STAR */
   IN_DQ_STRING,		/* DASH */
   IN_DQ_STRING,		/* SEMI */
   IN_DQ_STRING,		/* OTHER */
   },

  /* IN_DQ_STRING_ESCAPE */
  {
   IN_DQ_STRING,		/* NL */
   IN_DQ_STRING,		/* WS */
   IN_DQ_STRING,		/* SQ */
   IN_DQ_STRING,		/* DQ */
   IN_DQ_STRING,		/* ESCAPE */
   IN_DQ_STRING,		/* SLASH */
   IN_DQ_STRING,		/* STAR */
   IN_DQ_STRING,		/* DASH */
   IN_DQ_STRING,		/* SEMI */
   IN_DQ_STRING,		/* OTHER */
   },

  /* IN_SQ_STRING */
  {
   IN_SQ_STRING,		/* NL */
   IN_SQ_STRING,		/* WS */
   IN_SQ_STRING_ONE_SQ,		/* SQ */
   IN_SQ_STRING,		/* DQ */
   IN_SQ_STRING_ESCAPE,		/* ESCAPE */
   IN_SQ_STRING,		/* SLASH */
   IN_SQ_STRING,		/* STAR */
   IN_SQ_STRING,		/* DASH */
   IN_SQ_STRING,		/* SEMI */
   IN_SQ_STRING,		/* OTHER */
   },

  /* IN_SQ_STRING_ONE_SQ */
  {
   COPY,			/* NL */
   COPY,			/* WS */
   IN_SQ_STRING,		/* SQ */
   COPY,			/* DQ */
   COPY,			/* ESCAPE */
   COPY,			/* SLASH */
   COPY,			/* STAR */
   COPY,			/* DASH */
   ACCEPT,			/* SEMI */
   COPY,			/* OTHER */
   },

  /* IN_SQ_STRING_ESCAPE */
  {
   IN_SQ_STRING,		/* NL */
   IN_SQ_STRING,		/* WS */
   IN_SQ_STRING,		/* SQ */
   IN_SQ_STRING,		/* DQ */
   IN_SQ_STRING,		/* ESCAPE */
   IN_SQ_STRING,		/* SLASH */
   IN_SQ_STRING,		/* STAR */
   IN_SQ_STRING,		/* DASH */
   IN_SQ_STRING,		/* SEMI */
   IN_SQ_STRING,		/* OTHER */
   }
};


static action[NUM_STATES][NUM_CLASSES] = {
  /* ACCEPT */
  {
   QUIT,			/* NL */
   QUIT,			/* WS */
   QUIT,			/* SQ */
   QUIT,			/* DQ */
   QUIT,			/* ESCAPE */
   QUIT,			/* SLASH */
   QUIT,			/* STAR */
   QUIT,			/* DASH */
   QUIT,			/* SEMI */
   QUIT,			/* OTHER */
   },

  /* COPY */
  {
   INSERT_WS,			/* NL */
   INSERT_WS,			/* WS */
   COPY_INPUT,			/* SQ */
   COPY_INPUT,			/* DQ */
   COPY_INPUT,			/* ESCAPE */
   HOLD,			/* SLASH */
   COPY_INPUT,			/* STAR */
   HOLD,			/* DASH */
   COPY_INPUT,			/* SEMI */
   COPY_INPUT,			/* OTHER */
   },

  /* ONE_SLASH */
  {
   COPY_HOLD_AND_INSERT_WS,	/* NL */
   COPY_HOLD_AND_INSERT_WS,	/* WS */
   COPY_HOLD_AND_INPUT,		/* SQ */
   COPY_HOLD_AND_INPUT,		/* DQ */
   COPY_HOLD_AND_INPUT,		/* ESCAPE */
   COPY_INPUT,			/* SLASH */
   SKIP,			/* STAR */
   COPY_HOLD_AND_INPUT,		/* DASH */
   COPY_HOLD_AND_INPUT,		/* SEMI */
   COPY_HOLD_AND_INPUT,		/* OTHER */
   },

  /* IN_C_COMMENT */
  {
   SKIP,			/* NL */
   SKIP,			/* WS */
   SKIP,			/* SQ */
   SKIP,			/* DQ */
   SKIP,			/* ESCAPE */
   SKIP,			/* SLASH */
   SKIP,			/* STAR */
   SKIP,			/* DASH */
   SKIP,			/* SEMI */
   SKIP,			/* OTHER */
   },

  /* ONE_STAR */
  {
   SKIP,			/* NL */
   SKIP,			/* WS */
   SKIP,			/* SQ */
   SKIP,			/* DQ */
   SKIP,			/* ESCAPE */
   INSERT_WS,			/* SLASH */
   SKIP,			/* STAR */
   SKIP,			/* DASH */
   SKIP,			/* SEMI */
   SKIP,			/* OTHER */
   },

  /* ONE_DASH */
  {
   COPY_HOLD_AND_INSERT_WS,	/* NL */
   COPY_HOLD_AND_INSERT_WS,	/* WS */
   COPY_HOLD_AND_INPUT,		/* SQ */
   COPY_HOLD_AND_INPUT,		/* DQ */
   COPY_HOLD_AND_INPUT,		/* ESCAPE */
   COPY_HOLD_AND_INPUT,		/* SLASH */
   COPY_HOLD_AND_INPUT,		/* STAR */
   SKIP,			/* DASH */
   COPY_HOLD_AND_INPUT,		/* SEMI */
   COPY_HOLD_AND_INPUT,		/* OTHER */
   },

  /* IN_SQL_COMMENT */
  {
   INSERT_WS,			/* NL */
   SKIP,			/* WS */
   SKIP,			/* SQ */
   SKIP,			/* DQ */
   SKIP,			/* ESCAPE */
   SKIP,			/* SLASH */
   SKIP,			/* STAR */
   SKIP,			/* DASH */
   SKIP,			/* SEMI */
   SKIP,			/* OTHER */
   },

  /* IN_DQ_STRING */
  {
   COPY_INPUT,			/* NL */
   COPY_INPUT,			/* WS */
   COPY_INPUT,			/* SQ */
   COPY_INPUT,			/* DQ */
   HOLD,			/* ESCAPE */
   COPY_INPUT,			/* SLASH */
   COPY_INPUT,			/* STAR */
   COPY_INPUT,			/* DASH */
   COPY_INPUT,			/* SEMI */
   COPY_INPUT,			/* OTHER */
   },

  /* IN_DQ_STRING_ESCAPE */
  {
   SKIP,			/* NL */
   COPY_HOLD_AND_INPUT,		/* WS */
   COPY_HOLD_AND_INPUT,		/* SQ */
   COPY_HOLD_AND_INPUT,		/* DQ */
   COPY_HOLD_AND_INPUT,		/* ESCAPE */
   COPY_HOLD_AND_INPUT,		/* SLASH */
   COPY_HOLD_AND_INPUT,		/* STAR */
   COPY_HOLD_AND_INPUT,		/* DASH */
   COPY_HOLD_AND_INPUT,		/* SEMI */
   COPY_HOLD_AND_INPUT,		/* OTHER */
   },

  /* IN_SQ_STRING */
  {
   COPY_INPUT,			/* NL */
   COPY_INPUT,			/* WS */
   COPY_INPUT,			/* SQ */
   COPY_INPUT,			/* DQ */
   HOLD,			/* ESCAPE */
   COPY_INPUT,			/* SLASH */
   COPY_INPUT,			/* STAR */
   COPY_INPUT,			/* DASH */
   COPY_INPUT,			/* SEMI */
   COPY_INPUT,			/* OTHER */
   },

  /* IN_SQ_STRING_ONE_SQ */
  {
   COPY_INPUT,			/* NL */
   COPY_INPUT,			/* WS */
   COPY_INPUT,			/* SQ */
   COPY_INPUT,			/* DQ */
   COPY_INPUT,			/* ESCAPE */
   COPY_INPUT,			/* SLASH */
   COPY_INPUT,			/* STAR */
   COPY_INPUT,			/* DASH */
   COPY_INPUT,			/* SEMI */
   COPY_INPUT,			/* OTHER */
   },

  /* IN_SQ_STRING_ESCAPE */
  {
   SKIP,			/* NL */
   COPY_HOLD_AND_INPUT,		/* WS */
   COPY_HOLD_AND_INPUT,		/* SQ */
   COPY_HOLD_AND_INPUT,		/* DQ */
   COPY_HOLD_AND_INPUT,		/* ESCAPE */
   COPY_HOLD_AND_INPUT,		/* SLASH */
   COPY_HOLD_AND_INPUT,		/* STAR */
   COPY_HOLD_AND_INPUT,		/* DASH */
   COPY_HOLD_AND_INPUT,		/* SEMI */
   COPY_HOLD_AND_INPUT,		/* OTHER */
   }
};


CHAR_CLASS classify[256];

static char *
fsm (void)
{
  /*
   * Make these local so that the optimizer has something to work with.
   *
   *  bp              Pointer to next available char in assembly buffer
   *  rp              Pointer to next character from raw buffer
   *  blimit          Limit of assembly buffer
   *  rlimit          Limit of the raw buffer
   *  pending_ws      True iff we should insert a blank before the next
   *                  non-blank character.
   *  hold            Holds a pending non-blank char; used for
   *                  escapes, etc.
   */
  char *bp;
  const char *blimit, *rp, *rlimit;
  int pending_ws;
  int hold = '?';
  STATE state;

#define DEPOSIT(ch) \
    do { \
	if (pending_ws) \
	{ \
	    if (bp != buf) \
		*bp++ = ' '; \
	    pending_ws = 0; \
	} \
	*bp++ = (ch); \
    } while (0)

  pending_ws = 0;
  state = COPY;

  if (raw_next >= raw_limit)
    {
      read_raw_fp ();
    }

  if (buf_next >= buf_limit)
    {
      grow_buf ();
    }

  for (; raw_next < raw_limit;)
    {
      /*
       * Set up limit so that we quit the loop when we exhaust either
       * the raw buffer or the accumulating buffer.  Make sure that
       * there are at least three characters in the assembly buffer,
       * since the COPY_HOLD_AND_INPUT action may need to deposit four
       * characters under the right circumstances (i.e., pending
       * whitespace preceding a "/;" will require us to deposit " /;\0"
       * in the buffer).
       */
      bp = buf_next;
      rp = raw_next;
      blimit = buf_limit - 3;	/* Artificially shorten */
      rlimit = raw_limit;

      /*
       * Now the actual finite state machine.  Because we have
       * artificially shortened the length of the assembly buffer, this
       * loop will break before any action can try to stuff more
       * characters than we can hold in the assembly buffer.
       */
      while (bp < blimit && rp < rlimit && state != ACCEPT)
	{
	  unsigned int ch;
	  CHAR_CLASS cc;
	  STATE st;

	  st = state;
	  ch = (unsigned char) *rp++;
	  cc = classify[ch];

	  state = next_state[st][cc];

	  switch (action[st][cc])
	    {
	    case COPY_INPUT:
	      DEPOSIT (ch);
	      break;

	    case COPY_INPUT_AND_INSERT_WS:
	      DEPOSIT (ch);
	      pending_ws++;
	      break;

	    case HOLD:
	      hold = ch;
	      break;

	    case COPY_HOLD_AND_INPUT:
	      DEPOSIT (hold);
	      *bp++ = ch;
	      break;

	    case COPY_HOLD_AND_INSERT_WS:
	      DEPOSIT (hold);
	      pending_ws++;
	      break;

	    case INSERT_WS:
	      pending_ws++;
	      break;

	    case SKIP:
	      break;

	    case QUIT:
	      /*
	       * This is actually unreachable, since the loop should be
	       * terminated first by recognizing (state == ACCEPT).
	       */
	      goto maybe_accept;
	    }
	}

      /*
       * Make sure that the globals are updated from our cached
       * versions.
       */
    maybe_accept:
      buf_next = bp;
      raw_next = rp;

      if (state == ACCEPT)
	{
	  *buf_next++ = '\0';
	  buf_next = buf;
	  return buf;
	}

      /*
       * We got here either because we ran out of room in the assembly
       * buffer, or because we exhausted the raw buffer (or possibly
       * both at the same time).  Fix one of the problems and try
       * again.
       */
      if (rp >= rlimit)
	{
	  read_raw_fp ();
	}
      else
	{
	  grow_buf ();
	}
    }

  /*
   * If we get here we have exhausted the input file.  We either have
   * something interesting in the accumulation buffer or we don't.  If
   * we do, terminate it properly and return it.  If we don't, free the
   * accumulation buffer and return NULL.
   */
  if (buf_next > buf)
    {
      *buf_next++ = '\0';
    }
  else
    {
      if (buf)
	{
	  free (buf);
	}
      buf = NULL;
    }

  buf_next = buf;
  buf_limit = buf;

  return buf;
}

#define DIM(x) (sizeof (x) / sizeof (x[0]))

static void
init (void)
{
  int i;

  for (i = 0; i < DIM (classify); i++)
    classify[i] = (common_char_isspace (i) ? WS : OTHER);

  classify['\n'] = NL;
  classify['\''] = SQ;
  classify['"'] = DQ;
  classify['\\'] = ESCAPE;
  classify['/'] = SLASH;
  classify['*'] = STAR;
  classify['-'] = DASH;
  classify[';'] = SEMI;
}


static void
restart (FILE * fp)
{
  raw_fp = fp;
  raw_next = raw_buf;
  raw_limit = raw_buf;

  buf_next = buf;
  buf_limit = buf;
}


/*
 * get_next_stmt
 *
 * Arguments:
 *      fp: file to get the next line from
 *
 * Returns: char *
 *
 * Errors:
 *
 * Description:
 */

char *
get_next_stmt (FILE * fp)
{
  static int initialized = 0;

  if (!initialized)
    {
      init ();
      initialized++;
      parse_line_num = 0;
    }

  if (fp != raw_fp)
    {
      restart (fp);
      parse_line_num = 0;
    }

  return fsm ();
}


void
end_stmts (void)
{
  restart (NULL);
}

#ifdef TEST_PARSER

int
main (int argc, const char *argv[])
{
  const char *default_files[] = { "-", NULL };
  const char **files = default_files;
  const char *filename;
  int i;

  if (argc > 1)
    files = &argv[1];

  for (i = 0, filename = files[0]; filename; filename = files[++i])
    {
      FILE *fp;
      const char *line;

      if (strcmp (filename, "-") == 0)
	fp = stdin;
      else
	{
	  if ((fp = fopen (filename, "r")) == NULL)
	    {
	      perror (filename);
	      continue;
	    }
	}

      while ((line = get_next_stmt (fp)))
	{
	  fputs (line, stdout);
	  fputc ('\n', stdout);
	  fflush (stdout);
	}
    }

  exit (0);
  return 0;
}

#endif
