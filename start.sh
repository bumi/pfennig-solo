#!/bin/bash

set -e

cd /pfennig

echo "booting up"


if [ -n "$POSTGRES_PASS" ]; then
  export DATABASE_PORT_5432_PASSWORD=$POSTGRES_PASS
  export PG_PASSWORD=$POSTGRES_PASS
fi
if [ -n "$POSTGRES_USER" ]; then
  export DATABASE_PORT_5432_USER=$POSTGRES_USER
fi

# defaulting to postgres as database user
if [ -n "$DATABASE_PORT_5432_USER" ]; then
	export DATABASE_PORT_5432_USER=postgres
fi

env

if [ -n "$DATABASE_PORT_5432_TCP_ADDR" ]; then
  echo "DATABASE_PORT_5432_TCP_ADDR is set"
  echo "assuming linking with env variables"

  echo "$DATABASE_PORT_5432_TCP_ADDR:$DATABASE_PORT_5432_TCP_PORT:pfennig:$DATABASE_PORT_5432_USER:$DATABASE_PORT_5432_PASSWORD" > "$HOME/.pgpass"
  chmod 0600 "$HOME/.pgpass"

  cat "$HOME/.pgpass"

  PSQL="psql -h $DATABASE_PORT_5432_TCP_ADDR -p $DATABASE_PORT_5432_TCP_PORT -U $DATABASE_PORT_5432_USER"

  echo $PSQL

  export DATABASE_URL="postgresql://$DATABASE_PORT_5432_USER:$DATABASE_PORT_5432_PASSWORD@$DATABASE_PORT_5432_TCP_ADDR:$DATABASE_PORT_5432_TCP_PORT/pfennig"

else
  echo "seems no DATABASE_PORT_5432_TCP_ADDR env var is set."
  echo "assuming we are running locally using fig"
  echo "linking is done with host entries connecting to 'db' host"

  PSQL="psql -h db -U postgres"
  export DATABASE_URL="postgresql://postgres:@db:5432/pfennig"
fi

echo "preparing DB"

if [ ! $($PSQL -c '\l'| grep pfennig) ]; then
  echo "creating database"
  $PSQL -c "create database pfennig;"
fi

#TODO: some shema migration
# if no invoices table is there we run the create script
if [ ! $($PSQL -c '\dt' pfennig | grep invoices) ]; then
  echo "importing schema"
  $PSQL -f pfennig-create.sql pfennig
fi

echo $DATABASE_URL
echo "running pfennig"
java -DapplicationId=$APPLICATION_ID -jar target/pfennig-jar-with-dependencies.jar
