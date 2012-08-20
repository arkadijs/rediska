# Rediska

A simple full-text search engine using Redis.

## Setup

### Start Redis instances

Edit `start-redises.sh` to set path to `redis-server`, number of servers, and per-server memory limit.

`./start-redises.sh` on a separate console.

### Build

1. If you want to run tests, edit `src/main/java/*Test.groovy`. Set `ra.RATest.docPath` and `ra.WebTest.docPath` to README
and JDK API folders respectively.
2. Customize HTTP server port in `ra.Main` and `ra.WebTest`, defaults to 8080.
3. `ra.RA.nOfRedises` must match the number set in `start-redises.sh`
4. Customize `ra.RA.tokenize()` regexp to suit your need.

`mvn package`

### Start server

`java -Xmx1024m -jar target/rediska-1.0-jar-with-dependencies.jar`

## API
```
POST   /reset
PUT    /content/<content-id>
GET    /content?q=query+terms
DELETE /content?q=query+terms
DELETE /content?id=content-id1|content-id2|...
```
