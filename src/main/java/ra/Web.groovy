package ra

import javax.servlet.http.*

class Web extends HttpServlet {
    def help = '''
Usage: POST   /reset
       PUT    /content/<content-id>
       GET    /content?q=query+terms
       DELETE /content?q=query+terms
       DELETE /content?id=content-id1|content-id2|...
'''
    def limit = 10000
    def nOfRedises = 2
    def cache = Collections.synchronizedMap(new WeakHashMap<String, List<String>>())

    def ra = new ThreadLocal<RA>() {
        @Override RA initialValue() { new RA(nOfRedises) }
        @Override void remove() { get().disconnect(); super.remove() }
    }

    def usage(HttpServletResponse resp) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, help)
    }

    @Override
    void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if (req.servletPath == '/reset') {
            cache.clear()
            ra.get().reset()
            resp.status = HttpServletResponse.SC_OK
        } else
            usage(resp)

    }

    @Override
    void doPut(HttpServletRequest req, HttpServletResponse resp) {
        if (req.characterEncoding == null)
            req.characterEncoding = 'UTF-8'

        def pi = req.pathInfo
        if (pi == null || pi.isEmpty() || pi.length() < 2) {
            usage(resp)
            return
        }
        def contentId = pi.substring(1)

        req.reader.withReader {
            def buf = java.nio.CharBuffer.allocate(limit)
            it.read(buf)
            if (buf.length() > 0 && !buf.isAllWhitespace()) {
                ra.get()[contentId] = buf.rewind().toString()
                resp.setStatus(HttpServletResponse.SC_CREATED)
            } else
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT)
        }
    }

    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
        def q = req.getParameter('q')
        if (q == null) {
            usage(resp)
            return
        }
        def ids = ra.get()[q]
        cache.put(q, ids)
        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"
        new groovy.json.StreamingJsonBuilder(resp.writer)(ids)
    }

    @Override
    void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        def ids
        def q = req.getParameter('q')
        def p = req.getParameter('id')
        if (q != null) {
            ids = cache.remove(q) ?: ra.get()[q]
        } else if (p != null) {
            ids = p.split('\\|') as List
        } else {
            usage(resp)
            return
        }
        ra.get().remove(ids)
    }
}
