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
 *      Overview: Common functions for mysql                                          
 *                                                                            
 */


#include <sys/types.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <assert.h>
#include <errno.h>
#include <limits.h>

#include "common.h"

/*
 * common_char_isspace() - test for a white space character
 *   return: non-zero if c is a white space character,
 *           0 otherwise.
 *   c (in): the character to be tested
 */
int
common_char_isspace (int c)
{
  return ((c) == ' ' || (c) == '\t' || (c) == '\r' || (c) == '\n'
	  || (c) == '\f' || (c) == '\v');
}

/*
 * common_char_isupper() - test for a upper case character
 *   return: non-zero if c is a upper case character,
 *           0 otherwise.
 *   c (in): the character to be tested
 */
int
common_char_isupper (int c)
{
  return ((c) >= 'A' && (c) <= 'Z');
}

/*
 * common_char_tolower() - convert uppercase character to lowercase
 *   return: lowercase character corresponding to the argument
 *   c (in): the character to be converted
 */
int
common_char_tolower (int c)
{
  return (common_char_isupper ((c)) ? ((c) - ('A' - 'a')) : (c));
}

/*
 * str_to_int32() - convert a string to a integer
 *   return: int
 *  
 */
static int
str_to_int32 (int *ret_p, char **end_p, const char *str_p, int base)
{
  long val = 0;

  assert (ret_p != NULL);
  assert (end_p != NULL);
  assert (str_p != NULL);

  *ret_p = 0;
  *end_p = NULL;

  errno = 0;
  val = strtol (str_p, end_p, base);

  if ((errno == ERANGE && (val == LONG_MAX || val == LONG_MIN))
      || (errno != 0 && val == 0))
    {
      return -1;
    }

  if (*end_p == str_p)
    {
      return -1;
    }

  /* Long is 8 bytes and int is 4 bytes in Linux 64bit, so the
   * additional check of integer range is necessary.
   */
  if (val < INT_MIN || val > INT_MAX)
    {
      return -1;
    }

  *ret_p = (int) val;

  return 0;
}

/*
 * common_parse_int() - parse a integer from a string
 *   return: int
 *  
 */
int
common_parse_int (int *ret_p, const char *str_p, int base)
{
  int error = 0;
  int val;
  char *end_p;

  assert (ret_p != NULL);
  assert (str_p != NULL);

  *ret_p = 0;

  error = str_to_int32 (&val, &end_p, str_p, base);
  if (error < 0)
    {
      return -1;
    }

  if (*end_p != '\0')
    {
      return -1;
    }

  *ret_p = val;

  return 0;

}

/*
 * skip_white_comments() - skip whit and comments
 *   return: void
 *  
 */
void
skip_white_comments (char **str)
{
  int state = 0;

  for (;; (*str)++)
    {
      switch (state)
	{
	case 0:
	  if (**str == '/')
	    {
	      state = 1;
	    }
	  else if (!common_char_isspace (**str))
	    {
	      state = 4;
	    }
	  break;
	case 1:
	  if (**str == '*')
	    {
	      state = 2;
	    }
	  else
	    {
	      state = 4;
	      /* Backtrack the pointer one location */
	      str--;
	    }
	  break;
	case 2:
	  if (**str == '*')
	    {
	      state = 3;
	    }
	  break;
	case 3:
	  if (**str == '/')
	    {
	      state = 0;
	    }
	  else if (**str != '*')
	    {
	      state = 2;
	    }
	  break;
	}
      if (state == 4)
	{
	  break;
	}
    }
}

/*
 * args_parse_strings_new() - parse arguments from argv
 *   return: int
 *  
 */
int
args_parse_strings_new (ARGDEF * arguments, int argc, char *argv[])
{
  int i;
  int index;
  int found = 0;

  for (i = 1; i < argc; i++)
    {
      for (index = 0; arguments[index].name != NULL; index++)
	{
	  if (found == 1)
	    {
	      found = 0;
	      break;
	    }
	  /* required.. */
	  if (arguments[index].key == NULL)
	    {
	      if (*((char **) arguments[index].variable) != NULL)
		{
		  continue;
		}
	      *((char **) arguments[index].variable) = argv[i];
	      break;
	    }

	  if (strcmp (arguments[index].key, argv[i]) == 0)
	    {
	      switch (arguments[index].type)
		{
		case ARG_INTEGER:
		  if (i + 1 == argc)
		    {
		      printf ("there's no arg..\n");
		      return ER_FAILED;
		    }
		  if (common_parse_int
		      (arguments[index].variable, argv[i + 1], 10) != 0)
		    {
		      printf ("common_parse_int() error\n");
		      return ER_FAILED;
		    }
		  i++;
		  found = 1;
		  break;

		case ARG_STRING:
		  if (i + 1 == argc)
		    {
		      printf ("there's no arg..\n");
		      return ER_FAILED;
		    }
		  *((char **) arguments[index].variable) = argv[i + 1];
		  i++;
		  found = 1;
		  break;

		case ARG_FLOAT:
		  printf ("ARG_FLOAT is not supported..\n");
		  break;

		case ARG_BOOLEAN:
		  *((int *) arguments[index].variable) = 1;
		  break;

		case ARG_NULL:
		  printf ("ARG_NULL is not supported..\n");
		  break;

		default:
		  break;
		}
	    }
	}
    }

  return NO_ERROR;
}
