## JMeter for controlling CORDA performance runs
This module contains gradle tasks to make running the JMeter (http://jmeter.apache.org)
load generation tool against Corda nodes much easier and more useful.  It does this by
providing a simple way to launch JMeter with the actual JMeter install coming
from downloaded dependencies, and by providing some Samplers that interact with
the Corda node via RPC.

### Running via the interactive GUI

To run up the JMeter UI, using the jmeter.properties in the resources folder,
type the following:

`./gradlew tools:jmeter:run`

You can then open the example script in "Example Flow Properties.jmx" via the File -> Open menu option.  You need to 
configure the host, ports, user name and password in the Java Sampler that correspond to your chosen target Corda node.
Simply running from the UI will result in the RPC client running inside the UI JVM.

If you wish to pass additional arguments to JMeter, you can do this:

`./gradlew tools:jmeter:run -PjmeterArgs="['-n', '-Ljmeter.engine=DEBUG']"`

The intention is to run against a remote Corda node or nodes, hosted on servers rather than desktops.  To
this end, we leverage the JMeter ability to run remote agents that actually execute the tests, with these 
reporting results back to the UI (or headless process if you so desire - e.g. for automated benchmarks).  This is
supplemented with some additional convenience of automatically creating ssh tunnels to the remote nodes
(we don't want the JMeter ports open to the internet) in coordination with the jmeter.properties.
The remote agents then run close to the nodes, so the latency of RPC calls is minimised.

A Capsule (http://www.capsule.io) based launchable JAR is created that can be run with the simple command line

`java -jar jmeter-corda-<version>.jar`

Embedded in the JAR is all of the corda code for flows and RPC, as well as the jmeter.propeties.  This
JAR will also include a properties file based on the hostname in the JMeter configuration,
so we allocate different SSH tunneled port numbers this way.

#### SSH Tunnels

To launch JMeter with the tunnels automatically created:

`./gradlew tools:jmeter:run -PjmeterHosts="['hostname1', 'hostname2']"`

The list of hostnames should be of at least length one, with a maximum equal to the length of the remote_hosts
option in jmeter.properties.  We effectively "zip" together the hostnames and that list to build the SSH tunnels.
The remote_hosts property helps define the ports (the hosts should always be local) used
for each host listed in jmeterHosts. Some additional ports are also opened based on some other
parts of the configuration to access the RMI registry and to allow return traffic
from remote agents.

The SSH tunnels can be started independently with:

`./gradlew tools:jmeter:runSsh -PjmeterHosts="['hostname1', 'hostname2']"`

For the ssh tunneling to work, an ssh agent must be running on your local machine with the 
appropriate private key loaded. If the environment variable `SSH_AUTH_SOCK` is set, the code 
assumes that a posix sshagent process is being used, if it is not set, it assumes that 
[Pageant](https://www.ssh.com/ssh/putty/putty-manuals/0.68/Chapter9.html) is in use. If the 
remote user name is different from the current user name, `-XsshUser <remote user name>` 
can be used to set this, or in the gradle call:

`./gradlew tools:jmeter:runSsh -PjmeterHosts="['hostname1', 'hostname2']" -PsshUser="'username'"`

#### Running locally with driver

To run up 3 nodes (2 nodes, 1 non-validating notary) locally for testing anything in the `perftestcordapp` (e.g. samplers,
custom flows), you can use gradle to run:

`./gradlew tools:jmeter:runDriver`

This uses the driver test infrastructure to fire up the nodes. See `StartLocalPerfCorDapp` for X500 names of nodes, 
RPC user logins etc.  The RPC port of Bank A is typically 10004, but they are all reporting in the console output.  A
sample JMeter config for this setup has been included as `LocalIssueAndPay Request.jmx` under resources.

### Running in non-interactive test/batch mode

To run Jmeter in performance test mode, we want to run a predefined test without starting the UI and record the results 
in a csv file. In order to do this, additional arguments need to be passed to JMeter. Using gradle, the command line
would look something like this:

```./gradlew tools:jmeter:run -PjmeterArgs="['-n', '-t', 'build/resources/main/Testplans/CashIssuance_40k.jmx', '-l', 'CashIssuance_40k.jtl', '-R', '127.0.0.1:20100']" -PjmeterHosts="['perf-node-4.corda.r3cev.com']"```

The interesting bit here are the `jmeterArgs`:
- `-n` tells JMeter to run non-interactively
- `-t <filename>` loads the testplan to run
- `-l <filename>` specifies the output to write to (if it exists, it will be appended)
- `-R <hostname:port>` specifies the host to run against - note this is localhost in this case as we are using ssh 
tunnels to reach the test nodes. 

### Generating an HTML report from a recorded CSV file

It's possible to generate a simple but effective HTML report for a JMeter test run that already produced a CSV file.

```./gradlew tools:jmeter:run -PjmeterArgs="['-g', '<input-file>.csv', '-o', '<path-of-report-dir>', '-Jjmeter.reportgenerator.report_title=<report title>']"```

The report output directory must be empty or not exist (in which case JMeter attempts to create it).