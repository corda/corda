# IRS Demo

This demo brings up three nodes: Bank A, Bank B and a node that simultaneously runs a notary, a network map and an
interest rates oracle. The two banks agree on an interest rate swap, and then do regular fixings of the deal as the
time on a simulated clock passes.

Functionality is split into two parts - CordApp which provides actual distributed ledger backend and Spring Boot
webapp which provides REST API and web frontend. Application communicate using Corda RPC protocol.

To run from the command line in Unix:
1. Run ``./gradlew samples:irs-demo:cordapp:deployNodes`` to install configs and a command line tool under
   ``samples/irs-demo/cordapp/build``
2. Run ``./gradlew samples:irs-demo:web:deployWebapps`` to install configs and tools for running webservers
3. Move to the ``samples/irs-demo/`` directory
4. Run ``./cordapp/build/nodes/runnodes`` to open up three new terminals with the three nodes (you may have to install xterm)
5. On Linux, run ``./web/build/webapps/runwebapps`` to open three more terminals for associated webservers. On macOS,
   use the following command instead: ``osascript ./web/build/webapps/runwebapps.scpt``

To run from the command line in Windows:

1. Run ``gradlew.bat samples:irs-demo:cordapp:deployNodes`` to install configs and a command line tool under
   ``samples\irs-demo\build``
2. Run ``gradlew.bat samples:irs-demo:web:deployWebapps`` to install configs and tools for running webservers
3. Run ``cd samples\irs-demo`` to change current working directory
4. Run ``cordapp\build\nodes\runnodes.bat`` to open up several 3 terminals for each nodes
5. Run ``web\build\webapps\runwebapps.bat`` to open up several 3 terminals for each nodes' webservers

This demo also has a web app. To use this, run nodes and then navigate to http://localhost:10007/ and
http://localhost:10010/ to see each node's view of the ledger.

To use the web app, click the "Create Deal" button, fill in the form, then click the "Submit" button. You can then use
the time controls at the top left of the home page to run the fixings. Click any individual trade in the blotter to
view it.

*Note:* The IRS web UI currently has a bug when changing the clock time where it may show no numbers or apply fixings
inconsistently. The issues will be addressed in a future release. Meanwhile, you can take a look at a simpler oracle
example here: https://github.com/corda/oracle-example.

## Running the system test

The system test utilize docker. Amount of RAM required to run the IRS system test is around 2.5GB, it is important
to allocated appropriate system resources (On MacOS/Windows this may require explicit changes to docker configuration)

### Gradle

The system test is designed to exercise the entire stack, including Corda nodes and the web frontend. It uses [Docker](https://www.docker.com), [docker-compose](https://docs.docker.com/compose/), and
[PhantomJS](http://phantomjs.org/). Docker and docker-compose need to be installed and configured to be inside the system path
(default installation). PhantomJs binary have to be put in a known location and have execution permission enabled
(``chmod a+x phantomjs`` on Unix) and the full path to the binary exposed as system property named ``phantomjs.binary.path`` or
a system variable named ``PHANTOMJS_BINARY_PATH``.
Having this done, the system test can be run by running the Gradle task ``:samples:irs-demo:systemTest``.

### Other

In order to run the the test by other means that the Gradle task - two more system properties are expected -
``CORDAPP_DOCKER_COMPOSE`` and ``WEB_DOCKER_COMPOSE`` which should specify full path docker-compose file for IRS cordapp
 and web frontend respectively. Those can be obtained by running ``:samples:irs-demo:cordapp:prepareDockerNodes`` and
``web:generateDockerCompose`` Gradle tasks. ``systemTest`` task simply executes those two and set proper system properties up.

