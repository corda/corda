Running the trading demo
========================

The repository contains a program that implements a demo of two nodes running the two-party trading protocol, which you
can learn about in :doc:`protocol-state-machines`.

The node has only currently been tested on MacOS X. If you have success on other platforms, please let us know.

To run the demo, firstly edit your /etc/hosts file or Windows equivalent to add two aliases for localhost: alpha and
beta. This is necessary for now because parts of the code use the DNS hostname to identify nodes and thus defining two
nodes both called localhost won't work. We might fix this in future to include the port number everywhere, so making
this easier.

You should now be able to run ``ping alpha`` and ``ping beta`` and not see errors.

Now, open two terminals, and in the first run:::

    ./gradlew runDemoBuyer

It will create a directory named "alpha" and ask you to edit the configuration file inside. Open up ``alpha/config``
in your favourite text editor and give the node a legal identity of "Alpha Corp, Inc" or whatever else you feel like.
The actual text string is not important. Now run the gradle command again, and it should start up and wait for
a seller to connect.

In the second terminal, run::

    ./gradlew runDemoSeller

and repeat the process, this time calling the node ... something else.

You should see some log lines scroll past, and within a few seconds the messages "Purchase complete - we are a
happy customer!" and "Sale completed - we have a happy customer!" should be printed.

If it doesn't work, jump on the mailing list and let us know.