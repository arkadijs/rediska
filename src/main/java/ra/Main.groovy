package ra

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.QueuedThreadPool

class Main {
    static void main(String[] args) {
        start(args).join()
    }

    static Server start(String[] args) {
        def jetty = new Server()

        def connector = new SelectChannelConnector();
        connector.port = 8080
        connector.requestHeaderSize = 65536 // allow large DELETE requests by content id
        connector.threadPool = new QueuedThreadPool(16)
        connector.maxIdleTime = 10_000
        jetty.setConnectors(connector)

        def handlers = new HandlerList()

        def index = Main.class.getResource('/www/index.html')
        if (index != null) {
            def uri = index.toURI().toString()
            def res = new ResourceHandler()
          //res.resourceBase = '/Users/arkadi/Work/Rediska/src/main/resources/www'
            res.resourceBase = uri.substring(0, uri.indexOf('/index.html'))
            res.welcomeFiles = ['index.html']
            handlers.addHandler(res)
        }

        def context = new ServletContextHandler(jetty, '/');
        def servlet = new ServletHolder(new Web())
        context.addServlet(servlet, '/content/*');
        context.addServlet(servlet, '/reset');
        handlers.addHandler(context)

        jetty.setHandler(handlers);
        jetty.start()
        jetty
    }
}
