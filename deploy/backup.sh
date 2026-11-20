#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd $SCRIPT_DIR

export $(grep -v '^#' .env | tr -d '\r' | xargs)

TS=$(date +"%Y%m%d_%H%M%S")

cd backend-go
tar -zcvf "./attachment_${TS}.tar.gz" ./attachment
docker exec eac-postgres pg_dump -U ${DB_USERNAME} -d ${DB_NAME} >"./pg_dump_${TS}.sql"
