#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 1 ] && [ "$#" -ne 4 ]; then
  echo "Usage: ./run-validation.sh <input-json> [<tcb-base-url> <tcb-access-key> <tcb-secret-key>]" >&2
  exit 1
fi

mvn -q -DskipTests compile

if [ "$#" -eq 1 ]; then
  mvn -q \
    -Dexec.mainClass=org.thecouponbureau.validate.basket.cli.BasketValidationCli \
    -Dexec.args="$1" \
    org.codehaus.mojo:exec-maven-plugin:3.5.0:java
else
  mvn -q \
    -Dexec.mainClass=org.thecouponbureau.validate.basket.cli.BasketValidationCli \
    -Dexec.args="$1 $2 $3 $4" \
    org.codehaus.mojo:exec-maven-plugin:3.5.0:java
fi
