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

#ifndef _DB_CLI_H_
#define _DB_CLI_H_

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>

#if defined (__cplusplus)
extern "C"
{
#endif

  int init_db_client ();
  int shutdown_db (void);
  void clear_db_client_related ();
  int reconnect_to_server (void);

  int get_tran_id ();

  int is_client_restarted ();

  int login_db (char *host, char *username, char *password, char *db_name);

  int execute_sql_statement (FILE * fp, char *statement);
  int is_executing_end (int errid);

  const char *error_message ();

  char *print_class_info (char *cmd_ptr, FILE * file_handler);
  void print_ope (int num, const char *str);

  int local_tm_isblocked (int tran_index);


#if defined (__cplusplus)
}
#endif

#endif
