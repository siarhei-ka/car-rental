-- Example locations in Poland. They match the example stock of the inventory service.

insert into location (id, name, city, latitude, longitude, time_zone, active) values
    ('waw-airport', 'Warsaw Chopin Airport', 'Warsaw', 52.1657, 20.9671, 'Europe/Warsaw', true),
    ('waw-center', 'Warsaw Centre', 'Warsaw', 52.2297, 21.0122, 'Europe/Warsaw', true),
    ('krk-airport', 'Krakow Airport', 'Krakow', 50.0777, 19.7848, 'Europe/Warsaw', true);
