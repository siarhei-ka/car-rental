-- Example discount rules for Germany. Loaded only when discount.seed-data is true.

insert into discount_rule (id, name, min_extras, min_days, effect, percent, target, valid_from,
                           active, updated_at) values
    (gen_random_uuid(), '3 or more extras: 10% off the extras', 3, null, 'PERCENT_OFF', 10, 'EXTRAS',
     date '2026-01-01', true, now()),
    (gen_random_uuid(), 'A week or longer: 5% off', null, 7, 'PERCENT_OFF', 5, 'TOTAL',
     date '2026-01-01', true, now());
