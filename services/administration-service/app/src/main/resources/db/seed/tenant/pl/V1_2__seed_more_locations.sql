-- The major cities and the airports with regular flights in Poland. They match the
-- example stock of the inventory service and the example rates of the pricing service.

insert into location (id, name, city, latitude, longitude, time_zone, active) values
    ('wmi-airport', 'Warsaw Modlin Airport', 'Warsaw', 52.4511, 20.6518, 'Europe/Warsaw', true),
    ('ktw-airport', 'Katowice Airport', 'Katowice', 50.4743, 19.0800, 'Europe/Warsaw', true),
    ('gdn-airport', 'Gdansk Airport', 'Gdansk', 54.3776, 18.4662, 'Europe/Warsaw', true),
    ('wro-airport', 'Wroclaw Airport', 'Wroclaw', 51.1027, 16.8858, 'Europe/Warsaw', true),
    ('poz-airport', 'Poznan Airport', 'Poznan', 52.4210, 16.8263, 'Europe/Warsaw', true),
    ('rze-airport', 'Rzeszow Airport', 'Rzeszow', 50.1100, 22.0190, 'Europe/Warsaw', true),
    ('szz-airport', 'Szczecin Airport', 'Szczecin', 53.5847, 14.9022, 'Europe/Warsaw', true),
    ('luz-airport', 'Lublin Airport', 'Lublin', 51.2403, 22.7136, 'Europe/Warsaw', true),
    ('bzg-airport', 'Bydgoszcz Airport', 'Bydgoszcz', 53.0968, 17.9777, 'Europe/Warsaw', true),
    ('lcj-airport', 'Lodz Airport', 'Lodz', 51.7219, 19.3981, 'Europe/Warsaw', true),
    ('szy-airport', 'Olsztyn-Mazury Airport', 'Olsztyn', 53.4819, 20.9377, 'Europe/Warsaw', true),
    ('ieg-airport', 'Zielona Gora Airport', 'Zielona Gora', 52.1385, 15.7986, 'Europe/Warsaw', true),
    ('krk-center', 'Krakow Centre', 'Krakow', 50.0614, 19.9366, 'Europe/Warsaw', true),
    ('lcj-center', 'Lodz Centre', 'Lodz', 51.7592, 19.4560, 'Europe/Warsaw', true),
    ('wro-center', 'Wroclaw Centre', 'Wroclaw', 51.1079, 17.0385, 'Europe/Warsaw', true),
    ('poz-center', 'Poznan Centre', 'Poznan', 52.4064, 16.9252, 'Europe/Warsaw', true),
    ('gdn-center', 'Gdansk Centre', 'Gdansk', 54.3520, 18.6466, 'Europe/Warsaw', true),
    ('szz-center', 'Szczecin Centre', 'Szczecin', 53.4285, 14.5528, 'Europe/Warsaw', true),
    ('bzg-center', 'Bydgoszcz Centre', 'Bydgoszcz', 53.1235, 18.0084, 'Europe/Warsaw', true),
    ('luz-center', 'Lublin Centre', 'Lublin', 51.2465, 22.5684, 'Europe/Warsaw', true),
    ('bia-center', 'Bialystok Centre', 'Bialystok', 53.1325, 23.1688, 'Europe/Warsaw', true),
    ('ktw-center', 'Katowice Centre', 'Katowice', 50.2649, 19.0238, 'Europe/Warsaw', true);
