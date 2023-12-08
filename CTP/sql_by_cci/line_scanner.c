/*
 * Copyright 2008 Search Solution Corporation
 * Copyright 2016 CUBRID Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

#include <assert.h>

typedef enum csql_statement_state
{
  CSQL_STATE_GENERAL = 0,
  CSQL_STATE_C_COMMENT,
  CSQL_STATE_CPP_COMMENT,
  CSQL_STATE_SQL_COMMENT,
  CSQL_STATE_SINGLE_QUOTE,
  //CSQL_STATE_MYSQL_QUOTE,
  CSQL_STATE_DOUBLE_QUOTE_IDENTIFIER,
  CSQL_STATE_BACKTICK_IDENTIFIER,
  CSQL_STATE_BRACKET_IDENTIFIER,
  CSQL_STATE_STATEMENT_END
} CSQL_STATEMENT_STATE;

typedef enum csql_statement_substate
{
  CSQL_SUBSTATE_INITIAL = 0,
  CSQL_SUBSTATE_SEEN_CREATE,
  CSQL_SUBSTATE_SEEN_OR,
  CSQL_SUBSTATE_SEEN_REPLACE,
  CSQL_SUBSTATE_EXPECTING_IS_OR_AS,
  CSQL_SUBSTATE_PL_LANG_SPEC,
  CSQL_SUBSTATE_SEEN_LANGUAGE,
  CSQL_SUBSTATE_PLCSQL_TEXT,
  CSQL_SUBSTATE_SEEN_END
} CSQL_STATEMENT_SUBSTATE;

static CSQL_STATEMENT_STATE g_state = CSQL_STATE_GENERAL;
// following three fields are used to identify the beginning and the end of PL/CSQL texts
static CSQL_STATEMENT_SUBSTATE g_substate = CSQL_SUBSTATE_INITIAL;
static int g_plcsql_begin_end_balance = 0;
static int g_plcsql_nest_level = 0;

static int
is_identifier_letter (const char c)
{
  return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (c == '_') ? 1 : 0;
}

static int
match_word_ci (const char *word, const char **bufp)
{
  int len = strlen (word);
  assert (len > 0);

  if (strncasecmp (word, *bufp, len) == 0 && !is_identifier_letter ((*bufp)[len]))
    {
      *bufp += (len - 1);	// advance the pointer to the last letter
      return 1;
    }
  else
    {
      return 0;
    }
}

int
is_in_plcsql_text(void)
{
  return (g_substate == CSQL_SUBSTATE_PLCSQL_TEXT || g_substate == CSQL_SUBSTATE_SEEN_END) ? 1 : 0;
}

void
clear_line_scanner_state ()
{
  g_state = CSQL_STATE_GENERAL;
  g_substate = CSQL_SUBSTATE_INITIAL;
  g_plcsql_begin_end_balance = 0;
  g_plcsql_nest_level = 0;
}

void
scan_line (const char *line)
{
  int stmt_complete = 0;
  const char *p;
  int str_length;

  assert (line);

  // the state must be cleared to State.GENERAL before this call
  assert (g_state != CSQL_STATE_STATEMENT_END);
  assert ((g_plcsql_begin_end_balance == 0 && g_plcsql_nest_level == 0) ||
	  (g_substate == CSQL_SUBSTATE_PLCSQL_TEXT || g_substate == CSQL_SUBSTATE_SEEN_END));

  if (g_state == CSQL_STATE_CPP_COMMENT || g_state == CSQL_STATE_SQL_COMMENT)
    {
      /* these are single line comments and we're parsing a new line */
      g_state = CSQL_STATE_GENERAL;
    }

  str_length = strlen (line);
  /* run as g_state machine */
  for (p = line; p < line + str_length; p++)
    {
      switch (g_state)
	{
	case CSQL_STATE_GENERAL:

	  // eat up blanks
	  switch (*p)
	    {
	    case ' ':
	    case '\t':
	    case '\r':
	    case '\n':
	      continue;
	    }

	  // here, *p is a non-white-space

	substate_transition:
	  switch (g_substate)
	    {
	    case CSQL_SUBSTATE_INITIAL:
	      if (match_word_ci ("create", &p))
		{
		  g_substate = CSQL_SUBSTATE_SEEN_CREATE;
		  continue;
		}
	      else
		{
		  // keep the g_substate CSQL_SUBSTATE_INITIAL
		  // break and proceed to the second switch
		}
	      break;

	    case CSQL_SUBSTATE_SEEN_CREATE:
	      if (match_word_ci ("or", &p))
		{
		  g_substate = CSQL_SUBSTATE_SEEN_OR;
		  continue;
		}
	      else if (match_word_ci ("procedure", &p) || match_word_ci ("function", &p))
		{
		  g_substate = CSQL_SUBSTATE_EXPECTING_IS_OR_AS;
		  continue;
		}
	      else
		{
		  g_substate = CSQL_SUBSTATE_INITIAL;
		  // break and proceed to the second switch
		}
	      break;

	    case CSQL_SUBSTATE_SEEN_OR:
	      if (match_word_ci ("replace", &p))
		{
		  g_substate = CSQL_SUBSTATE_SEEN_REPLACE;
		  continue;
		}
	      else
		{
		  g_substate = CSQL_SUBSTATE_INITIAL;
		  // break and proceed to the second switch
		}
	      break;

	    case CSQL_SUBSTATE_SEEN_REPLACE:
	      if (match_word_ci ("procedure", &p) || match_word_ci ("function", &p))
		{
		  g_substate = CSQL_SUBSTATE_EXPECTING_IS_OR_AS;
		  continue;
		}
	      else
		{
		  g_substate = CSQL_SUBSTATE_INITIAL;
		  // break and proceed to the second switch
		}
	      break;

	    case CSQL_SUBSTATE_EXPECTING_IS_OR_AS:
	      if (match_word_ci ("is", &p) || match_word_ci ("as", &p))
		{
		  g_substate = CSQL_SUBSTATE_PL_LANG_SPEC;
		  continue;
		}
	      else
		{
		  // keep the g_substate CSQL_SUBSTATE_EXPECTING_IS_OR_AS
		  // break and proceed to the second switch
		}
	      break;

	    case CSQL_SUBSTATE_PL_LANG_SPEC:
	      if (match_word_ci ("language", &p))
		{
		  g_substate = CSQL_SUBSTATE_SEEN_LANGUAGE;
		  continue;
		}
	      else
		{
		  // TRANSITION to CSQL_SUBSTATE_PLCSQL_TEXT!!!
		  g_substate = CSQL_SUBSTATE_PLCSQL_TEXT;
		  g_plcsql_begin_end_balance = 0;
		  g_plcsql_nest_level = 0;
		  goto substate_transition;	// use goto to repeat a g_substate transition without increasing p
		}
	      break;

	    case CSQL_SUBSTATE_SEEN_LANGUAGE:
	      if (match_word_ci ("java", &p))
		{
		  g_substate = CSQL_SUBSTATE_INITIAL;
		  continue;
		}
	      else if (match_word_ci ("plcsql", &p))
		{
		  // TRANSITION to CSQL_SUBSTATE_PLCSQL_TEXT!!!
		  g_substate = CSQL_SUBSTATE_PLCSQL_TEXT;
		  g_plcsql_begin_end_balance = 0;
		  g_plcsql_nest_level = 0;
		  continue;
		}
	      else
		{
		  // syntax error
		  g_substate = CSQL_SUBSTATE_INITIAL;
		  // break and proceed to the second switch
		}
	      break;

	    case CSQL_SUBSTATE_PLCSQL_TEXT:
	      if (match_word_ci ("procedure", &p) || match_word_ci ("function", &p))
		{
		  if (g_plcsql_begin_end_balance == 0)
		    {
		      g_plcsql_nest_level++;
		    }
		  continue;
		}
	      else if (match_word_ci ("case", &p))
		{
		  // case can start an expression and can appear in a balance 0 area
		  if (g_plcsql_begin_end_balance == 0)
		    {
		      g_plcsql_nest_level++;
		    }
		  g_plcsql_begin_end_balance++;
		  continue;
		}
	      else if (match_word_ci ("begin", &p) || match_word_ci ("if", &p) || match_word_ci ("loop", &p))
		{
		  g_plcsql_begin_end_balance++;
		  continue;
		}
	      else if (match_word_ci ("end", &p))
		{
		  g_substate = CSQL_SUBSTATE_SEEN_END;
		  continue;
		}
	      else
		{
		  // keep the g_substate CSQL_SUBSTATE_PLCSQL_TEXT
		  // break and proceed to the second switch
		}
	      break;

	    case CSQL_SUBSTATE_SEEN_END:
	      g_plcsql_begin_end_balance--;
	      if (g_plcsql_begin_end_balance < 0)
		{
		  // syntax error
		  g_plcsql_begin_end_balance = 0;
		}
	      if (g_plcsql_begin_end_balance == 0)
		{
		  g_plcsql_nest_level--;
		  if (g_plcsql_nest_level < 0)
		    {
		      // the last END closing PL/CSQL text was found
		      g_substate = CSQL_SUBSTATE_INITIAL;
		      g_plcsql_begin_end_balance = 0;
		      g_plcsql_nest_level = 0;
		      goto substate_transition;	// use goto to repeat a g_substate transition without increasing p
		    }
		}

	      g_substate = CSQL_SUBSTATE_PLCSQL_TEXT;

	      // match if/case/loop if exists, but just advance p and ignore them
	      if (match_word_ci ("if", &p) || match_word_ci ("case", &p) || match_word_ci ("loop", &p))
		{
		  continue;
		}
	      else
		{
		  goto substate_transition;	// use goto to repeat a g_substate transition without increasing p
		}

	      break;

	    default:
	      assert (0);	// unreachable
	    }

	  if (is_identifier_letter (*p))
	    {
	      if (stmt_complete)
		{
		  stmt_complete = 0;
		}

	      // once an identifier letter is found, advance p while the next letter is also an identifir letter
	      // in other words, consume the whole identifier
	      while (p + 1 < line + str_length && is_identifier_letter (*(p + 1)))
		{
		  p++;
		}
	      continue;
	    }

	  switch (*p)
	    {
	    case '/':
	      if (*(p + 1) == '/')
		{
		  g_state = CSQL_STATE_CPP_COMMENT;
		  p++;
		  break;
		}
	      if (*(p + 1) == '*')
		{
		  g_state = CSQL_STATE_C_COMMENT;
		  p++;
		  break;
		}
	      stmt_complete = 0;
	      break;
	    case '-':
	      if (*(p + 1) == '-')
		{
		  g_state = CSQL_STATE_SQL_COMMENT;
		  p++;
		  break;
		}
	      stmt_complete = 0;
	      break;
	    case '\'':
	      g_state = CSQL_STATE_SINGLE_QUOTE;
	      stmt_complete = 0;
	      break;
	    case '"':
	      g_state = CSQL_STATE_DOUBLE_QUOTE_IDENTIFIER;
	      stmt_complete = 0;
	      break;
	    case '`':
	      g_state = CSQL_STATE_BACKTICK_IDENTIFIER;
	      stmt_complete = 0;
	      break;
	    case '[':
	      g_state = CSQL_STATE_BRACKET_IDENTIFIER;
	      stmt_complete = 0;
	      break;
	    case ';':
	      if (g_substate != CSQL_SUBSTATE_PLCSQL_TEXT)
		{
		  assert (g_substate != CSQL_SUBSTATE_SEEN_END);

		  stmt_complete = 1;

		  // initialize the g_state variables used to identify PL/CSQL text
		  g_substate = CSQL_SUBSTATE_INITIAL;
		  g_plcsql_begin_end_balance = 0;
		  g_plcsql_nest_level = 0;
		}
	      break;
	    case ' ':
	    case '\t':
	    case '\r':
	    case '\n':
	      assert (0);	// unreachable
	      break;
	    default:
	      if (stmt_complete)
		{
		  stmt_complete = 0;
		}
	      break;
	    }
	  break;

	case CSQL_STATE_C_COMMENT:
	  if (*p == '*' && *(p + 1) == '/')
	    {
	      g_state = CSQL_STATE_GENERAL;
	      p++;
	      break;
	    }
	  break;

	case CSQL_STATE_CPP_COMMENT:
	  if (*p == '\n')
	    {
	      g_state = CSQL_STATE_GENERAL;
	    }
	  break;

	case CSQL_STATE_SQL_COMMENT:
	  if (*p == '\n')
	    {
	      g_state = CSQL_STATE_GENERAL;
	    }
	  break;

	case CSQL_STATE_SINGLE_QUOTE:
	  if (*p == '\'')
	    {
	      if (*(p + 1) == '\'')
		{
		  /* escape by '' */
		  p++;
		}
	      else
		{
		  g_state = CSQL_STATE_GENERAL;
		}
	    }
	  break;

	case CSQL_STATE_DOUBLE_QUOTE_IDENTIFIER:
	  if (*p == '"')
	    {
	      g_state = CSQL_STATE_GENERAL;
	    }
	  break;

	case CSQL_STATE_BACKTICK_IDENTIFIER:
	  if (*p == '`')
	    {
	      g_state = CSQL_STATE_GENERAL;
	    }
	  break;

	case CSQL_STATE_BRACKET_IDENTIFIER:
	  if (*p == ']')
	    {
	      g_state = CSQL_STATE_GENERAL;
	    }
	  break;

	default:
	  /* should not be here */
	  break;
	}
    }

  /* when include other stmts and the last smt is non sense stmt. */
  if (stmt_complete
      && (g_state == CSQL_STATE_SQL_COMMENT || g_state == CSQL_STATE_CPP_COMMENT || g_state == CSQL_STATE_GENERAL))
    {
      g_state = CSQL_STATE_STATEMENT_END;
    }
}
