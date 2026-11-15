-- Example stock for Germany. Loaded only when inventory.seed-data is true.

insert into stock (location_id, car_type, car_class, cars) values
    ('ber-airport', 'SEDAN', 'ECONOMY', 6),
    ('ber-airport', 'SUV',   'ECONOMY', 2),
    ('ber-airport', 'VAN',   'ECONOMY', 2),
    ('muc-center',  'SEDAN', 'ECONOMY', 3),
    ('muc-center',  'SUV',   'PREMIUM', 1);
