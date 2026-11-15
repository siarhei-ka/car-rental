-- Example stock for Poland. Loaded only when inventory.seed-data is true.

insert into stock (location_id, car_type, car_class, cars) values
    ('waw-airport', 'SEDAN', 'ECONOMY', 5),
    ('waw-airport', 'SEDAN', 'PREMIUM', 2),
    ('waw-airport', 'SUV',   'ECONOMY', 3),
    ('waw-airport', 'VAN',   'ECONOMY', 2),
    ('waw-center',  'SEDAN', 'ECONOMY', 3),
    ('waw-center',  'SUV',   'ECONOMY', 1),
    ('krk-airport', 'SEDAN', 'ECONOMY', 4),
    ('krk-airport', 'VAN',   'ECONOMY', 1);
