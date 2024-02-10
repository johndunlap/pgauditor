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
  schema_name="$1"
  psql <<EOF
  create schema ${schema_name};
EOF
}

function create_person_table() {
  table_schema="$1"
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

# Begin writing unit tests
echo "Running unit tests..."

# Create the database
echo "Creating database \"${PGDATABASE}\""
createdb "${PGDATABASE}"

# Drop the database on exit
function on_exit() {
  exit_status=$?
  database_name="$1"
  echo "Dropping database \"${database_name}\""
  dropdb "${database_name}"

  if [ ${exit_status} -eq 0 ];then
    echo "SUCCESS: TESTS PASSED"
  else
    echo "ERROR: TESTS FAILED"
  fi

  exit ${exit_status}
}

# Trap the exit signal to automatically drop the test database on exit
trap 'on_exit ${PGDATABASE}' EXIT

# Stop on error
set -eu

create_schema "pgauditor"
create_person_table "pgauditor"

# Verify that the table was created
psql -c "select * from pgauditor.person" 1> /dev/null

# Query the columns of the table from the information schema
EXPECTED=$(psql -tc "select string_agg(g.column_name || ':' || g.data_type, ',') from (select column_name, data_type from information_schema.columns where table_name = 'person' order by column_name) g" | awk '{print $1}')

if [ "${EXPECTED}" == 'email:text,first_name:text,id:bigint,last_name:text,phone:text' ]; then
  echo "OK: Found expected columns"
else
  echo "ERROR: Did not find expected columns"
  exit 1
fi


./pgauditor --table pgauditor.person | psql -v ON_ERROR_STOP=1

