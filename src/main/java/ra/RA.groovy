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

    private init(int n) {
        n.times {
            redises << new Jedis(host, basePort + it, 20_000) // 20 sec
        }
    }

    private static long previousInvocation = 0
    private rateLimit(Closure closure) {
        long now = System.nanoTime()
        def diff = now - previousInvocation
        def near = diff < 1_000_000 // within millisecond
        def far = diff > 1_000_000_000 // one second ago
        if (near || far) {
            closure()
            if (far)
                previousInvocation = now
        }
    }
    private ratedPrintln(String msg) { rateLimit { println msg } }
    private ratedPrintln(Exception e) { rateLimit { e.printStackTrace() } }

    private memoryLimit(Exception e) {
        (e instanceof redis.clients.jedis.exceptions.JedisDataException &&
                e.message.contains('maxmemory')) || memoryLimit(e.getCause())
    }

    private allRedises(Closure closure) {
        withPool(nOfThreads) {
            redises.collect { redis -> [redis, closure.callAsync(redis)] } .collect {
                def (redis, future) = it
                try {
                    future.get()
                } catch (Exception e) {
                    ratedPrintln e
                    redis.disconnect()
                    if (memoryLimit(e))
                        deleteAllKeys(redis)
                    []
                }
            } .flatten()
        }
    }

    private oneRedis(int preferred, Closure closure) {
        def n = redises.size()
        def ordered = n > 1 ? {
                def rest = (preferred+1..preferred+n-1).collect { it % n }
                Collections.shuffle(rest)
                redises[preferred, rest]
            }.call() : redises
        Exception ex
        def success = ordered.any { redis ->
            try {
                closure(redis)
                true
            } catch (Exception e) {
                ratedPrintln e
                redis.disconnect()
                if (memoryLimit(e))
                    deleteAllKeys(redis)
                ex = e
                false
            }
        }
        if (!success)
            throw ex
    }

    def disconnect() {
        allRedises { redis -> redis.quit() }
    }

    private deleteAllKeys(Jedis redis) {
        def keys = redis.keys('*') as List
        if (!keys.isEmpty())
            redis.del(*keys)
    }

    /** Resets redis storage. */
    def reset() {
        allRedises { deleteAllKeys(it) }
    }

    List<String> tokenize(String s) {
        def prev = ''
        // Groovy unique() is O(N^2)
        (s.split('[\\s,!"<>/]+') as List).grep { it.length() > 2 && !stopwords.contains(it.toLowerCase()) }
                .sort().inject([]) { uniq, token ->
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
            pipe.sync() // syncAndReturnAll() should be used to detect OOM
        }
    }

    /** Searches for tokens in redis index, content-id-s = ra[sentence]. */
    List<String> getAt(String what) {
        def tokens = tokenize(what).collect { _token + it }
        if (!tokens)
            []
        else
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
    def words = lang.collect {
            getClass().getClassLoader().getResourceAsStream("stopwords/$it").withStream {
                it.readLines("UTF-8").collect {
                    it.replaceAll('\\s+', '').toLowerCase()
                }
            } .grep { it }
        } .flatten() as Set
}
