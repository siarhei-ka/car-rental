#!/bin/sh
# Creates the database and the role of the ancillary service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$ANCILLARY_DB_PASSWORD" <<'SQL'
create role ancillary login password :'password';
create database ancillary owner ancillary;
SQL
