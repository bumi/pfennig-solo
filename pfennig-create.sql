create table invoices (
  id                        integer not null,
  price                     bigint,
  satoshi                   bigint,
  received_satoshi          bigint,
  currency                  varchar(255),
  notification_url          varchar(255),
  order_id                  varchar(255),
  description               varchar(255),
  identifier                varchar(255),
  paid_at                   timestamp,
  transaction_hash          varchar(255),
  label                     varchar(255),
  confidence                integer,
  chain_height              integer,
  address_hash              varchar(255),
  created_at                timestamp not null,
  constraint pk_invoices primary key (id))
;

create table watching_addresses (
  id                        integer not null,
  notification_url          varchar(255),
  identifier                varchar(255),
  address_hash              varchar(255),
  received_satoshi          bigint,
  transaction_hash          varchar(255),
  label                     varchar(255),
  confidence                integer,
  chain_height              integer,
  created_at                timestamp,
  paid_at                   timestamp,
  constraint pk_watching_addresses primary key (id))
;

create sequence invoices_seq;

create sequence watching_addresses_seq;



