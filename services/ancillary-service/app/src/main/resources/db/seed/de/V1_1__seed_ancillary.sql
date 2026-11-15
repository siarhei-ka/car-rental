-- Example extras and one-way fee for Germany, in EUR. Loaded only when ancillary.seed-data is true.

insert into extra (code, name, kind, charge, amount, currency, max_quantity, active, updated_at) values
    ('CHILD_SEAT',   'Child seat',           'EQUIPMENT', 'PER_DAY', 8.00, 'EUR', 3, true, now()),
    ('BOOSTER_SEAT', 'Booster seat',         'EQUIPMENT', 'PER_DAY', 5.00, 'EUR', 3, true, now()),
    ('GPS',          'Satellite navigation', 'EQUIPMENT', 'PER_DAY', 6.00, 'EUR', 1, true, now());

insert into one_way_fee (amount, currency, updated_at) values (50.00, 'EUR', now());
