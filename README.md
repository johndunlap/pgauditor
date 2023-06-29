# pgauditor
pgauditor is a single file bash script which automates the generation of audit tables and trigger functions for PostgreSQL. It captures details like who, what, where, and when. There are many ways to capture this information but this approach requires very little time or infrastructure to implement within an existing PostgreSQL schema.

## Connection parameters
Set connection parameters by defining the following environment variables:

https://www.postgresql.org/docs/current/libpq-envars.html
* PGHOST
* PGPORT
* PGDATABASE
* PGUSER
* PGPASSWORD
* PGAPPNAME

## Usage
```bash
./pgauditor TABLE | psql
```
