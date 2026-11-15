#!/bin/sh
# Creates the database and the role of the administration service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$ADMINISTRATION_DB_PASSWORD" <<'SQL'
create role administration login password :'password';
create database administration owner administration;
SQL
