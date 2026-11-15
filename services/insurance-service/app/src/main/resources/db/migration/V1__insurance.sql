-- The countries this service serves, with the currency of their insurance prices.
create table served_country (
    code     char(2) primary key,
    active   boolean not null,
    currency char(3) not null
);

-- The insurance options, the same in every country. Their prices come from the provider.
create table insurance_option (
    code        varchar(32)  primary key,
    name        varchar(100) not null,
    description varchar(500) not null,
    preselected boolean      not null,
    sort_order  integer      not null
);

-- Exactly one option is chosen until the customer picks another one.
create unique index insurance_option_one_preselected on insurance_option (preselected)
    where preselected;

insert into insurance_option (code, name, description, preselected, sort_order) values
    ('BASIC', 'Basic cover',
     'Third-party liability and damage to the car above an excess', true, 1),
    ('FULL', 'Full cover',
     'Third-party liability, damage and theft of the car, without excess', false, 2);
