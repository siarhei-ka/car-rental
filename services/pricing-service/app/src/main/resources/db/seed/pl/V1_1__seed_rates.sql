-- Example daily rates for Poland, in PLN. Loaded only when pricing.seed-data is true.

insert into daily_rate (location_id, car_type, car_class, amount, currency, updated_at) values
    ('waw-airport', 'SEDAN', 'ECONOMY', 149.00, 'PLN', now()),
    ('waw-airport', 'SEDAN', 'PREMIUM', 299.00, 'PLN', now()),
    ('waw-airport', 'SUV',   'ECONOMY', 219.00, 'PLN', now()),
    ('waw-airport', 'VAN',   'ECONOMY', 259.00, 'PLN', now()),
    ('waw-center',  'SEDAN', 'ECONOMY', 139.00, 'PLN', now()),
    ('waw-center',  'SUV',   'ECONOMY', 209.00, 'PLN', now()),
    ('krk-airport', 'SEDAN', 'ECONOMY', 129.00, 'PLN', now()),
    ('krk-airport', 'VAN',   'ECONOMY', 239.00, 'PLN', now());
