# pg-audit
Assortment of plpgsql functions which can generate tables plpgsql functions for auditing tables in PostgreSQL databases. Generated audit triggers can, optionally, capture the current user.

## Available functions

| Function        | Description                                  |                                 
|-----------------|----------------------------------------------|
| pgaudit_create  | creates an audit table and starts auditing   |
| pgaudit_update  | adds columns to audit table if necessary     |
| pgaudit_drop    | ceases auditing and drops the audit table    |
| pgaudit_disable | ceases auditing but leaves audit table alone |
