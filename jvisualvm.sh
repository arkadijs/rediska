#!/bin/sh
exec jvisualvm --cp:a $(dirname "$0")/lib/jmxremote_optional.jar
