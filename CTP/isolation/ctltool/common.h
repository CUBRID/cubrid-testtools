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
 * Overview:  Header file for common utility routines     
 */


#ifndef _COMMON_H_
#define _COMMON_H_

#if defined (__cplusplus)
extern "C"
{
#endif

#ifndef NO_ERROR
#define NO_ERROR 0
#endif

#ifndef ER_FAILED
#define ER_FAILED -1
#endif

  enum argtypes
  {
    ARG_INTEGER,
    ARG_STRING,
    ARG_FLOAT,
    ARG_BOOLEAN,
    ARG_NULL
  };
  typedef enum argtypes ARGTYPE;


/* Constrant string style argument definitions */
  typedef struct argdef ARGDEF;
  struct argdef
  {
    const char *name;
    ARGTYPE type;
    const char *key;
    void *variable;
    const char *help;
    int (*validate) (void *variable, FILE * outfp);
  };


  int common_char_isupper (int c);
  int common_char_isspace (int c);
  int common_char_tolower (int c);
  int common_parse_int (int *ret_p, const char *str_p, int base);
  void skip_white_comments (char **str);
  int args_parse_strings_new (ARGDEF * arguments, int argc, char *argv[]);


#if defined (__cplusplus)
}
#endif

#endif
