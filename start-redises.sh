#!/bin/bash

set -e

#redis=/Users/arkadi/Work/redis-2.4.16/src/redis-server
redis=/home/user/Work/redis-2.4.16/src/redis-server
n=2
dir=/var/tmp
base_port=6379
maxmemory=512mb # for every redis

cd $dir

trap kill_redises SIGINT

function kill_redises() {
    kill $redis_pids
    wait
}

for i in $(seq 0 $((n-1))); do
    port=$((base_port+i))
    config=redis-$port.conf
    cat > $config <<EOC
daemonize no
port $port
loglevel verbose
logfile stdout
databases 32
#save 900 1
#save 300 10
#save 60 10000
rdbcompression yes
dbfilename dump-$port.rdb
dir $dir/
maxmemory $maxmemory
maxmemory-policy noeviction
appendonly no
activerehashing yes
EOC

    $redis $dir/$config &
    redis_pids="$redis_pids $!"
done

wait
