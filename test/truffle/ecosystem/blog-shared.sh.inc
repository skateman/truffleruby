#!/usr/bin/env bash

truffle=$(dirname "$ecosystem")
source "$truffle/common.sh.inc"

alias truffleruby="jt ruby -S"

gem_test_pack_path="$1"
echo "gem_test_pack_path: $gem_test_pack_path" # TODO

if [ "$gem_test_pack_path" ]; then
  truffleruby bundle config --local cache_path "$gem_test_pack_path/gem-cache"
else
  truffleruby bundle config --delete cache_path
fi

truffleruby bundle config --local without postgresql mysql

if [ "$gem_test_pack_path" ]; then
  truffleruby bundle install --local --no-cache
else
  truffleruby bundle install
fi

truffleruby bin/rails db:setup
truffleruby bin/rails log:clear tmp:clear

truffleruby bin/rails test

if [ -f tmp/pids/server.pid ]; then
  kill "$(cat tmp/pids/server.pid)" || true
  rm tmp/pids/server.pid
fi

port=57085
truffleruby bundle exec bin/rails server --port="$port" &

function kill_server() {
  kill %1
  wait %1 || true
}

set +x
url="http://localhost:$port/posts.json"
while ! curl -s "$url"; do
  echo -n .
  sleep 1
done
set -x

test "$(curl -s "$url")" = '[]'

kill_server

# put back the original bin/rake, as it gets overwritten by bundle install
jt ruby "$ecosystem/restore-rake.rb" "$repo"
