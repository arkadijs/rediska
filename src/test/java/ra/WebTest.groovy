package ra

import static groovyx.gpars.GParsPool.withPool
import wslite.rest.RESTClient
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)
class WebTest extends GroovyTestCase {
    def docPath = '/usr/lib/jvm/java-7-openjdk-i386/docs/api'
    def baseUri = 'http://localhost:8080'
    static server
    RESTClient rest = new RESTClient("$baseUri/content")

    @org.junit.BeforeClass
    static void start() {
        server = Main.start([] as String[])
    }

    @org.junit.AfterClass
    static void stop() {
        server.stop()
    }

    def id = 'ABC'
    def id2 = 'DEF'
    def aboutLife = 'Life is wonderful and then I\'m free'
    def what = 'Life is wonderful'

    @org.junit.Before
    void testRestReset() {
        RATest.perf('redis-es cleared in') {
            assert 200 == new RESTClient("$baseUri/reset").post() {} .statusCode
        }
    }

    @Test
    void testRestPut() {
        assert 201 == rest.put(path: id) {
            text aboutLife
        } .statusCode
    }

    @Test
    void testRestAssignedId() {
        def r = rest.put() {
            text aboutLife
        }
        assert 201 == r.statusCode
        assert 'text/plain' == r.contentType
        assert !r.contentAsString.isEmpty()
    }

    @Test
    void testRestGet() {
        testRestPut()
        def resp = rest.get(query: [q: what])
        assert 200 == resp.statusCode
        assert 0 < Long.parseLong(resp.headers['X-RA-Elapsed'])
        assert [id] == resp.json
    }

    @Test
    void testRestReturnContent() {
        testRestPut()
        def resp = rest.get(query: [q: what, rc: true])
        assert 200 == resp.statusCode
        assert [[id: id, text: 'free life wonderful']] == resp.json
    }

    @Test
    void testRestDeleteByQuery() {
        testRestPut()
        def q = [ query: [q: what] ]
        assert [id] == rest.get(q).json
        assert 200 == rest.delete(q).statusCode
        assert [] == rest.get(q).json
    }

    @Test
    void testRestDeleteById() {
        assert 201 == rest.put(path: id)  { text aboutLife }.statusCode
        assert 201 == rest.put(path: id2) { text aboutLife }.statusCode
        def q = [query: [q: what]]
        def ids = rest.get(q).json
        assert [id, id2].sort() == ids.sort()
        // ids is JSONArray which join() method quotes strings, so make it Java array first
        assert 200 == rest.delete(query: [id: ids.toArray().join('|')]).statusCode
        assert [] == rest.get(q).json
    }

    @Test
    void testRestMT() {
        def n = 16
        def nOfThreads = 16+1
        def data = sampleData()
        data = data.collate(data.size() / n as int)
        RATest.perf('multi-threaded put web test finished in') {
            withPool(nOfThreads) {
                def test = { dataChunk, i ->
                    RATest.perf("put task $i finished in") {
                        def rest = new RESTClient("$baseUri/content/")
                        dataChunk.each { content ->
                            assert 201 == rest.put(path: content[0]) { text content[1] } .statusCode
                        }
                    }
                }
                (0..n).collect { test.callAsync(data[it], it) } .each { it.get() }
            }
        }
        def s = 100
        RATest.perf('multi-threaded search web test finished in') {
            withPool(nOfThreads) {
                def test = { i ->
                    RATest.perf("search task $i finished in") {
                        def rest = new RESTClient("$baseUri/content")
                        s.times {
                            def r = rest.get(query: [q: 'Runtime Locale'])
                            assert 200 == r.statusCode
                            assert 0 < r.json.size()
                        }
                    }
                }
                (0..n).collect { test.callAsync(it) } .each { it.get() }
            }
        }
    }

    @Test
    void testRediskaJThroughput() {
        def n = 16
        def nOfThreads = 2+1
        def data = sampleData()
        data = data.collate(data.size() / n as int)
        def u = new URL(baseUri)
        def rj = new Client(u.host, u.port, 100, 5000)
        def deq = new java.util.concurrent.LinkedBlockingDeque<java.util.concurrent.Future<Client.Result>>()
        RATest.perf('Rediska-J put web test finished in') {
            withPool(nOfThreads) {
                def enqueue = { dataChunk, i ->
                    RATest.perf("Rediska-J async put task $i finished in") {
                        dataChunk.each { content ->
                            deq.add(rj.put(*content))
                        }
                    }
                }
                def dequeue = {
                    RATest.perf('Rediska-J future.get() task finished in') {
                        def (errors, total, apiTime) = [0, 0, 0l]
                        while (true) {
                            def f = deq.pollFirst(3, java.util.concurrent.TimeUnit.SECONDS)
                            if (f == null)
                                break
                            def r = f.get()
                            if (!r.success) {
                                ++errors
                                println "error: ${r.code}"
                            }
                            if (r.elapsedNanos > 0)
                                apiTime += r.elapsedNanos/1000 as long
                            ++total
                        }
                        println "async futures processed: $total total; $errors errors; api elapsed time ${Math.round(apiTime/1000000)}s"
                    }
                }
                ([dequeue.callAsync()] + (0..n).collect { enqueue.callAsync(data[it], it) }).each { it.get() }
            }
        }
    }

    @Test
    void testRediskaJPut2Throughput() {
        def n = 16
        def nOfThreads = 2
        def data = sampleData()
        def nMessages = data.size()
        data = data.collate(data.size() / n as int)
        def u = new URL(baseUri)
        def rj = new Client(u.host, u.port, 10, 5000)
        def total = new AtomicInteger(0)
        def errors = new AtomicInteger(0)
        def apiTime = new AtomicLong(0)
        def invoker = Thread.currentThread()
        def listener = new com.biasedbit.http.future.HttpRequestFutureListener<Client.Result>() {
            @Override
            void operationComplete(com.biasedbit.http.future.HttpRequestFuture<Client.Result> future) {
                if (201 == future.responseStatusCode) {
                    def r = future.processedResult
                    assert null != r
                    if (!r.success) {
                        errors.getAndIncrement()
                        println "error: ${future.responseStatusCode} ${future.response.status.reasonPhrase}"
                    }
                    if (r.elapsedNanos > 0)
                        apiTime.getAndAdd(r.elapsedNanos/1000 as long)
                } else {
                    errors.getAndIncrement()
                    println "error: ${future.responseStatusCode} ${future.response.status.reasonPhrase}"
                }
                def totalProcessed = total.getAndIncrement()
                if (totalProcessed == nMessages - 100)
                    invoker.interrupt()
            }
        };
        RATest.perf('Rediska-J put2 web test finished in') {
            withPool(nOfThreads) {
                def enqueue = { dataChunk, i ->
                    RATest.perf("Rediska-J async put2 task $i finished in") {
                        dataChunk.each { content ->
                            while (true) {
                                try {
                                    rj.put2(*content, listener)
                                    break
                                } catch (com.biasedbit.http.CannotExecuteRequestException ex) {
                                    if (ex.message == 'Request queue is full') {
                                        println 'http request queue full, sleeping'
                                        Thread.sleep(1000)
                                    } else
                                        throw ex
                                }
                            }
                        }
                    }
                }
                (0..n).collect { enqueue.callAsync(data[it], it) } .each { it.get() }
            }
            def interrupted = false
            try { Thread.sleep(200_000) } catch (InterruptedException ex) { interrupted = true }
            Thread.sleep(1000)
            println "async callbacks processed: ${total.get()} total; ${errors.get()} errors; api elapsed time ${Math.round(apiTime.get()/1000000)}s"
            assert true == interrupted
        }
    }

    def sampleData() {
        def docDir = new File(docPath)
        def htmls = []

        docDir.eachFileRecurse(groovy.io.FileType.FILES) {
            if (it.name.endsWith(".html"))
                htmls << it
        }
        htmls = htmls[0..1000]
        def l = 5000
        def data = []
        htmls.each { file ->
            def id = file.canonicalPath //.hashCode()
            def str = new String(file.readBytes(), "UTF-8")
            if (str.length() > l) {
                l.step(str.length(), l) { i ->
                    data << ["$id-$i", str.substring(i-l, i) ]
                }
            } else
                data << [id.toString(), str]
        }

        assert data.size() > 1000
        println "total number of chunks: ${data.size()}; chars: " + data.sum { it[1].length() }
        data
    }
}
