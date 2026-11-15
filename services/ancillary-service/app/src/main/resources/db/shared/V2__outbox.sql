-- Events waiting to be sent to Kafka, written in the transaction of the change they describe.
create table outbox (
    id           bigserial    primary key,
    topic        varchar(100) not null,
    event_key    varchar(200) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null,
    published_at timestamptz
);

create index outbox_unpublished_idx on outbox (id) where published_at is null;
