# pg-audit
Assortment of plpgsql functions which can generate tables plpgsql functions for auditing tables in PostgreSQL databases. Generated audit triggers can, optionally, capture the current user.

## Available functions

| Function              | Description                                                                                                                                                                        |                                 
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| pgaudit_create(text)  | Accepts a table name as an argument and outputs SQL for creating an audit table and audit triggers for the given table                                                             |
| pgaudit_update(text)  | Accepts a table name as an argument and outputs SQL for adding columns to the audit table if there are any new columns in the given table which should be added to the audit table |
| pgaudit_drop(text)    | Accepts a table name as an argument and outputs SQL for dropping the audit table and audit triggers for the given table                                                            |
| pgaudit_disable(text) | Accepts a table name as an argument and outputs SQL for dropping the audit triggers for the given table. This assumes that the given table is already audited.                     |
| pgaudit_enable(text)  | Accepts a table name as an argument and outputs SQL for creating audit triggers for the given table. This assumes that the given table is already audited.                         |
