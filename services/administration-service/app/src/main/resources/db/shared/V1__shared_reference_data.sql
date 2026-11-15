-- Reference data shared by all countries, and the outbox of events.

create table country (
    code     varchar(2)   primary key,
    name     varchar(100) not null,
    currency varchar(3)   not null,
    active   boolean      not null
);

create table car_type (
    code   varchar(32)  primary key,
    name   varchar(100) not null,
    active boolean      not null
);

create table car_class (
    code   varchar(32)  primary key,
    name   varchar(100) not null,
    active boolean      not null
);

-- Events are written here in the same transaction as the change they describe, and sent to
-- Kafka afterwards, so that an event is never lost and never describes a change that did not
-- happen.
create table outbox (
    id           bigserial    primary key,
    topic        varchar(100) not null,
    event_key    varchar(200) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null,
    published_at timestamptz
);

create index outbox_unpublished_idx on outbox (id) where published_at is null;
