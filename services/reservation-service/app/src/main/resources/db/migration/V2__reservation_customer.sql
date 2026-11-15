-- Every reservation belongs to the customer who made it: the user ID in Keycloak.
-- Reservations made before customer sign-in existed belong to no one and cannot be read by a
-- customer.
alter table reservation add column customer_id varchar(255) not null default '';
alter table reservation alter column customer_id drop default;
create index reservation_customer_idx on reservation (customer_id);

-- An idempotency key is unique per customer, so two customers never share a reservation.
alter table idempotency_key add column customer_id varchar(255) not null default '';
alter table idempotency_key alter column customer_id drop default;
alter table idempotency_key drop constraint idempotency_key_pkey;
alter table idempotency_key add primary key (customer_id, key);
