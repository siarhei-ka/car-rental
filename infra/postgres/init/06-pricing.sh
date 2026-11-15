#!/bin/sh
# Creates the database and the role of the pricing service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$PRICING_DB_PASSWORD" <<'SQL'
create role pricing login password :'password';
create database pricing owner pricing;
SQL
