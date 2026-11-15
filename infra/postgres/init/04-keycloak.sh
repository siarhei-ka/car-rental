#!/bin/sh
# Creates the database and the role of Keycloak on the first start of PostgreSQL.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
create role keycloak login password :'password';
create database keycloak owner keycloak;
SQL
