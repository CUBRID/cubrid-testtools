create table test (a int primary key, b char(5), c varchar(5));

insert into test values (1,'abc','abc');
insert into test values (2,'efghi','efghi');
insert into test values (3,'cub','cub');

select * from test;

update test set b='zzz' where a=1;

select * from test where b='zzz';

delete from test where c='cub';

select c from test;

drop table test;
