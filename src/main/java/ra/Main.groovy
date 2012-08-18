package ra

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import org.eclipse.jetty.server.nio.SelectChannelConnector
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

        def context = new ServletContextHandler(jetty, '/');
        jetty.setHandler(context);

        def servlet = new ServletHolder(new Web())
        context.addServlet(servlet, '/content/*');
        context.addServlet(servlet, '/reset');

        jetty.start()
        jetty
    }
}
