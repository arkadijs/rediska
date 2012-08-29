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
    def host = 'localhost'
    def basePort = 6379
    def nOfRedises = 4
    def nOfThreads = 4
    static nKeepDays = 2 // today + 2 -> total 3 days
    static nDatabases = 32
    def stopwords = Stopwords.instance.words
    List<Jedis> redises = []
    // XXX synchronization
    static volatile int currentDatabase = 1
    static volatile List<Integer> activeDatabases = [1]

    RA() {
        init(nOfRedises)
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
                e.message.contains('maxmemory')) || (e && memoryLimit(e.getCause()))
    }

    private void allRedisesOneByOne(Closure closure) {
        redises.each { redis ->
            try {
                closure(redis)
            } catch (Exception e) {
                ratedPrintln e
                if (!memoryLimit(e))
                    redis.disconnect()
            }
        }
    }

    private List allRedises(Closure closure) {
        withPool(nOfThreads) {
            redises.collect { redis -> [redis, closure.callAsync(redis)] } .collect {
                def (redis, future) = it
                try {
                    future.get()
                } catch (Exception e) {
                    ratedPrintln e
                    if (!memoryLimit(e))
                        redis.disconnect()
                    []
                }
            } .flatten()
        }
    }

    private void oneRedis(int preferred, Closure closure) {
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
                if (!memoryLimit(e))
                    redis.disconnect()
                ex = e
                false
            }
        }
        if (!success)
            throw new RuntimeException("All Redis instances failed, last exception attached", ex)
    }

    def disconnect() {
        allRedises { redis -> redis.quit() }
    }

    /** Resets one day of redis storage. */
    def resetDay(int day) {
        allRedisesOneByOne { redis ->
            def r = redis.select(day)
            if (r == 'OK')
                redis.flushDB()
            else
                println "Redis SELECT failed: $r"
        }
    }

    /** Resets redis storage. */
    def reset() {
        allRedisesOneByOne { it.flushAll() }
    }

    def splitter = ~/[\s,\.\?!"':=<>\(\)\[\]\/\\]+/
    def hostname = ~/[\w-]+(\.[\w-]+)+(?:\/[\w\/\+-]+)?/
    List<String> tokenize(String s) {
        def prev = ''
        // Groovy unique() is O(N^2) :/
        splitter.split(s)
                .grep { it.length() > 2 } .collect { it.toLowerCase() } .grep { !stopwords.contains(it) }
                .sort().inject([]) { uniq, token ->
            if (token != prev) {
                uniq << token
                prev = token
            }
            uniq
        } +
        hostname.matcher(s).collect { it[0] }
    }

    def _token = 't:'
    def _content = 'c:'

    /** Puts content into redis index, ra[id] = content. */
    def putAt(String id, String content) {
        def tokens = tokenize(content)
        if (!tokens)
            return
        def i = id.hashCode() % redises.size()
        def contentKey = _content + id
        oneRedis(i) { redis ->
            redis.select(currentDatabase)
            redis.sadd(contentKey, *tokens)
            def pipe = redis.pipelined()
            tokens.each { pipe.sadd(_token + it, id) }
            pipe.sync() // syncAndReturnAll() should be used to detect OOM
        }
    }

    /** Searches for tokens in redis index, content-id-s = ra[sentence]. */
    List<String> getAt(String what) {
        search(what, false)
    }

    /** Searches for tokens in redis index, returning plain list of content-id-s
     * or list of hashes in case of returnContent == true.
     * @return  [[ id = ..., text = ... ], ...] = ra.search(sentence, true) */
    List search(String what, boolean returnContent) {
        def tokens = tokenize(what).collect { _token + it }
        if (!tokens)
            []
        else
            allRedises { redis ->
                activeDatabases.inject([]) { result, dbIndex ->
                    redis.select(dbIndex)
                    def ids = redis.sinter(*tokens) as List
                    if (!ids.isEmpty()) {
                        // flatten() is performed by allRedises()
                        if (!returnContent)
                            result << ids
                        else {
                            result << ids.collect {
                                [
                                    id: it,
                                    text: redis.smembers(_content + it).toList().sort().join(' ')
                                ]
                            }
                        }
                    }
                    result
                }
            }
    }

    /** Removes content from redis index, ra.remove([content0, content1, ...]) */
    def remove(List<String> ids) {
        allRedises { redis ->
            activeDatabases.each { dbIndex ->
                redis.select(dbIndex)
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
}

@Singleton
class Stopwords {
    def lang = ['en', 'et', 'lv', 'lt', 'ru', '_special']
    def words = lang.collect {
            this.class.classLoader.getResourceAsStream("stopwords/$it").withStream {
                it.readLines('UTF-8').collect {
                    it.replaceAll('\\s+', '').toLowerCase()
                }
            } .grep { it }
        } .flatten() as Set
}
