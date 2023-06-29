# pgauditor
pgauditor is a single file bash script which generates audit tables and trigger functions for PostgreSQL. Its audit tables can who, what, where, and when. This approach can quickly be implemented within any PostgreSQL application. Many of the generated implementation details can be customized with the CLI.

IMPORTANT: A core design principle of pgauditor is that it does not delete or modify information under any circumstances. This is intentional because it mitiges the risk of a bug destroying information, which would be the exact opposite of its intended purpose.

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
