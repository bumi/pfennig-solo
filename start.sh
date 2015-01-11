#!/bin/bash

set -e

cd /pfennig

PGPASSWORD=$DATABASE_PORT_5432_PASSWORD
PSQL="psql -h $DATABASE_PORT_5432_TCP_ADDR -p $DATABASE_PORT_5432_TCP_PORT -U $DATABASE_PORT_5432_USER"

if [ ! $($PSQL -c '\l'| grep ^pfennig$) ]; then
  $PSQL -c "create database pfennig;"
fi

#TODO: some shema migration
if [ ! $($PSQL -c '\dt' pfennig | grep invoices) ]; then
  $PSQL -f pfennig-create.sql pfennig
fi

DATABASE_URL="postgresql://$DATABASE_PORT_5432_USER:$DATABASE_PORT_5432_PASSWORD@$DATABASE_PORT_5432_TCP_ADDR:$DATABASE_PORT_5432_TCP_PORT/pfennig"
java -jar target/pfennig-jar-with-dependencies.jar
