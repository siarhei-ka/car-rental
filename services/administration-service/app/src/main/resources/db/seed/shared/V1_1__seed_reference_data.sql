-- Example reference data. Loaded only when administration.seed-data is true.

insert into country (code, name, currency, active) values
    ('pl', 'Poland', 'PLN', true),
    ('de', 'Germany', 'EUR', true);

insert into car_type (code, name, active) values
    ('SEDAN', 'Sedan', true),
    ('SUV', 'SUV', true),
    ('VAN', 'Van', true);

insert into car_class (code, name, active) values
    ('ECONOMY', 'Economy', true),
    ('COMFORT', 'Comfort', true),
    ('PREMIUM', 'Premium', true);
