# pgauditor
pgauditor is a single file bash script which generates audit tables and trigger functions for PostgreSQL. Its audit tables can collect who, what, where, and when. This approach can quickly be implemented within any PostgreSQL application. Many of the generated implementation details can be customized with the CLI. A core design principle of pgauditor is that it does not delete or modify information under any circumstances. This is intentional because it mitiges the risk of a bug destroying information, which would be the exact opposite of its intended purpose.

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
