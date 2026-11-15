#!/bin/sh
# Creates the database and the role of the discount service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$DISCOUNT_DB_PASSWORD" <<'SQL'
create role discount login password :'password';
create database discount owner discount;
SQL
