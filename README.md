# pg-audit
This project consists of a single sql file which contains plpgsql functions which generate plpgsql functions and audit tables for capturing changes to PostgreSQL database tables.

## Available functions

| Function              | Description                                                                                                                                                                                            |                                 
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| pgaudit_create(text)  | Accepts a table name as an argument and outputs SQL for creating an audit table and audit triggers for the given table. Audit tables are created with the name of the given table with an aud_ prefix. |
| pgaudit_update(text)  | Accepts a table name as an argument and outputs SQL for adding columns to the audit table if there are any new columns in the given table which should be added to the audit table.                    |
| pgaudit_drop(text)    | Accepts a table name as an argument and outputs SQL for dropping the audit table and audit triggers for the given table.                                                                               |
| pgaudit_disable(text) | Accepts a table name as an argument and outputs SQL for dropping the audit triggers for the given table.                                                                                               |
| pgaudit_enable(text)  | Accepts a table name as an argument and outputs SQL for creating audit triggers for the given table. Nothing will be output unless the audit table exists.                                             |
