#!/bin/sh
mvn install:install-file -Dfile=lib/jmxremote_optional.jar -DgroupId=javax.management -DartifactId=jmx_remote -Dversion=1.0.1_03 -Dpackaging=jar
