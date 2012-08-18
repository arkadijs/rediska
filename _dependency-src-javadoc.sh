#!/bin/sh
mvn dependency:source && mvn dependency:resolve -Dclassifier=javadoc
