drop table core_issue;

create table core_issue (
	id int primary key auto_increment,
	process_name varchar(100),
	detail_stack varchar(2000),
	digest_stack varchar(2000),
	issue_key varchar(50),
	issue_status varchar(20),
	create_time timestamp,
	update_time timestamp
);

	