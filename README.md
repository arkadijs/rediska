# Rediska

A simple full-text search engine using Redis.

## Setup

### Start Redis instances

Edit `start-redises.sh` to set path to `redis-server`, number of servers, and per-server memory limit.

`./start-redises.sh` on a separate console.

### Build

1. If you want to run tests: (a) Edit `src/main/java/*Test.groovy`. Set `ra.RATest.docPath` and `ra.WebTest.docPath` to README
and JDK API folders respectively. (b) Install [Rediska-J][2] into local Maven cache.
2. Customize HTTP server port in `ra.Main` and `ra.WebTest`, defaults to 8080.
3. `ra.RA.nOfRedises` must match the number set in `start-redises.sh`
4. Customize `ra.RA.tokenize()` regexp to suit your need.
5. Install JAR not available from Maven repos `./_dependency-jmx.sh`

`mvn package`

### Start server

`java -Xmx512m -jar target/rediska-1.0-jar-with-dependencies.jar`

## API
```
POST   /reset
PUT    /content[/<content-id>] (201 Created, replies assigned/provided content-id)
GET    /content?q=query+terms[&rc=1] (set rc= to return tokenized content)
DELETE /content?q=query+terms
DELETE /content?id=content-id1|content-id2|...
```

### Web UI
Open [http://server:8080/][1] in browser.

### JMX monitoring
Start VisualVM `jvisualvm --cp:a lib/jmxremote_optional.jar` or just `./jvisualvm.sh`. Add host and new JMX connection

`service:jmx:jmxmp://server.host.com:7090`

[1]: http://localhost:8080
[2]: https://bitbucket.org/arkadi/rediska-j
