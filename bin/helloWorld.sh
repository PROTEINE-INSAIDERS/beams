#!/usr/bin/env bash
sbt "helloWorld/compile"
sbt "helloWorld/run -k Master" & \
sbt "helloWorld/run -k Alice" & \
sbt "helloWorld/run -k
Bob"