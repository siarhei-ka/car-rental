#!/bin/sh
# Creates the database and the role of the insurance service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$INSURANCE_DB_PASSWORD" <<'SQL'
create role insurance login password :'password';
create database insurance owner insurance;
SQL
