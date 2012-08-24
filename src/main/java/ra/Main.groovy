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

        def mbeans = new org.eclipse.jetty.jmx.MBeanContainer(java.lang.management.ManagementFactory.getPlatformMBeanServer());
        jetty.container.addEventListener(mbeans);
        jetty.addBean(mbeans);
        mbeans.addBean(org.eclipse.jetty.util.log.Log.getLog());
        def jmx = new org.eclipse.jetty.jmx.ConnectorServer(
            new javax.management.remote.JMXServiceURL(
                'jmxmp', null, 7090),
              //'service:jmx:jmxmp://' + (System.getProperty('java.rmi.server.hostname') ?: 'localhost') + ':7090'),
              //'rmi', null, 7090, '/jndi/rmi://' + (System.getProperty('java.rmi.server.hostname') ?: 'localhost') + ':7090/jmxrmi'),
            'org.eclipse.jetty.jmx:name=rmiconnectorserver')
        jmx.start()

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
        servlet.initOrder = 1
        context.addServlet(servlet, '/content/*');
        context.addServlet(servlet, '/reset');
        handlers.addHandler(context)

        jetty.setHandler(handlers);
        jetty.start()
        jetty
    }
}
