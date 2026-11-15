-- The discount rules of one country. Flyway applies this file to every country schema.

-- Rules are never deleted, only switched off. A rule has at least one condition and applies only
-- when all of them hold. Its effect is a percentage or a fixed amount off the extras or the total.
create table discount_rule (
    id              uuid           primary key,
    name            varchar(100)   not null,
    min_extras      integer        check (min_extras between 1 and 100),
    -- Codes of extras that must all be in the basket, separated by commas; empty for none.
    required_extras varchar(400)   not null default '',
    min_days        integer        check (min_days between 1 and 365),
    effect          varchar(16)    not null check (effect in ('PERCENT_OFF', 'AMOUNT_OFF')),
    percent         numeric(5, 2)  check (percent > 0 and percent <= 100),
    amount          numeric(14, 3) check (amount > 0),
    currency        char(3),
    target          varchar(16)    not null check (target in ('EXTRAS', 'TOTAL')),
    valid_from      date           not null,
    valid_to        date           check (valid_to >= valid_from),
    active          boolean        not null,
    updated_at      timestamptz    not null,
    check (min_extras is not null or min_days is not null or required_extras <> ''),
    check ((effect = 'PERCENT_OFF' and percent is not null and amount is null and currency is null)
        or (effect = 'AMOUNT_OFF' and percent is null and amount is not null and currency is not null))
);
