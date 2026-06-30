#!/usr/bin/env bash

set -euo pipefail

mvn -q clean package -DskipTests

JAR_PATH="target/basket-validator-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "Jar file was not created: $JAR_PATH" >&2
  exit 1
fi

echo "Jar created at: $JAR_PATH"
