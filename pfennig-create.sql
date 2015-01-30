create table invoices (
  id                        integer not null,
  price                     bigint,
  satoshi_value             bigint,
  currency                  varchar(255),
  notification_url          varchar(255),
  order_id                  varchar(255),
  description               varchar(255),
  identifier                varchar(255),
  paid_at                   timestamp,
  label                     varchar(255),
  address_hash              varchar(255),
  created_at                timestamp not null,
  constraint pk_invoices primary key (id))
;

create table payments (
  id                        integer not null,
  received_satoshi          bigint,
  transaction_hash          varchar(255),
  appeared_at_chain_height  integer,
  address_hash              varchar(255),
  paid_at                   timestamp,
  confirmed_at              timestamp,
  created_at                timestamp not null,
  constraint pk_payments primary key (id))
;

create table watching_addresses (
  id                        integer not null,
  notification_url          varchar(255),
  identifier                varchar(255),
  address_hash              varchar(255),
  received_satoshi          bigint,
  label                     varchar(255),
  created_at                timestamp,
  constraint pk_watching_addresses primary key (id))
;

create sequence invoices_seq;

create sequence payments_seq;

create sequence watching_addresses_seq;



