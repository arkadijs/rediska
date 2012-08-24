package ra

import static groovyx.gpars.GParsPool.withPool
import wslite.rest.RESTClient
import org.junit.Test

@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)
class WebTest extends GroovyTestCase {
    def docPath = '/usr/lib/jvm/java-7-openjdk-i386/docs/api'
  //def docPath = '/Users/arkadi/Manuals/jdk7'
    def baseUri = 'http://localhost:8080'
    static server
    RESTClient rest = new RESTClient("$baseUri/content/")

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
            assert 200 == new RESTClient("$baseUri/reset").post() {} .statusCode
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
        assert 0 < Long.parseLong(resp.headers['X-RA-Elapsed'])
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
                        def rest = new RESTClient("$baseUri/content/")
                        data[i].eachWithIndex { content, id ->
                            assert 201 == rest.put(path: content[0]) { text content[1] } .statusCode
                        }
                    }
                }
                (0..n).collect { test.callAsync(it) } .each { it.get() }
            }
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
            def id = file.canonicalPath
            def str = new String(file.readBytes(), "UTF-8")
            if (str.length() > l) {
                l.step(str.length(), l) { i ->
                    data << ["$id-$i", str.substring(i-l, i) ]
                }
            } else
                data << [id, str]
        }

        assert data.size() > 1000
        println "total number of chunks: ${data.size()}; chars: " + data.sum { it[1].length() }
        data
    }
}
