#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/.."

./mvnw -pl jieqi-gui -am install -DskipTests
./mvnw -f jieqi-gui/pom.xml javafx:run
