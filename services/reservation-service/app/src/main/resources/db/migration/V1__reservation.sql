-- Reservations of one country. Flyway applies this file to every country schema.

create table reservation (
    id                  uuid        primary key,
    pick_up_location_id varchar(64) not null,
    return_location_id  varchar(64) not null,
    car_type            varchar(32) not null,
    car_class           varchar(32) not null,
    start_at            timestamptz not null,
    days                integer     not null check (days >= 1),
    state               varchar(16) not null
        check (state in ('CONFIRMED', 'CANCELLED', 'PICKED_UP', 'RETURNED', 'NO_SHOW')),
    -- What the inventory still has to be told, and since when.
    pending_action      varchar(16)
        check (pending_action in ('CONFIRM', 'RELEASE', 'RETURN')),
    pending_since       timestamptz,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    check ((pending_action is null) = (pending_since is null))
);

create index reservation_pending_idx on reservation (pending_since) where pending_action is not null;
create index reservation_pick_up_idx on reservation (pick_up_location_id, start_at);

-- One row per idempotency key, in the same transaction as the reservation (NFR-3).
create table idempotency_key (
    key            varchar(128) primary key,
    request_hash   char(64)     not null,
    reservation_id uuid         not null references reservation (id) on delete cascade,
    created_at     timestamptz  not null
);
