-- The daily rates of one country. Flyway applies this file to every country schema.
-- One current rate per location, car type and car class, in the currency of the country.
create table daily_rate (
    location_id varchar(64)    not null,
    car_type    varchar(32)    not null,
    car_class   varchar(32)    not null,
    amount      numeric(14, 3) not null check (amount > 0),
    currency    char(3)        not null,
    updated_at  timestamptz    not null,
    primary key (location_id, car_type, car_class)
);
