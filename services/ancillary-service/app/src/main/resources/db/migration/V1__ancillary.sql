-- The extras and the one-way fee of one country. Flyway applies this file to every country schema.

-- Extras are never deleted, only switched off, because reservations may refer to them.
create table extra (
    code         varchar(32)    primary key,
    name         varchar(100)   not null,
    kind         varchar(16)    not null check (kind in ('EQUIPMENT', 'SERVICE')),
    charge       varchar(16)    not null check (charge in ('PER_DAY', 'PER_RENTAL')),
    amount       numeric(14, 3) not null check (amount >= 0),
    currency     char(3)        not null,
    max_quantity integer        not null check (max_quantity between 1 and 10),
    active       boolean        not null,
    updated_at   timestamptz    not null
);

-- At most one row: the fixed fee for returning the car to another location of the country.
create table one_way_fee (
    id         boolean        primary key default true check (id),
    amount     numeric(14, 3) not null check (amount >= 0),
    currency   char(3)        not null,
    updated_at timestamptz    not null
);
