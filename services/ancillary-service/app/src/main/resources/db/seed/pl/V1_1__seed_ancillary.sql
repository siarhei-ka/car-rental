-- Example extras and one-way fee for Poland, in PLN. Loaded only when ancillary.seed-data is true.

insert into extra (code, name, kind, charge, amount, currency, max_quantity, active, updated_at) values
    ('CHILD_SEAT',   'Child seat',           'EQUIPMENT', 'PER_DAY',    25.00, 'PLN', 3, true, now()),
    ('BOOSTER_SEAT', 'Booster seat',         'EQUIPMENT', 'PER_DAY',    15.00, 'PLN', 3, true, now()),
    ('GPS',          'Satellite navigation', 'EQUIPMENT', 'PER_DAY',    20.00, 'PLN', 1, true, now()),
    ('ROAD_TOLL',    'Road toll payment',    'SERVICE',   'PER_RENTAL', 49.00, 'PLN', 1, true, now());

insert into one_way_fee (amount, currency, updated_at) values (150.00, 'PLN', now());
