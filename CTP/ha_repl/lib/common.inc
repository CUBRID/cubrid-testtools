check_server_status=cmd:cubrid server status;cubrid_rel | awk '{print $4}'

HC_CHECK_FOR_CMD_EACH_STATEMENT_0=cmd: sleep 3

HC_CHECK_FOR_EACH_STATEMENT_1=select 'db_stored_procedure_args' TABLE_NAME, db_stored_procedure_args.* from db_stored_procedure_args order by 1,2,3,4,5,6,7;
HC_CHECK_FOR_EACH_STATEMENT_2=select 'db_stored_procedure' TABLE_NAME, sp_name, pkg_name, sp_type, return_type, arg_count, lang, authid, owner, code, comment from db_stored_procedure order by 1,2,3,4,5,6,7,8,9;
HC_CHECK_FOR_EACH_STATEMENT_3=select 'db_partition' TABLE_NAME, db_partition.* from db_partition order by 1,2,3,4,5,6,7,8,9;
HC_CHECK_FOR_EACH_STATEMENT_4=select 'db_trigger' TABLE_NAME, trigger_name, owner_name, target_class_name, target_owner_name, target_attr_name, target_attr_type, action_type, action_time, comment from db_trigger order by 1,2,3,4,5,6,7,8;
HC_CHECK_FOR_EACH_STATEMENT_5=select 'db_index_key' TABLE_NAME, db_index_key.* from db_index_key order by 1,2,3,4,5,6,7,8;
HC_CHECK_FOR_EACH_STATEMENT_6=select 'db_index' TABLE_NAME, index_name, is_unique, is_reverse, class_name, owner_name, key_count, is_primary_key, is_foreign_key, filter_expression, have_function, status, referential_index_class_owner_name, referential_index_class_name, referential_index_name, delete_rule, update_rule, referential_match_option, index_type, deduplicate_key_level, comment from db_index order by 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20;
HC_CHECK_FOR_EACH_STATEMENT_7=select 'db_meth_file' TABLE_NAME, db_meth_file.* from db_meth_file order by 1,2,3,4;
HC_CHECK_FOR_EACH_STATEMENT_8=select 'db_meth_arg_setdomain_elm' TABLE_NAME, db_meth_arg_setdomain_elm.* from db_meth_arg_setdomain_elm order by 1,2,3,4,5,6,7,8,9;
HC_CHECK_FOR_EACH_STATEMENT_9=select 'db_method' TABLE_NAME, db_method.* from db_method order by 1,2,3,4,5,6,7;
HC_CHECK_FOR_EACH_STATEMENT_10=select 'db_attr_setdomain_elm' TABLE_NAME, db_attr_setdomain_elm.* from db_attr_setdomain_elm order by 1,2,3,4,5,6,7,8;
HC_CHECK_FOR_EACH_STATEMENT_11=select 'db_attribute' TABLE_NAME, db_attribute.* from db_attribute order by 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16;
HC_CHECK_FOR_EACH_STATEMENT_12=select 'db_vclass' TABLE_NAME, db_vclass.* from db_vclass order by 1,2,3,4;
HC_CHECK_FOR_EACH_STATEMENT_13=select 'db_direct_super_class' TABLE_NAME, db_direct_super_class.* from db_direct_super_class order by 1,2;
HC_CHECK_FOR_EACH_STATEMENT_14=select 'db_class' TABLE_NAME, class_name, owner_name, class_type, is_system_class, tde_algorithm, statistics_strategy, partitioned, is_reuse_oid_class, collation, comment from db_class order by 1,2,3,4,5,6,7,8,9,10;
HC_CHECK_FOR_EACH_STATEMENT_15=select '_db_serial' TABLE_NAME, if(owner is null, USER, (select name from db_user where db_user=owner)) owner, _db_serial.name, _db_serial.current_val, _db_serial.increment_val, _db_serial.max_val, _db_serial.min_val, _db_serial.start_val, _db_serial.cyclic, _db_serial.started, _db_serial.class_name, _db_serial.att_name, _db_serial.cached_num, _db_serial.comment from _db_serial;
HC_CHECK_FOR_EACH_STATEMENT_16=select '_db_collation' TABLE_NAME, _db_collation.* from _db_collation order by 1,2,3,4,5,6,7,8,9;
HC_CHECK_FOR_EACH_STATEMENT_17=select '_db_data_type' TABLE_NAME, _db_data_type.* from _db_data_type order by 1,2,3;
HC_CHECK_FOR_EACH_STATEMENT_18=select '_db_query_spec' TABLE_NAME, _db_query_spec.* from _db_query_spec order by 3,1,2;
HC_CHECK_FOR_EACH_STATEMENT_19=select '_db_meth_arg' TABLE_NAME, _db_meth_arg.* from _db_meth_arg order by 1,2,3,4,5;
HC_CHECK_FOR_EACH_STATEMENT_20=select '_db_meth_sig' TABLE_NAME, _db_meth_sig.* from _db_meth_sig order by 1,2,3,4,5;
HC_CHECK_FOR_EACH_STATEMENT_21=select '_db_trigger' TABLE_NAME, unique_name, owner, name, status, priority, event, target_class, target_attribute, target_class_attribute, condition_type, condition, condition_time, action_type, action_definition, action_time, comment from db_trigger order by 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16;
HC_CHECK_FOR_EACH_STATEMENT_22=select 'db_password' TABLE_NAME, db_password.* from db_password order by 1,2;
HC_CHECK_FOR_EACH_STATEMENT_23=select 'db_root' TABLE_NAME, db_root.* from db_root order by 1,2,3,4,5;

--HC_CHECK_FOR_EACH_STATEMENT_5=select * from db_auth order by 1,2,3,4;
--HC_CHECK_FOR_EACH_STATEMENT_33=select * from db_authorization order by 1,2;
--HC_CHECK_FOR_EACH_STATEMENT_35=select * from db_user order by 1,2,3,4,5,6,7;
--HC_CHECK_FOR_EACH_STATEMENT_21=select * from _db_auth order by 1,2,3,4,5;
