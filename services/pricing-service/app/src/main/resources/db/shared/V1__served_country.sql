-- The countries this service serves, with the currency of their prices. Each country also has its
-- own schema tenant_<code>.
create table served_country (
    code     char(2) primary key,
    active   boolean not null,
    currency char(3) not null
);
