-- The insurance option, the extras and the price a reservation was made with. The price is
-- calculated by the pricing service and stored, so later price changes do not alter it.
-- Reservations made before prices were stored have basic cover, no extras and no price.
alter table reservation add column insurance varchar(32) not null default 'BASIC';
alter table reservation alter column insurance drop default;
-- The extras as CODE:quantity, separated by commas and ordered by code; empty for none.
alter table reservation add column extras varchar(400) not null default '';
-- Every line of the price, the discount and the total, as the pricing service sent them.
alter table reservation add column price jsonb;
alter table reservation add column total numeric(14, 3);
alter table reservation add column currency char(3);
alter table reservation add constraint reservation_price_check
    check ((price is null) = (total is null) and (price is null) = (currency is null));
