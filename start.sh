#!/bin/bash

set -e

cd /pfennig

echo "booting up"

echo "$DATABASE_PORT_5432_TCP_ADDR:$DATABASE_PORT_5432_TCP_PORT:pfennig:$DATABASE_PORT_5432_USER:$DATABASE_PORT_5432_PASSWORD" > "$HOME/.pgpass"
chmod 0600 "$HOME/.pgpass"

PSQL="psql -h $DATABASE_PORT_5432_TCP_ADDR -p $DATABASE_PORT_5432_TCP_PORT -U $DATABASE_PORT_5432_USER"

echo "preparing DB"
cat "$HOME/.pgpass"

if [ ! $($PSQL -c '\l'| grep pfennig) ]; then
  echo "creating database"
  $PSQL -c "create database pfennig;"
fi

#TODO: some shema migration
if [ ! $($PSQL -c '\dt' pfennig | grep invoices) ]; then
  echo "importing schema"
  $PSQL -f pfennig-create.sql pfennig
fi

export DATABASE_URL="postgresql://$DATABASE_PORT_5432_USER:$DATABASE_PORT_5432_PASSWORD@$DATABASE_PORT_5432_TCP_ADDR:$DATABASE_PORT_5432_TCP_PORT/pfennig"
echo $DATABASE_URL
echo "running pfennig"
java -jar target/pfennig-jar-with-dependencies.jar
