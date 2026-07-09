#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/.."

./mvnw -pl jieqi-server -am install -DskipTests
./mvnw -f jieqi-server/pom.xml exec:java
