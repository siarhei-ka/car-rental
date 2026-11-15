-- One driver profile per customer. The customer ID is the user ID in Keycloak.
-- Name and email stay in Keycloak and are read from the access token.
create table driver_profile (
  customer_id     varchar(255) primary key,
  date_of_birth   date         not null,
  licence_country char(2)      not null,
  created_at      timestamptz  not null,
  updated_at      timestamptz  not null
);
