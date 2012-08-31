package ra

import javax.servlet.http.*

class Web extends HttpServlet {
    def help = '''
Usage: POST   /reset
       PUT    /content[/<content-id>] (201 Created, replies assigned/provided content-id)
       GET    /content?q=query+terms[&rc=1] (set rc= to return tokenized content)
       DELETE /content?q=query+terms
       DELETE /content?id=content-id1|content-id2|...
'''
    def limit = 10000

    def ra = new ThreadLocal<RA>() {
        @Override RA initialValue() { new RA() }
        @Override void remove() { get().disconnect(); super.remove() }
    }

    def usage(HttpServletResponse resp) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, help)
    }

    def perf(HttpServletResponse resp, closure) {
        long start = System.nanoTime()
        def result = closure()
        long elapsed = System.nanoTime() - start
        resp.setHeader('X-RA-Elapsed', (elapsed > 0 ? elapsed : 0).toString())
        result
    }

    @Override
    void init() {
        Cleaner.init()
    }

    @Override
    void doPost(HttpServletRequest req, HttpServletResponse resp) {
        if (req.servletPath == '/reset') {
            perf(resp) {
                ra.get().reset()
            }
            resp.status = HttpServletResponse.SC_OK
        } else
            usage(resp)

    }

    @Override
    void doPut(HttpServletRequest req, HttpServletResponse resp) {
        if (req.characterEncoding == null)
            req.characterEncoding = 'UTF-8'

        String contentId
        def pi = req.pathInfo
        if (pi == null || pi.isEmpty() || pi.length() < 2)
            contentId = UUID.randomUUID()
        else if (pi == '/null') {
            usage(resp)
            return
        } else
            contentId = pi.substring(1)

        req.reader.withReader {
            def buf = java.nio.CharBuffer.allocate(limit)
            def cnt = it.read(buf)
            if (cnt < 1) {
                resp.status = HttpServletResponse.SC_NO_CONTENT
                return
            }
            buf.limit(cnt)
            buf.rewind()
            if (buf.length() > 0 && !buf.isAllWhitespace()) {
                perf(resp) {
                    ra.get()[contentId] = buf.toString()
                }
                resp.status = HttpServletResponse.SC_CREATED
                resp.contentType = 'text/plain'
                resp.characterEncoding = 'UTF-8'
                resp.writer.withWriter {
                    it.write(contentId)
                }
            } else
                resp.status = HttpServletResponse.SC_NO_CONTENT
        }
    }

    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) {
        def q = req.getParameter('q')
        if (q == null) {
            usage(resp)
            return
        }
        def ids = perf(resp) {
            ra.get().search(q, req.getParameter('rc') as boolean)
        }
        resp.contentType = 'application/json'
        resp.characterEncoding = 'UTF-8'
        new groovy.json.StreamingJsonBuilder(resp.writer)(ids)
    }

    @Override
    void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        def ids
        def q = req.getParameter('q')
        def p = req.getParameter('id')
        perf(resp) {
            if (q != null) {
                ids = ra.get()[q]
            } else if (p != null) {
                ids = p.split('\\|') as List
            } else {
                usage(resp)
                return
            }
            ra.get().remove(ids)
        }
    }
}
