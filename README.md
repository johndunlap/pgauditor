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

Next, we will run pgauditor to generate the DDL for our audit table and trigger functions:
```bash
./pgauditor --table inventory
```

The output of this command is as follows:
```sql
CREATE TABLE IF NOT EXISTS aud_inventory(
  audit_id bigint UNIQUE NOT NULL DEFAULT nextval('pgauditor_audit_seq')
  ,operation pgauditor_operation
  ,changed_by text
  ,changed_at timestamp with time zone
  ,old_id bigint
  ,new_id bigint
  ,old_name text
  ,new_name text
  ,old_quantity integer
  ,new_quantity integer
  ,old_unit_price numeric
  ,new_unit_price numeric
);

CREATE OR REPLACE FUNCTION audit_inventory_changes() RETURNS TRIGGER
AS
$BODY$
DECLARE
  changed_by_var text := NULL;
  changed_at_var timestamp with time zone := current_timestamp;
  operation_var pgauditor_operation := NULL;
  old_id_var bigint := NULL;
  new_id_var bigint := NULL;
  old_name_var text := NULL;
  new_name_var text := NULL;
  old_quantity_var integer := NULL;
  new_quantity_var integer := NULL;
  old_unit_price_var numeric := NULL;
  new_unit_price_var numeric := NULL;
  change_count INT := 0;
BEGIN
  operation_var=TG_OP::pgauditor_operation;

select into changed_by_var current_user;

  IF (operation_var = 'UPDATE') THEN
    IF ((OLD.id IS NULL and NEW.id IS NOT NULL) or (OLD.id IS NOT NULL and NEW.id IS NULL) or (OLD.id != NEW.id)) THEN
      old_id_var := OLD.id;
      new_id_var := NEW.id;
      change_count := change_count + 1;
    END IF;
    IF ((OLD.name IS NULL and NEW.name IS NOT NULL) or (OLD.name IS NOT NULL and NEW.name IS NULL) or (OLD.name != NEW.name)) THEN
      old_name_var := OLD.name;
      new_name_var := NEW.name;
      change_count := change_count + 1;
    END IF;
    IF ((OLD.quantity IS NULL and NEW.quantity IS NOT NULL) or (OLD.quantity IS NOT NULL and NEW.quantity IS NULL) or (OLD.quantity != NEW.quantity)) THEN
      old_quantity_var := OLD.quantity;
      new_quantity_var := NEW.quantity;
      change_count := change_count + 1;
    END IF;
    IF ((OLD.unit_price IS NULL and NEW.unit_price IS NOT NULL) or (OLD.unit_price IS NOT NULL and NEW.unit_price IS NULL) or (OLD.unit_price != NEW.unit_price)) THEN
      old_unit_price_var := OLD.unit_price;
      new_unit_price_var := NEW.unit_price;
      change_count := change_count + 1;
    END IF;
  ELSIF (operation_var = 'INSERT') THEN
    new_id_var := NEW.id;
    new_name_var := NEW.name;
    new_quantity_var := NEW.quantity;
    new_unit_price_var := NEW.unit_price;
    change_count := change_count + 1;
  ELSIF (operation_var = 'DELETE') THEN
    old_id_var := OLD.id;
    old_name_var := OLD.name;
    old_quantity_var := OLD.quantity;
    old_unit_price_var := OLD.unit_price;
    change_count := change_count + 1;
  ELSE
    raise exception 'Unknown operation: %', operation_var;
  END IF;

  IF change_count > 0 THEN
    INSERT INTO aud_inventory(
      audit_id
      ,operation
      ,changed_by
      ,changed_at
      ,old_id
      ,new_id
      ,old_name
      ,new_name
      ,old_quantity
      ,new_quantity
      ,old_unit_price
      ,new_unit_price
    ) values(
      nextval('pgauditor_audit_seq')
      ,operation_var
      ,changed_by_var
      ,changed_at_var
      ,old_id_var
      ,new_id_var
      ,old_name_var
      ,new_name_var
      ,old_quantity_var
      ,new_quantity_var
      ,old_unit_price_var
      ,new_unit_price_var
    );
  END IF;
  RETURN NULL;
END;
$BODY$
LANGUAGE plpgsql VOLATILE;
CREATE TRIGGER aud_inventory_insert_trigger AFTER INSERT ON inventory FOR EACH ROW EXECUTE PROCEDURE audit_inventory_changes();
CREATE TRIGGER aud_inventory_update_trigger AFTER UPDATE ON inventory FOR EACH ROW EXECUTE PROCEDURE audit_inventory_changes();
CREATE TRIGGER aud_inventory_delete_trigger AFTER DELETE ON inventory FOR EACH ROW EXECUTE PROCEDURE audit_inventory_changes();
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
| 1        | INSERT    | personal   | 2023-06-29 04:53:21.202671 +00:00 |        | 4      |          | date     |              | 40           |                | 4              |
| 2        | UPDATE    | personal   | 2023-06-29 04:53:22.135707 +00:00 |        |        |          |          | 10           | 11           |                |                |
| 3        | DELETE    | personal   | 2023-06-29 04:53:22.723849 +00:00 | 3      |        | cherry   |          | 30           |              | 3              |                | 
