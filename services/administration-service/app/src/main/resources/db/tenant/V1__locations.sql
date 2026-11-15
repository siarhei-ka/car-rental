-- Locations of one country. Flyway applies this file to every country schema.

create table location (
    id        varchar(64)      primary key,
    name      varchar(100)     not null,
    city      varchar(100)     not null,
    latitude  double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    time_zone varchar(64)      not null,
    active    boolean          not null
);
