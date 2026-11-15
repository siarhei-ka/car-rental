-- Example locations in Germany. They match the example stock of the inventory service.

insert into location (id, name, city, latitude, longitude, time_zone, active) values
    ('ber-airport', 'Berlin Brandenburg Airport', 'Berlin', 52.3667, 13.5033, 'Europe/Berlin', true),
    ('muc-center', 'Munich Centre', 'Munich', 48.1351, 11.5820, 'Europe/Berlin', true);
