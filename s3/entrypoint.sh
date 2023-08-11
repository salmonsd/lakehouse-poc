#!/usr/bin/env bash

set -e

/usr/bin/docker-entrypoint.sh "$@" &
pid=${!}

shutdown() {
    kill -SIGTERM ${pid}
}

trap shutdown SIGTERM SIGINT SIGQUIT

while ! timeout 1 bash -c "echo > /dev/tcp/localhost/9001" > /dev/null 2>&1; do
  sleep 1
done

create-default-bucket.sh

wait ${pid}