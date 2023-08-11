#!/usr/bin/env bash

set -e

HOST=${HOST:-'http://s3:9001'}
COOKIE='/tmp/curl_s3_cookie'
DEFAULT_BUCKET=${DEFAULT_BUCKET:-''}
ACCESS_KEY_ID=${MINIO_ROOT_USER:-''}
SECRET_ACCESS_KEY=${MINIO_ROOT_PASSWORD:-''}

# Login
curl -s ${HOST}'/api/v1/login' \
  -c ${COOKIE} \
  -H 'Content-Type: application/json' \
  --data-raw '{"accessKey":"'${ACCESS_KEY_ID}'","secretKey":"'${SECRET_ACCESS_KEY}'"}' \
  --compressed \
  --insecure

# Create bucket
curl -s ${HOST}'/api/v1/buckets' \
  -b ${COOKIE} \
  -H 'Content-Type: application/json' \
  --data-raw '{"name":"'${DEFAULT_BUCKET}'","versioning":false,"locking":false}' \
  --compressed \
  --insecure