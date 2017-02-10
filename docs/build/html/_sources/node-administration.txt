Node administration
===================

When a node is running, it exposes an RPC interface that lets you monitor it,
you can upload and download attachments, access a REST API and so on. A bundled
Jetty web server exposes the same interface over HTTP.

Logging
-------

In the default configuration logs are stored to the logs subdirectory of the node directory and are rotated from time to time. You can
have logging printed to the console as well by passing the ``--log-to-console`` command line flag. Corda
uses the SL4J logging façade which is configured with the log4j2 binding framework to manage its logging,
so you can also configure it in more detail by writing a custom log4j2 logging configuration file and passing ``-Dlog4j.configurationFile=my-config-file.xml``
on the command line as well. The default configuration is copied during the build from ``config/dev/log4j2.xml``, or for the test sourceSet from ``config/test/log4j2.xml``.

In corda code a logger is typically instantiated via the ``net.corda.core.utilities.loggerFor`` utility method which will create an SL4J ``Logger`` with a name based on the type parameter.
Also, available in ``net.corda.core.utilities``, are extension methods to take a lazily evaluated logging lambda for trace and debug level, which will not evaluate the lambda if the LogLevel threshold is higher.

Database access
---------------

The node exposes its internal database over a socket which can be browsed using any tool that can use JDBC drivers.
The JDBC URL is printed during node startup to the log and will typically look like this:

     ``jdbc:h2:tcp://192.168.0.31:31339/node``

The username and password can be altered in the :doc:`corda-configuration-file` but default to username "sa" and a blank
password.

Any database browsing tool that supports JDBC can be used, but if you have IntelliJ Ultimate edition then there is
a tool integrated with your IDE. Just open the database window and add an H2 data source with the above details.
You will now be able to browse the tables and row data within them.

Monitoring your node
--------------------

Like most Java servers, the node exports various useful metrics and management operations via the industry-standard
`JMX infrastructure <https://en.wikipedia.org/wiki/Java_Management_Extensions>`_. JMX is a standard API
for registering so-called *MBeans* ... objects whose properties and methods are intended for server management. It does
not require any particular network protocol for export. So this data can be exported from the node in various ways:
some monitoring systems provide a "Java Agent", which is essentially a JVM plugin that finds all the MBeans and sends
them out to a statistics collector over the network. For those systems, follow the instructions provided by the vendor.

Sometimes though, you just want raw access to the data and operations itself. So nodes export them over HTTP on the
``/monitoring/json`` HTTP endpoint, using a program called `Jolokia <https://jolokia.org/>`_. Jolokia defines the JSON
and REST formats for accessing MBeans, and provides client libraries to work with that protocol as well.

Here are a few ways to build dashboards and extract monitoring data for a node:

* `JMX2Graphite <https://github.com/logzio/jmx2graphite>`_ is a tool that can be pointed to /monitoring/json and will
  scrape the statistics found there, then insert them into the Graphite monitoring tool on a regular basis. It runs
  in Docker and can be started with a single command.
* `JMXTrans <https://github.com/jmxtrans/jmxtrans>`_ is another tool for Graphite, this time, it's got its own agent
  (JVM plugin) which reads a custom config file and exports only the named data. It's more configurable than
  JMX2Graphite and doesn't require a separate process, as the JVM will write directly to Graphite.
* *Java Mission Control* is a desktop app that can connect to a target JVM that has the right command line flags set
  (or always, if running locally). You can explore what data is available, create graphs of those metrics, and invoke
  management operations like forcing a garbage collection.
* *VisualVM* is another desktop app that can do fine grained JVM monitoring and sampling. Very useful during development.
* Cloud metrics services like New Relic also understand JMX, typically, by providing their own agent that uploads the
  data to their service on a regular schedule.

Memory usage and tuning
-----------------------

All garbage collected programs can run faster if you give them more memory, as they need to collect less
frequently. As a default JVM will happily consume all the memory on your system if you let it, Corda is
configured with a relatively small 200mb Java heap by default. When other overheads are added, this yields
a total memory usage of about 500mb for a node (the overheads come from things like compiled code, metadata,
off-heap buffers, thread stacks, etc).

If you want to make your node go faster and profiling suggests excessive GC overhead is the cause, or if your
node is running out of memory, you can give it more by running the node like this:

``java -Xmx1024m -jar corda.jar``

The example command above would give a 1 gigabyte Java heap.

.. note:: Unfortunately the JVM does not let you limit the total memory usage of Java program, just the heap size.

Uploading and downloading attachments
-------------------------------------

Attachments are files that add context to and influence the behaviour of transactions. They are always identified by
hash and they are public, in that they propagate through the network to wherever they are needed.

All attachments are zip files. Thus to upload a file to the ledger you must first wrap it into a zip (or jar) file. Then
you can upload it by running this command from a UNIX terminal:

.. sourcecode:: shell

   curl -F myfile=@path/to/my/file.zip http://localhost:31338/upload/attachment

The attachment will be identified by the SHA-256 hash of the contents, which you can get by doing:

.. sourcecode:: shell

   shasum -a 256 file.zip

on a Mac or by using ``sha256sum`` on Linux. Alternatively, the hash will be returned to you when you upload the
attachment.

An attachment may be downloaded by fetching:

.. sourcecode:: shell

   http://localhost:31338/attachments/DECD098666B9657314870E192CED0C3519C2C9D395507A238338F8D003929DE9

where DECD... is of course replaced with the hash identifier of your own attachment. Because attachments are always
containers, you can also fetch a specific file within the attachment by appending its path, like this:

.. sourcecode:: shell

   http://localhost:31338/attachments/DECD098666B9657314870E192CED0C3519C2C9D395507A238338F8D003929DE9/path/within/zip.txt

Uploading interest rate fixes
-----------------------------

If you would like to operate an interest rate fixing service (oracle), you can upload fix data by uploading data in
a simple text format to the ``/upload/interest-rates`` path on the web server.

The file looks like this::

    # Some pretend noddy rate fixes, for the interest rate oracles.

    LIBOR 2016-03-16 1M = 0.678
    LIBOR 2016-03-16 2M = 0.655
    EURIBOR 2016-03-15 1M = 0.123
    EURIBOR 2016-03-15 2M = 0.111

The columns are:

* Name of the fix
* Date of the fix
* The tenor / time to maturity in days
* The interest rate itself
