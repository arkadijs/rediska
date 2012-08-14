package ra

//@Grab('redis.clients:jedis:2.1.0')

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
    def redis = new redis.clients.jedis.Jedis("localhost")
    //def redis = new DummyRedis()

    /** Resets redis storage. */
    def reset() {
        def keys = redis.keys('*') as List
        if (!keys.isEmpty())
            redis.del(*keys)
    }

    List<String> tokenize(String s) {
        def uniq = []
        def prev = ''
        // Groovy unique() is O(N^2)
        for (token in (s.split('\\s+') as List).grep { it.length() > 2 }.sort()) {
            if (token != prev) {
                uniq.add(token)
                prev = token
            }
        }
        uniq
    }

    def _token = "t:"
    def _content = "c:"

    /** Puts content into redis index, ra[id] = content. */
    def putAt(String id, String content) {
        def tokens = tokenize(content)
        def contentKey = _content + id
        def pipe = redis.pipelined()
        tokens.each { pipe.sadd(_token + it, id) }
        tokens.each { pipe.sadd(contentKey, it) }
        pipe.sync()
    }

    /** Searches for tokens in redis index, content-id-s = ra[sentence]. */
    Set<String> getAt(String what) {
        redis.sinter(*tokenize(what).collect { _token + it })
    }

    /** Removes content from redis index, ra.remove([content0, content1]) */
    def remove(List<String> ids) {
        def pipe = redis.pipelined()
        ids.each { id ->
            def contentKey = _content + id
            redis.smembers(contentKey).each {
                pipe.srem(_token + it, id)
            }
            pipe.del(contentKey)
            pipe.sync()
        }
    }
}
