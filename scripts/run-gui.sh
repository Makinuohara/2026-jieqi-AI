#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/.."

./mvnw -pl jieqi-app -am install -DskipTests
./mvnw -f jieqi-app/pom.xml exec:java
