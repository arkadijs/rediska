package ra

import static groovyx.gpars.GParsPool.withPool

class RATest extends GroovyTestCase {
    def docPath = '/usr/share/doc'
    Random rand = new Random()
    RA ra

    void setUp() {
        ra = new RA()
        ra.reset()
    }

    void tearDown() {
        ra.disconnect()
        ra = null
    }

    void testStopwords() {
        def w = Stopwords.instance.words
        assert w.contains('about')
        assert w.contains('тогда')
    }

    def aboutDog = "Slow dog runs over lucky tock"
    def what = aboutDog.split(" ")[1,2].join(" ")

    void testFindInSentence() {
        ra["A"] = aboutDog
        def ids = ra[what]
        assertEquals(["A"], ids)
    }

    void testRemoveContent() {
        ra["B"] = aboutDog
        ra.remove(["B"])
        def ids = ra[what]
        assertEquals([], ids)
    }

    static perf(blurb, closure) {
        long start = System.nanoTime()
        closure()
        println(blurb + ": " + ((System.nanoTime() - start)/1000000 as int) + "ms")
    }

    void testPerformance() {
        // read README files from doc dir
        def data
        perf("files read in") {
            data = sampleData()
        }
        // add READMEs content
        def n = 10
        perf("${n}x regular content added in") {
            load(ra, data, n)
        }
        // add one README as spam
        def x = 10000
        def spam
        perf("${x}x spam content added in") {
            spam = loadSpam(ra, data, x)
        }
        // search for spam
        n = 100
        def spamIds
        perf("${n}x spam searched in") {
            spamIds = search(ra, spam, n)
        }
        assert spamIds.size() >= x && spamIds.size() < 1.1*x
        // remove spam from dictionary
        perf("spam removed from dictionary in") {
            remove(ra, spamIds)
        }
    }

    void testMT() {
        def n = 10
        def nOfThreads = 4
        def data = sampleData()
        data = data.collate(data.size() / n as int, false)
        perf("multi-threaded test finished in") {
            withPool(nOfThreads) {
                def test = { i ->
                    perf("task $i finished in") {
                        def ra = new RA()
                        load(ra, data[i], 10)
                        def spam = loadSpam(ra, data[i], 1000)
                        def spamIds = search(ra, spam, 5)
                        remove(ra, spamIds)
                    }
                }
                (0..n-1).collect { test.callAsync(it) } .each { it.get() }
            }
        }
    }

    def sampleData() {
        def docDir = new File(docPath)
        def readmes = []

        docDir.eachFileRecurse(groovy.io.FileType.FILES) {
            if (it.name.contains("README") && !it.name.endsWith(".gz"))
                readmes << it
        }
        def data = readmes.collect { file ->
            try {
                [file.canonicalPath, new String(file.readBytes(), "UTF-8")]
            } catch (FileNotFoundException) { // broken symlink
                null
            }
        }.grep { it }
        assert data.size() > 20
        println "total number of files: ${data.size()}; chars: ${data.sum { it[1].length() }}"
        data
    }

    def load(ra, data, n) {
        n.times { i ->
            data.each {
                def id = it[0]; def content = it[1]
                ra["$id:load-$i"] = content
            }
        }
    }

    def loadSpam(ra, data, n) {
        def spamAll = data.findAll { it[1].length() > 500 && it[1].length() < 3000 } ?: data
        def spam = spamAll[ rand.nextInt(spamAll.size()) ] [1]
        n.times {
            ra["random-spam-$it"] = spam
        }
        spam
    }

    def search(ra, spam, n) {
        def spamTokens = ra.tokenize(spam)[10..20].join(" ")
        def spamIds = []
        n.times {
            spamIds = ra[spamTokens]
        }
        println("found ${spamIds.size()} entries")
        spamIds
    }

    def remove(ra, spamIds) {
        ra.remove(spamIds as List)
    }
}
