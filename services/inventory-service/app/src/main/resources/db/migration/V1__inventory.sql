-- Inventory of one country. Flyway applies this file to every country schema.

create table stock (
    location_id varchar(64) not null,
    car_type    varchar(32) not null,
    car_class   varchar(32) not null,
    cars        integer     not null check (cars >= 0),
    primary key (location_id, car_type, car_class)
);

create table hold (
    id                 varchar(64) primary key,
    location_id        varchar(64) not null,
    car_type           varchar(32) not null,
    car_class          varchar(32) not null,
    start_at           timestamptz not null,
    days               integer     not null check (days >= 1),
    return_location_id varchar(64) not null,
    one_way            boolean     not null,
    status             varchar(16) not null check (status in ('PROVISIONAL', 'CONFIRMED')),
    created_at         timestamptz not null,
    foreign key (location_id, car_type, car_class) references stock
);

create index hold_stock_idx on hold (location_id, car_type, car_class);
create index hold_provisional_idx on hold (created_at) where status = 'PROVISIONAL';

create table transfer (
    id                 varchar(64) primary key,
    location_id        varchar(64) not null,
    car_type           varchar(32) not null,
    car_class          varchar(32) not null,
    cars               integer     not null check (cars >= 1),
    departure_at       timestamptz not null,
    target_location_id varchar(64) not null,
    planned_arrival_at timestamptz not null check (planned_arrival_at > departure_at),
    foreign key (location_id, car_type, car_class) references stock
);

create index transfer_stock_idx on transfer (location_id, car_type, car_class);
