package ra

import redis.clients.jedis.Jedis
import static groovyx.gpars.GParsPool.withPool

class DummyRedisPipe {
    def sadd(x, y) {}
    def srem(x, y) {}
    def del(x) {}
    def sync() {}
}

class DummyRedis {
    def keys(x) { [] }
    def sinter(x) { [] }
    def smembers(x) { [] }
    def del(x) {}
    def pipelined() { new DummyRedisPipe() }
}

class RA {
    def host = "localhost"
    def basePort = 6379
    def nOfRedises = 2 // try to set to 3
    def nOfThreads = 4
    def stopwords = Stopwords.instance.words
    List<Jedis> redises = []

    RA() {
        init(nOfRedises)
    }

    RA(n) {
        init(n)
    }

    private def init(int n) {
        n.times {
            redises << new Jedis(host, basePort + it)
        }
    }

    static long previousPrint = 0
    def rateLimit(String msg) {
        long now = System.nanoTime()
        if (now - previousPrint > 1_000_000_000) {
            println msg
            previousPrint = now
        }
    }
    def rateLimit(Exception e) { rateLimit(e.toString()) }

    // TODO faster recovery in case 1. redis is down, 2. redis is stuck/slow
    def allRedises(Closure closure) {
        withPool(nOfThreads) {
            redises.collect { closure.callAsync(it) } .collect { future ->
                def result = []
                try {
                    result = future.get()
                } catch (Exception e) {
                    rateLimit e
                    // TODO? redis.disconnect()
                }
                result
            } .flatten()
        }
    }

    def oneRedis(int preferred, Closure closure) {
        def n = redises.size()
        def ordered = n > 1 ? {
                def rest = (preferred+1..preferred+n-1).collect { it % n }
                Collections.shuffle(rest)
                redises[preferred, rest]
            }.call() : redises
        ordered.any { redis ->
            def done = false
            try {
                closure(redis)
                done = true
            } catch (Exception e) {
                rateLimit e
                redis.disconnect()
            }
            done
        }
    }

    def disconnect() {
        allRedises { redis -> redis.disconnect() }
    }

    /** Resets redis storage. */
    def reset() {
        allRedises { redis ->
            def keys = redis.keys('*') as List
            //println 'clearing ' + keys
            if (!keys.isEmpty())
                redis.del(*keys)
        }
    }

    List<String> tokenize(String s) {
        def prev = ''
        // Groovy unique() is O(N^2)
        (s.split('[\\s,!]+') as List).grep { it.length() > 2 && !stopwords.contains(it) }.sort().inject([]) { uniq, token ->
            if (token != prev) {
                uniq << token
                prev = token
            }
            uniq
        }
    }

    def _token = "t:"
    def _content = "c:"

    /** Puts content into redis index, ra[id] = content. */
    def putAt(String id, String content) {
        def tokens = tokenize(content)
        if (!tokens)
            return
        def i = id.hashCode() % redises.size()
        def contentKey = _content + id
        oneRedis(i) { redis ->
            def pipe = redis.pipelined()
            tokens.each { pipe.sadd(_token + it, id) }
            tokens.each { pipe.sadd(contentKey, it) }
            pipe.sync()
        }
    }

    /** Searches for tokens in redis index, content-id-s = ra[sentence]. */
    List<String> getAt(String what) {
        def tokens = tokenize(what).collect { _token + it }
        allRedises { redis ->
            redis.sinter(*tokens) as List
        }
    }

    /** Removes content from redis index, ra.remove([content0, content1, ...]) */
    def remove(List<String> ids) {
        allRedises { redis ->
            ids.each { id ->
                def pipe = redis.pipelined()
                def contentKey = _content + id
                redis.smembers(contentKey).each {
                    pipe.srem(_token + it, id)
                }
                pipe.del(contentKey)
                pipe.sync()
            }
        }
    }
}

@Singleton
class Stopwords {
    def lang = ['en', 'et', 'lv', 'lt', 'ru']
    def words

    Stopwords() {
        words = lang.collect {
            getClass().getClassLoader().getResourceAsStream("stopwords/$it").withStream {
                it.readLines("UTF-8").collect {
                    it.replaceAll('\\s+', '')
                }
            } .grep { it }
        } .flatten() as Set
    }
}
