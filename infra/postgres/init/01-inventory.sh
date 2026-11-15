#!/bin/sh
# Creates the database and the role of the inventory service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$INVENTORY_DB_PASSWORD" <<'SQL'
create role inventory login password :'password';
create database inventory owner inventory;
SQL
