-- Example daily rates for Germany, in EUR. Loaded only when pricing.seed-data is true.

insert into daily_rate (location_id, car_type, car_class, amount, currency, updated_at) values
    ('ber-airport', 'SEDAN', 'ECONOMY', 39.00, 'EUR', now()),
    ('ber-airport', 'SUV',   'ECONOMY', 59.00, 'EUR', now()),
    ('ber-airport', 'VAN',   'ECONOMY', 69.00, 'EUR', now()),
    ('muc-center',  'SEDAN', 'ECONOMY', 45.00, 'EUR', now()),
    ('muc-center',  'SUV',   'PREMIUM', 99.00, 'EUR', now());
