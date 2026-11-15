#!/bin/sh
# Creates the database and the role of the reservation service on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$RESERVATION_DB_PASSWORD" <<'SQL'
create role reservation login password :'password';
create database reservation owner reservation;
SQL
