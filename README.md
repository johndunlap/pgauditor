# pgauditor
pgauditor generates audit tables and trigger functions for PostgreSQL. Its audit tables can collect who, what, where, and when. This approach can quickly be implemented within any PostgreSQL application. Many of the generated implementation details can be customized with the CLI. A core design principle of pgauditor is that it does not delete or modify information under any circumstances. This is intentional because it mitiges the risk of a bug destroying information, which would be the exact opposite of its intended purpose.

## How it works
PostgreSQL supports trigger functions which can intercept SQL statements while they are being applied to your database. For example in the case of an SQL update statement, pgauditor would create an after update trigger on the table being audited. Within that trigger function, pgauditor can access both the old and new values of every column. These values are then written to the audit table with an insert statement. The trigger function captures "what", the current_timestamp function is used to capture "when", and there are multiple ways to capture "who". 

## Connection parameters
Connect to your database by setting the same environment variables that you would use to run psql. This is convenient because it allows you to pipe the output of pgauditor into psql. For more information see:
https://www.postgresql.org/docs/current/libpq-envars.html

At a minimum you will need to set the following environment variables:
* PGHOST
* PGPORT
* PGDATABASE
* PGUSER
* PGPASSWORD

Note that pgauditor does connect to your database during DDL generation to collect metadata, even if you don't run the generated DDL. If it didn't, you would have to provide more than the table name as an argument.

## Usage
```bash
# Minimal configuration which accepts all defaults
./pgauditor --table TABLE | psql
```

## Example
First, let's create a table and populate it with some data so that we have something to audit:
```sql
create table inventory (
  id bigserial primary key,
  name text not null,
  quantity integer not null,
  unit_price numeric(10,2) not null
);
insert into inventory (name, quantity, unit_price) values ('apple', 10, 1.00);
insert into inventory (name, quantity, unit_price) values ('banana', 20, 2.00);
insert into inventory (name, quantity, unit_price) values ('cherry', 30, 3.00);
```

We can run this DDL in psql to create the audit table and trigger functions:
```bash
./pgauditor --table inventory | psql
```

The output of this command is as follows:
```bash
CREATE SEQUENCE
CREATE TYPE
CREATE TABLE
CREATE FUNCTION
CREATE TRIGGER
CREATE TRIGGER
CREATE TRIGGER
```

Now, let's make some changes to the table and see what happens:
```sql
insert into inventory (name, quantity, unit_price) values ('date', 40, 4.00);
update inventory set quantity = 11 where name = 'apple';
delete from inventory where name = 'cherry';
```

We can see the audit records by querying the audit table:
```sql
select * from aud_inventory;
```

The output of this query is as follows:
| audit_id | operation | changed_by | changed_at                        | old_id | new_id | old_name | new_name | old_quantity | new_quantity | old_unit_price | new_unit_price |
|----------|-----------|------------|-----------------------------------|--------|--------|----------|----------|--------------|--------------|----------------|----------------|
| 1        | INSERT    | personal   | 2023-06-29 04:53:21.202671 +00:00 | null   | 4      | null     | date     | null         | 40           | null           | 4              |
| 2        | UPDATE    | personal   | 2023-06-29 04:53:22.135707 +00:00 | null   | null   | null     | null     | 10           | 11           | null           | null           |
| 3        | DELETE    | personal   | 2023-06-29 04:53:22.723849 +00:00 | 3      | null   | cherry   | null     | 30           | null         | 3              | null           | 
