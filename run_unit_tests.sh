#!/bin/bash

# =============================================================================
#                               _ _ _
#   _ __   __ _  __ _ _   _  __| (_) |_ ___  _ __
#  | '_ \ / _` |/ _` | | | |/ _` | | __/ _ \| '__|
#  | |_) | (_| | (_| | |_| | (_| | | || (_) | |
#  | .__/ \__, |\__,_|\__,_|\__,_|_|\__\___/|_|
#  |_|    |___/
#
# =============================================================================
# MIT License
#
# Copyright (C) 2023 John Dunlap<john.david.dunlap@gmail.com>
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#
# =============================================================================
# Set connection parameters by defining the following environment variables:
# https://www.postgresql.org/docs/current/libpq-envars.html
# PGHOST
# PGPORT
# PGDATABASE
# PGUSER
# PGPASSWORD
#
# =============================================================================

export PGDATABASE='pgauditor'
export PGUSER=${PGUSER:-"pgauditor"}
export PGHOST=${PGHOST:-"localhost"}
export PGPORT=${PGPORT:-"5432"}

# Throw an error if we cannot connect to PostgreSQL
psql -d postgres -c "SELECT 1" 1> /dev/null 2>&1
if [ $? -ne 0 ]; then
  echo "ERROR: Cannot connect to PostgreSQL"
  exit 1
fi

# Verify that the user is a superuser
psql -d postgres -tc "SELECT usesuper FROM pg_user WHERE usename = current_user" 2>&1 | head -n 1 | grep "^ t$" 1> /dev/null
if [ $? -ne 0 ]; then
  echo "ERROR: User \"${PGUSER}\" must be a superuser to run the tests"
  exit 2
fi

# Throw an error if the database already exists as a safety precaution
psql -c "SELECT 1" 1> /dev/null 2>&1
if [ $? -eq 0 ]; then
  echo "ERROR: Database \"${PGDATABASE}\" already exists. Are you sure this is the correct database? The database used for testing must not exist prior to running the tests because the database will be created and dropped during the tests."
  exit 3
fi

function create_schema() {
  local schema_name="$1"
  psql <<EOF
  create schema ${schema_name};
EOF
}

function create_person_table() {
  local table_schema="$1"
  psql <<EOF
  create table ${table_schema}.person (
    id bigserial primary key,
    first_name text,
    last_name text,
    email text,
    phone text
  );
EOF
}

function create_database() {
  local database_name="$1"
  echo "Creating database \"${PGDATABASE}\""
  createdb "${PGDATABASE}"
}

function drop_database() {
  local database_name="$1"
  echo "Dropping database \"${PGDATABASE}\""
  dropdb "${PGDATABASE}"
}

# Begin writing unit tests
echo "Running unit tests..."

# Drop the database on exit
function on_exit() {
  local exit_status=$?
  local database_name="$1"

  # Just in case it was not dropped by the tests
  drop_database "${database_name}" 1> /dev/null 2>&1

  exit ${exit_status}
}

function run() {
  local test_name="$1"
  shift
  REMAINING_ARGS="$@"
  LOG_FILE=$(mktemp)
  "${test_name}" "${REMAINING_ARGS}" 1>"${LOG_FILE}" 2>&1
  SUCCESS=$?

  if [ ${SUCCESS} -eq 0 ]; then
    echo "PASS: ${test_name} ${REMAINING_ARGS}"
    rm -f "${LOG_FILE}"
    return 0
  else
    cat "${LOG_FILE}"
    echo "FAIL: ${test_name} ${REMAINING_ARGS}"
    rm -f "${LOG_FILE}"
    return 1
  fi

  return $SUCCESS
}

# Trap the exit signal to automatically drop the test database on exit
trap 'on_exit ${PGDATABASE}' EXIT

function test_create_audit_table() {
  local schema_name="$1"

  create_database "${PGDATABASE}"
  create_schema "pgauditor"
  create_person_table "pgauditor"

  # Verify that the table was created
  psql -c "select * from pgauditor.person" 1> /dev/null

  # Query the columns of the table from the information schema
  ACTUAL=$(psql -tc "select string_agg(g.column_name || ':' || g.data_type, ',') from (select column_name, data_type from information_schema.columns where table_name = 'person' and table_schema = 'pgauditor' order by column_name) g" | sed 's/^\s*//g')
  EXPECTED='email:text,first_name:text,id:bigint,last_name:text,phone:text'

  if [ "${ACTUAL}" == "${EXPECTED}" ]; then
    echo "OK: Found expected columns"
  else
    echo "ERROR: Did not find expected columns"
    return 1
  fi

  SQL=$(./pgauditor --table pgauditor.person 2>/dev/null)
  if [ $? -ne 0 ]; then
    return 1
  fi

  psql -c "${SQL}"
  if [ $? -ne 0 ]; then
    return 1
  fi

  # Query the columns of the table from the information schema
  ACTUAL=$(psql -tc "select string_agg(g.column_name || ':' || g.data_type, ',') from (select column_name, data_type from information_schema.columns where table_name = 'aud_person' and table_schema = 'pgauditor' order by column_name) g" | sed 's/^\s*//g')
  EXPECTED='audit_id:bigint,changed_at:timestamp with time zone,changed_by:text,new_email:text,new_first_name:text,new_id:bigint,new_last_name:text,new_phone:text,old_email:text,old_first_name:text,old_id:bigint,old_last_name:text,old_phone:text,operation:USER-DEFINED'

  if [ "${ACTUAL}" == "${EXPECTED}" ]; then
    echo "OK: Found expected columns"
  else
    echo -e "ERROR: Did not find expected columns: \n${EXPECTED}\n${ACTUAL}\n"
    return 1
  fi

  drop_database "${PGDATABASE}"

  if [ ${SUCCESS} -ne 0 ]; then
    return 1
  fi
}

function test_audit_update() {
  local schema_name="$1"

  set -eu

  create_database "${PGDATABASE}"
  create_schema "pgauditor"
  create_person_table "pgauditor"

  # Verify that the table was created
  psql -c "select * from pgauditor.person" 1> /dev/null

  set +eu
}

run test_create_audit_table "pgauditor"
run test_create_audit_table "public"

