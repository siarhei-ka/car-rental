#!/bin/sh
# Creates the database and the role of the customer service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$CUSTOMER_DB_PASSWORD" <<'SQL'
create role customer login password :'password';
create database customer owner customer;
SQL
