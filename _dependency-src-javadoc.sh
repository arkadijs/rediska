#!/bin/sh
mvn dependency:sources && mvn dependency:resolve -Dclassifier=javadoc
