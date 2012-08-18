package ra

import static groovyx.gpars.GParsPool.withPool
import wslite.rest.RESTClient
import org.junit.Test

@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)
class WebTest extends GroovyTestCase {
    static server
    RESTClient rest = new RESTClient("http://localhost:8080/content/")

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
    def aboutLife = "Life is wonderful and then I die"
    def what = "Life is wonderful"

    @org.junit.Before
    void testRestReset() {
        RATest.perf("redis-es cleared in") {
            assert 200 == new RESTClient("http://localhost:8080/reset").post() {} .statusCode
        }
    }

    void testRestPut() {
        assert 201 == rest.put(path: id) {
            text aboutLife
        } .statusCode
    }

    @Test
    void testRestGet() {
        testRestPut()
        def resp = rest.get(query: [q: what])
        assert 200 == resp.statusCode
        assert [id] == resp.json
        resp
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
        assert [id, id2] == ids
        // ids is JSONArray which join() method quotes strings, so make it Java array first
        assert 200 == rest.delete(query: [id: ids.toArray().join('|')]).statusCode
        assert [] == rest.get(q).json
    }

    @Test
    void testRestMT() {
        testRestReset()
        def n = 8
        def nOfThreads = 3
        def data = sampleData()
        data = data.collate(data.size() / n as int)
        RATest.perf("multi-threaded web test finished in") {
            withPool(nOfThreads) {
                def test = { i ->
                    RATest.perf("task $i finished in") {
                        def rest = new RESTClient("http://localhost:8080/content/")
                        data[i].eachWithIndex { content, id ->
                            assert 201 == rest.put(path: "$i-$id") { text content } .statusCode
                        }
                    }
                }
                (0..n).collect { test.callAsync(it) } .each { it.get() }
            }
        }
    }

    def sampleData() {
        def docDir = new File("/usr/lib/jvm/java-7-openjdk-i386/docs/api")
        def htmls = []

        docDir.eachFileRecurse(groovy.io.FileType.FILES) {
            if (it.name.endsWith(".html"))
                htmls << it
        }
        htmls = htmls[0..3000]
        def l = 5000
        def data = htmls.collect {
            def str = new String(it.readBytes(), "UTF-8")
            if (str.length() > l) {
                def splits = []
                l.step(str.length(), l) {
                    splits << str.substring(it-l, it)
                }
                splits
            } else
              str
        } .flatten()

        assert data.size() > 1000
        println "total number of chunks: ${data.size()}; chars: " + data.sum { it.length() }
        data
    }
}
