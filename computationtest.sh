#!/bin/bash

if [ ! -f computationTest.jar ]; then
  echo computationTest.jar does not exist! test aborted.
  exit 1
fi

java \
 -jar computationTest.jar \
 9 \
 15
