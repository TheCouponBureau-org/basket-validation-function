#!/usr/bin/env bash

set -euo pipefail

mvn -q clean package -DskipTests

JAR_PATH="target/basket-validator-1.0-SNAPSHOT.jar"
#FAT_JAR_PATH="target/basket-validator-1.0-SNAPSHOT-all.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "Jar file was not created: $JAR_PATH" >&2
  exit 1
fi

#if [ ! -f "$FAT_JAR_PATH" ]; then
#  echo "Fat jar file was not created: $FAT_JAR_PATH" >&2
#  exit 1
#fi

echo "Standard jar created at: $JAR_PATH"
#echo "Fat jar created at: $FAT_JAR_PATH"
#echo "Recommended run command:"
#echo "java -jar $FAT_JAR_PATH '<input-json-string>' [tcb-base-url tcb-access-key tcb-secret-key]"
