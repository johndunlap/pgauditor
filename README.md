# pgauditor
This project consists of a single bash shell script which generates audit tables and trigger functions for auditing PostgreSQL database tables. These triggers can capture who, what, where and when.

## Connection parameters
Set connection parameters by defining the following environment variables:

https://www.postgresql.org/docs/current/libpq-envars.html
* PGHOST
* PGPORT
* PGDATABASE
* PGUSER
* PGPASSWORD

## Usage
```bash
./pgauditor TABLE | psql
```
